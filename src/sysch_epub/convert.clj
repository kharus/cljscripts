(ns sysch-epub.convert
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.json :as json]
   [clojure.java.io :as io]
   [lambdaisland.uri :refer [uri]]
   [selmer.parser :as selmer]
   [clojure.string :as str])
  (:import
   [org.jsoup Jsoup]
   [org.jsoup.nodes Attribute Document Element])
  (:gen-class))

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

(def image-media-map
  {"jpg"  "image/jpeg"
   "jpeg"  "image/jpeg"
   "png" "image/png"})

(def latest-passing (atom 0))

(defn remap-page-type [page]
  (assoc page :type (page-type-map (:type page))))

(defn course-sections-clj [course-meta]
  (map remap-page-type (:sections course-meta)))

(def course-root
  "https://aisystant.system-school.ru/api/courses/course-versions?course-path=")

(defn read-headers []
  (-> "fetch.json"
      slurp
      json/read-str
      :headers))

(defn url-to-cache-file [url]
  (let [uri-work (uri url)]
    (if (:query uri-work)
      (str/join "-"
                [(fs/file-name (:path uri-work))
                 (str/replace (:query uri-work) "=" "-")])
      (fs/file-name (:path uri-work)))))

(defn download-aisyst [url]
  (let [cache-file (url-to-cache-file url)
        cache-path (str "cache/" cache-file)]
    (if (fs/exists? cache-path)
      (slurp cache-path)
      (let [response (:body
                      (http/get
                       url
                       {:headers (read-headers)}))]
        (spit cache-path response)
        response))))



(defn download-image-aisyst
  [epub-dir url]
  (let [cache-file (url-to-cache-file url)
        cache-path (str "cache/" cache-file)]
    (when-not (fs/exists? cache-path)
      (io/copy
       (:body (http/get url {:as :stream :headers (read-headers)}))
       (fs/file cache-path)))
    (fs/copy cache-path epub-dir {:replace-existing true})
    {:id (str "img-" (fs/strip-ext cache-file))
     :file-name cache-file
     :media-type (get image-media-map (fs/extension cache-file))}))

(defn download-aisyst-json [url]
  (json/read-str
   (download-aisyst url)))

(defn download-course-metadata [course-slug]
  (download-aisyst-json
   (str course-root course-slug)))

(def passings-url
  "https://aisystant.system-school.ru/api/courses/courses-passing")

(defn extract-latest-passing
  [passings course-slug]
  (->> passings
       (filter #(and (not (:archived %)) (= course-slug (:coursePath %))))
       first))

(defn extract-course-sections [course-meta]
  (->> (last course-meta)
       course-sections-clj
       (filter #(= :text (:type %)))
       (map #(select-keys % [:id :index :title]))
       (map #(assoc % :file-name (format "%05d.xhtml" (:index %))))))

(defn section-path [target-section-folder section]
  (fs/path target-section-folder (format "%05d.xhtml" (:index section))))

(defn render-section [target-section-folder section]
  (spit (str (section-path target-section-folder section))
        (selmer/render-file "Section0001.xhtml" section)))

(defn section-url
  [section]
  (selmer/render
   "https://aisystant.system-school.ru/api/courses/text/{{section-id}}?course-passing={{passing-id}}"
   {:section-id (:id section)
    :passing-id @latest-passing}))

(defn download-section [section]
  (download-aisyst (section-url section)))

(defn attach-article [section]
  (assoc section :article (download-section section)))

(defn extract-image-urls
  [section]
  (as-> (:article section) v
    (Jsoup/parse v)
    (jsoup-select-doc v "img")
    (map #(get-in % [:attrs "src"]) v)
    (map #(str "https://aisystant.system-school.ru" %) v)))

(defn -main [& args]
  (let [course-slug (first args)
        passings (download-aisyst-json passings-url)
        latest-passing-num (reset! latest-passing
                                   (-> (extract-latest-passing passings course-slug)
                                       :id))
        course-meta (download-course-metadata course-slug)
        course-sections (extract-course-sections course-meta)
        enriched-course-sections (map attach-article course-sections)
        image-urls (mapcat extract-image-urls enriched-course-sections)
        epub-dir (fs/path "target" course-slug)
        target-path (fs/path epub-dir "OEBPS" "content.opf")
        target-section-folder (fs/path epub-dir "OEBPS" "Text")
        images (map (partial download-image-aisyst (fs/path epub-dir "OEBPS" "Images")) image-urls)]
    (fs/create-dirs "target")
    (fs/copy-tree "resources/epub-template" epub-dir {:replace-existing true})


    (spit (str target-path)
          (selmer/render-file "content-book.opf"
                              {:title course-slug
                               :sections enriched-course-sections
                               :images images
                               :uuid (java.util.UUID/randomUUID)
                               :now (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.Instant/now))}))

    (run! (partial render-section (fs/path epub-dir "OEBPS" "Text")) enriched-course-sections)
    (fs/zip (str epub-dir ".epub")
            (str epub-dir)
            {:root (str epub-dir)})
    (print
     (selmer/render "Latest passing id: {{passing-id}}\n" {:passing-id @latest-passing}))))


(comment
  (def passings
    (download-aisyst-json
     "https://aisystant.system-school.ru/api/courses/courses-passing"))

  (def course-slug "ontologics-sobr")

  (def course-meta (download-course-metadata course-slug))

  (reset! latest-passing
          (-> (extract-latest-passing passings course-slug)
              :id))

  @latest-passing

  (def course-sections (extract-course-sections course-meta))

  (def enriched-course-sections
    (map attach-article course-sections))

  (run! (partial render-section (str "target/" course-slug))
        enriched-course-sections)

  (first enriched-course-sections)

  (extract-image-urls
   (nth enriched-course-sections 5))

  (mapcat extract-image-urls enriched-course-sections)

  (download-image-aisyst
   (str "target/" course-slug)
   "https://aisystant.system-school.ru/text/ontologics-sobr/2025-05-15T1900/600/0.png")

  (spit "target/content-book.opf"
        (selmer/render-file "content-book.opf" {:sections enriched-course-sections}))
  (def latest-meta (last course-meta))
  (keys latest-meta)
  (:version latest-meta)
  (attach-article {:id 67609, :index 0, :title "Введение"})

  {:id 67692, :title "Модели и знаки"}



  (download-section {:id 67609, :index 0, :title "Введение"})


  (section-url {:id 67609, :index 0, :title "Введение"})
  (format "%04d" 500)

  (download-aisyst
   (format
    "https://aisystant.system-school.ru/api/courses/text/%s?course-passing=%s"
    67692
    39713))


  :rcf)

;https://aisystant.system-school.ru/api/courses/text/67669?course-passing=39713