(ns sysch-epub.convert
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [lambdaisland.uri :refer [uri]]
            [babashka.fs :as fs]
            [selmer.parser :as selmer]
            [meander.epsilon :as m])
  (:gen-class))

(def page-type-map
  {"HEADER" :header
   "TEXT" :text
   "TEST" :test})

(def headers-map-static
  {:headers
   {"accept" "application/json"
    "authority" "aisystant.system-school.ru"
    "referer" "https://aisystant.system-school.ru/lk/"
    "user-agent" "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36"}})

(def headers-map
  (conj (:headers headers-map-static)
        ["cookie" (slurp "cookie.txt")]))
;;;https://aisystant.system-school.ru/api/courses/course-versions?course-path=selfdev
;;;https://aisystant.system-school.ru/api/courses/course-versions?course-path=intro-online-2021
(comment
  (def course-versions-web
    (http/get "https://aisystant.system-school.ru/api/courses/course-versions?course-path=selfdev"
              {:headers headers-map}))
  :rcf)


(defn download-section [sec-num]
  (http/get (str "https://aisystant.system-school.ru/api/courses/text/" sec-num "?course-passing=9751") {:headers headers-map}))

(defn subpath [path-str]
  (let [path (fs/path path-str)
        name-count (.getNameCount path)]
    (.subpath path 1 (- name-count 2))))

(defn download-image [url]
  (io/copy
   (:body (http/get url {:as :stream :headers headers-map}))
   (fs/file (fs/path "." (subpath (:path (uri url)))))))

(def course-versions (json/read-str (slurp "intro-online-2021.json")))

(defn remap-page-type [page]
  (assoc page :type (page-type-map (:type page))))

(def course-sections
  (map remap-page-type (:sections (last course-versions))))

(def xf (comp
         (map #(select-keys % [:id :index :title :type]))
         (filter #(>= (:index %) 940))
         (filter #(< (:index %) 1050))))

(comment
  (sequence xf course-sections)
  :rcf)

(def chapter4-sections
  [{:id 969, :index 940, :title "Глава 9. Системное предпринимательство", :type :header}
   {:id 970, :index 950, :title "Что такое предпринимательство", :type :text}
   {:id 971, :index 960, :title "Роль \"предприниматель\"", :type :text}
   {:id 972,
    :index 970,
    :title "Пререквизит или что дополнительно важно знать, чтобы быть предпринимателем",
    :type :text}
   {:id 973, :index 980, :title "Возможности", :type :text}
   {:id 974, :index 990, :title "Предпринимательские практики ", :type :text}
   {:id 975, :index 1000, :title "Стратегия и стратегирование", :type :text}
   {:id 976, :index 1010, :title "Предпринимательство в личных проектах", :type :text}
   {:id 977, :index 1020, :title "Выводы главы №9", :type :text}
   {:id 978, :index 1030, :title "Вопросы для повторения и дальнейшего изучения", :type :text}
   {:id 979, :index 1040, :title "Домашнее задание №9", :type :test}])

(comment
  (doseq [{section-id :id} chapter4-sections]
    (spit (str "wip/" section-id ".html") (:body (download-section section-id))))
  :rcf)


(defn flatten-image-path [image-path]
  (str
   (subpath image-path)
   "/"
   (fs/file-name (fs/parent image-path)) "-" (fs/file-name image-path)))

(comment
  (doseq [{section-id :id section-title :title} chapter4-sections]
    (with-open [r (io/reader (str "wip/" section-id ".html"))]
      (doseq [line (line-seq r)
              :let [img-matches (re-find #"img src=\"(.*?)\"" line)
                    img (second img-matches)]
              :when img-matches]
        (fs/create-dirs (fs/parent (subpath img)))
        (io/copy
         (:body (http/get (str "https://aisystant.system-school.ru" img) {:as :stream :headers headers-map}))
         (fs/file (fs/path "." (flatten-image-path img))))
        (prn img))))
  :rcf)

(selmer/set-resource-path! "/home/ruslan/code/bbscripts/templates")

(defn flatten-image-epub-path [image-path]
  (str
   "../Images/"
   (fs/file-name (fs/parent image-path)) "-" (fs/file-name image-path)))


(defn flatten-image-tag [hickory-image]
  (m/match hickory-image
    {:type :element,
     :attrs {:src ?image-url, :alt ""},
     :tag :img,
     :content nil}

    {:type :element,
     :attrs {:src (flatten-image-epub-path ?image-url)},
     :tag :img}

    _ hickory-image))

(defn rewrite-image-tag [section-id]
  (with-open [r (io/reader (str "wip/" section-id ".html"))]
    (doseq [line (line-seq r)]
      (spit (str "wip/" section-id "-s1.html")
            (str
             (bootleg/as-html (flatten-image-tag (bootleg/convert-to line :hickory)))
             "\n")
            :append true))))

(defn flatten-image-div [hickory-tag]
  (m/match hickory-tag
    {:type :element,
     :tag :div,
     :content ?div-content}

    (assoc hickory-tag :content (mapv flatten-image-tag ?div-content))

    {:type :element,
     :attrs _,
     :tag :b,
     :content _}

    (assoc hickory-tag :tag :h3)

    _ hickory-tag))


(defn parse-to-hickory-seq [raw-html]
  (bootleg/convert-to raw-html :hickory-seq))

(defn rewrite-popup-tag [popup-tag]
  (m/match popup-tag
    {:type :element,
     :tag :span,
     :attrs {:class "sspopup"},
     :content [{:type :element,
                :tag :sup
                :content [?note-id]},
               {:type :element,
                :tag :span
                :content [?note-content]}]}

    [{:type :element
      :tag :a
      :attrs {:href (str "#back_note" ?note-id) :epub:type "noteref"}
      :content [{:type :element, :tag :sup, :content [?note-id]}]},
     {:type :element
      :tag :aside
      :attrs {:id (str "back_note" ?note-id) :epub:type "footnote"}
      :content [(str/replace-first ?note-content #"^\[x\] " "")]}]

    {:type :element,
     :tag :span,
     :attrs {:class "sspopup"},
     :content [{:type :element,
                :tag :sup
                :content [?note-id]},
               {:type :element,
                :tag :span
                :content ["[x] " ?note-content]}]}

    [{:type :element
      :tag :a
      :attrs {:href (str "#back_note" ?note-id) :epub:type "noteref"}
      :content [{:type :element, :tag :sup, :content [?note-id]}]},
     {:type :element
      :tag :aside
      :attrs {:id (str "back_note" ?note-id) :epub:type "footnote"}
      :content [?note-content]}]

    _ [popup-tag]))


(defn rewrite-popup-content [hickory-tag]
  (m/match hickory-tag
    {:type :element,
     :attrs {:class "b"},
     :tag :p
     :content ?content}

    (let [tags (into [] (mapcat rewrite-popup-tag ?content))
          gb (group-by #(= :aside (:tag %)) tags)
          content (gb false)]
      (concat [(assoc hickory-tag :content content)]
              (gb true)))

    _ [hickory-tag]))

(defn rewrite-tags [section-id]
  (->> (slurp (str "wip/" section-id ".html"))
       (parse-to-hickory-seq)
       (map flatten-image-div)
       (mapcat rewrite-popup-content)
       (bootleg/as-html)
       (spit (str "wip/" section-id "-s1.html"))))

(comment
  (doseq [{section-id :id section-title :title} chapter4-sections]
    (rewrite-tags section-id))
  :rcf)

(comment
  (doseq [{section-id :id section-title :title section-type :type} chapter4-sections]
    (case section-type
      :header (spit (str section-id ".xhtml")
                    (selmer/render-file "title.xhtml"
                                        {:title section-title}))
      :text (spit (str section-id ".xhtml")
                  (selmer/render-file "chapter.xhtml"
                                      {:title section-title
                                       :body (slurp (str "wip/" section-id "-s1.html"))}))
      nil))
  :rcf)

(defn -main [& args]
  (let [sourse-url (first args)]
    (http/get "https://aisystant.system-school.ru/api/courses/course-versions?course-path=selfdev"
              {:headers headers-map})))