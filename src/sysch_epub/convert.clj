(ns sysch-epub.convert
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.json :as json]
   [clojure.java.io :as io]
   [lambdaisland.uri :refer [uri]]
   [selmer.parser :as selmer]
   [clojure.string :as str])
  (:gen-class))

(def page-type-map
  {"HEADER" :header
   "TEXT" :text
   "TEST" :test})

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
      (:body
       (http/get
        url
        {:headers (read-headers)})))))

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
  (filter #(and (not (:archived %)) (= course-slug (:coursePath %))) passings))

(defn jumbo [course-meta]
  (->> (last course-meta)
       course-sections-clj
       (filter #(= :text (:type %)))
       (map #(select-keys % [:id :title]))))

(defn -main [& args]
  (let [course-slug (first args)
        passings (download-aisyst-json passings-url)
        course-meta (download-course-metadata course-slug)]
    (print
     (extract-latest-passing passings course-slug)
     (jumbo course-meta))))

(comment
  (def passings
    (download-aisyst-json "https://aisystant.system-school.ru/api/courses/courses-passing"))
  ;https://aisystant.system-school.ru/api/courses/courses-passing
  (def course-meta
    (json/read-str
     (slurp "resources/system-school/ontologics-sobr-2025-05-15.json")))

  (def course-meta (download-course-metadata "ontologics-sobr"))

  (filter #(= :text (:type %)) course-meta)


  (jumbo course-meta)
  (def latest-meta (last course-meta))
  (keys latest-meta)
  (:version latest-meta)

  :rcf)

;https://aisystant.system-school.ru/api/courses/text/67669?course-passing=39713