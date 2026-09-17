(ns sysch-epub.convert
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.json :as json]
   [clojure.java.io :as io]
   [lambdaisland.uri :refer [uri]]
   [selmer.parser :as selmer]
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [sysch-epub.aisystant :as aisystant])
  (:import
   [org.jsoup Jsoup]
   [org.jsoup.nodes Attribute Document Element])
  (:gen-class))

(s/def :section/index int?)

(defn element->m
  [^Element element]
  {:id (.id element)
   :class-names (.classNames element)
   :tag-name (.normalName element)
   :attrs (->> (.attributes element)
               .iterator
               iterator-seq
               (map (juxt (memfn ^Attribute getKey) (memfn ^Attribute getValue)))
               (into {}))
   :own-text (.ownText element)
   :text (.text element)
   :whole-text (.wholeText element)
   :inner-html (.html element)
   :outer-html (.outerHtml element)})

(defn jsoup-select-doc
  [jsoup-doc css-query]
  (let [elements (-> jsoup-doc
                     (.select ^String css-query))]
    (map element->m elements)))

(def page-type-map
  {"HEADER" :header
   "TEXT" :text
   "TEST" :test})

(defn remap-page-type [page]
  (assoc page :type (page-type-map (:type page))))

(defn course-sections-clj [raw-sections]
  (map remap-page-type raw-sections))

(defn latest [course-meta]
  (last (sort-by :version course-meta)))

(defn ->course-version
  "Anti-corruption translation: raw course-versions JSON -> domain vocabulary."
  [raw-version]
  {:id (:id raw-version)
   :published-at (:version raw-version)
   :course-name (get-in raw-version [:course :name])
   :sections (:sections raw-version)})


(defn extract-latest-passing
  [passings course-slug]
  (->> passings
       (filter #(and (not (:archived %))
                     (= course-slug (:coursePath %))))
       first))

(defn ->course-enrollment
  "Anti-corruption translation: raw courses-passing JSON -> domain vocabulary."
  [raw-passing]
  {:id (:id raw-passing)
   :course-path (:coursePath raw-passing)
   :course-version-id (:courseVersionId raw-passing)
   :current-section-id (:currentSectionId raw-passing)})

(defn extract-course-sections [course-meta]
  (->> course-meta
       :sections
       course-sections-clj
       (map #(select-keys % [:id :index :title :type]))
       (map #(assoc % :file-name (format "%05d.xhtml" (:index %))))))

(defn section-path [target-section-folder section]
  (fs/path target-section-folder (format "%05d.xhtml" (:index section))))

(defn render-section [target-section-folder section]
  (spit (str (section-path target-section-folder section))
        (selmer/render-file "Section0001.xhtml" section)))

(defn section-url
  [enrollment section]
  (let [base (str "https://aisystant.system-school.ru/api/courses/text/" (:id section))
        passing-id (:id enrollment)]
    (if passing-id
      (str base "?course-passing=" passing-id)
      base)))

(defn download-section [enrollment section]
  (aisystant/download-aisyst (section-url enrollment section)))

(defn embed-image-urls
  "Change path of the images to relative URL inside epub"
  ;<img src="/text/ontologics-sobr/2025-06-19T2004/4150/7.jpeg" alt=""/><img alt="7" src="../Images/7.jpeg"/>
  [article]
  (str/replace article
               #"<img src=\"[^\"]*/(\d+\.[^\"]+)\""
               "<img src=\"../Images/$1\""))

(defn attach-article [enrollment section]
  (assoc section
         :article (embed-image-urls (download-section enrollment section))
         :raw-article (download-section enrollment section)))

(defn extract-image-urls
  [section]
  (as-> (:raw-article section) v
    (Jsoup/parse v)
    (jsoup-select-doc v "img")
    (map #(get-in % [:attrs "src"]) v)
    (map #(str "https://aisystant.system-school.ru" %) v)))

(defn aggregate-chapters [sections]
  (loop [ch-sections sections acc []]
    (if (empty? ch-sections)
      acc
      (let [chapter (first ch-sections)
            [h t] (split-with #(not= :header (:type %)) (rest ch-sections))
            text-sections (filter #(= :text (:type %)) h)]
        (recur
         t
         (conj acc (assoc chapter :sections text-sections)))))))

(defn toc-sections [sections]
  (let [[h t] (split-with #(not= :header (:type %)) sections)]
    (concat h (aggregate-chapters t))))

(defn resolve-current-enrollment [passings course-slug]
  (->course-enrollment (extract-latest-passing passings course-slug)))

(defn convert-course
  ([course-slug] (convert-course course-slug "target"))
  ([course-slug output-root]
   (let [passings (aisystant/download-aisyst-json aisystant/passings-url)
         enrollment (resolve-current-enrollment passings course-slug)
         course-meta (aisystant/download-course-metadata course-slug)
         course-sections (extract-course-sections (->course-version (latest course-meta)))
         text-only-course-sections (filter #(= :text (:type %)) course-sections)
         enriched-course-sections (map (partial attach-article enrollment) text-only-course-sections)
         image-urls (mapcat extract-image-urls enriched-course-sections)
         epub-dir (fs/path output-root course-slug)
         target-path (fs/path epub-dir "OEBPS" "content.opf")
         target-section-folder (fs/path epub-dir "OEBPS" "Text")
         images (map (partial aisystant/download-image-aisyst (fs/path epub-dir "OEBPS" "Images")) image-urls)
         course-version (->course-version (latest course-meta))
         all-sections (extract-course-sections course-version)
         toc-items (toc-sections all-sections)]
    (fs/create-dirs output-root)
    (fs/copy-tree "resources/epub-template" epub-dir {:replace-existing true})


    (spit (str target-path)
          (selmer/render-file "content-book.opf"
                              {:title course-slug
                               :sections enriched-course-sections
                               :images images
                               :uuid (java.util.UUID/randomUUID)
                               :now (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.Instant/now))}))

    (fs/delete-if-exists (fs/path target-section-folder "nav.xhtml"))

    (spit (str (fs/path target-section-folder "nav.xhtml"))
          (selmer/render-file
           "nav.xhtml"
           {:title (:course-name course-version)
            :toc-items toc-items
            :uuid (java.util.UUID/randomUUID)}))

    (run! (partial render-section target-section-folder) enriched-course-sections)
    (fs/zip (str epub-dir ".epub")
            (str epub-dir)
            {:root (str epub-dir)})
    (print
     (selmer/render "Latest passing id: {{passing-id}}\n" {:passing-id (:id enrollment)})))))

(defn -main [& args]
  (convert-course (first args)))


(comment
  (def passings
    (aisystant/download-aisyst-json
     "https://aisystant.system-school.ru/api/courses/courses-passing"))

  (def course-slug "ontologics-sobr")

  (def course-meta (aisystant/download-course-metadata course-slug))

  (reset! latest-passing
          (-> (extract-latest-passing passings course-slug)
              :id))

  @latest-passing


  (def course-sections (extract-course-sections (last course-meta)))

  (def text-only-course-sections (filter #(= :text (:type %)) course-sections))
  (def enriched-course-sections (map attach-article text-only-course-sections))

  (filter #(= (:index %) 4150) enriched-course-sections)

  (def enriched-course-sections
    (map attach-article course-sections))

  (count enriched-course-sections)

  (nil? ())

  (aisystant/download-aisyst "https://aisystant.system-school.ru/api/courses/text/69632?course-passing=41433")

  (def q *1)

  ;<img src="[^"]*\/(\d+)\.jpeg" alt="">
  
  (str/replace "<img src=\"/text/ontologics-sobr/2025-06-19T2004/4150/7.jpeg\" alt=\"\">"
               #"<img src=\"[^\"]*/(\d+\.[^\"]+)\""
               "<img src=\"../Images/$1\"")
  
  (first *1)
  (println (:article *1))
  :rcf)

;https://aisystant.system-school.ru/api/courses/text/67669?course-passing=39713
