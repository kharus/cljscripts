(ns sysch-epub.convert
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.json :as json]
   [clojure.java.io :as io]
   [lambdaisland.uri :refer [uri]]
   [selmer.parser :as selmer])
  (:gen-class))

(def course-root
  "https://aisystant.system-school.ru/api/courses/course-versions?course-path=")

(defn read-headers []
  (-> "fetch.json"
      slurp
      json/read-str
      :headers))

(defn download-course-metadata [course-slug]
  (json/read-str
   (:body
    (http/get
     (str course-root course-slug)
     {:headers (read-headers)}))))

(defn -main [& args]
  (let [course-slug (first args)]
    (print
     (download-course-metadata course-slug))))

(comment
  (def course-meta
    (download-course-metadata "ontologics-sobr"))
  

  (def latest-meta (last course-meta))
  (keys latest-meta)
  (:version latest-meta)
  :rcf)