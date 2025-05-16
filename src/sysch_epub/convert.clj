(ns sysch-epub.convert
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.json :as json]
   [clojure.java.io :as io]
   [lambdaisland.uri :refer [uri]]
   [selmer.parser :as selmer])
  (:gen-class))

(def page-type-map
  {"HEADER" :header
   "TEXT" :text
   "TEST" :test})

(defn remap-page-type [page]
  (assoc page :type (page-type-map (:type page))))

(defn course-sections [course-meta]
  (map remap-page-type (:sections course-meta)))

(def course-root
  "https://aisystant.system-school.ru/api/courses/course-versions?course-path=")

(defn read-headers []
  (-> "fetch.json"
      slurp
      json/read-str
      :headers))

(defn download-aisyst [url]
  (:body
   (http/get
    url
    {:headers (read-headers)})))

(defn download-aisyst-json [url]
  (json/read-str
   (download-aisyst url)))

(defn download-course-metadata [course-slug]
  (download-aisyst-json
   (str course-root course-slug)))

(defn -main [& args]
  (let [course-slug (first args)]
    (print
     (download-course-metadata course-slug))))

(comment
  (def passings
    (download-aisyst-json "https://aisystant.system-school.ru/api/courses/courses-passing"))
  ;https://aisystant.system-school.ru/api/courses/courses-passing
  (def course-meta
    (json/read-str
     (slurp "resources/system-school/ontologics-sobr-2025-05-15.json")))

  (def course-sections-clean
    (course-sections course-meta))

  (filter #(= :text (:type %)) course-sections-clean)

  (filter #(and (not (:archived %)) (= "ontologics-sobr" (:coursePath %))) passings)
  (def latest-meta (last course-meta))
  (keys latest-meta)
  (:version latest-meta)
  :rcf)

;https://aisystant.system-school.ru/api/courses/text/67669?course-passing=39713