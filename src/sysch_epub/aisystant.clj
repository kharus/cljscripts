(ns sysch-epub.aisystant
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.json :as json]
   [clojure.java.io :as io]
   [lambdaisland.uri :refer [uri]]
   [clojure.string :as str]))

(def ^:dynamic *cache-dir* "cache")

(def image-media-map
  {"jpg"  "image/jpeg"
   "jpeg"  "image/jpeg"
   "png" "image/png"})

(def course-root
  "https://aisystant.system-school.ru/api/courses/course-versions?course-path=")

(def passings-url
  "https://aisystant.system-school.ru/api/courses/courses-passing")

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
        cache-path (str *cache-dir* "/" cache-file)]
    (if (fs/exists? cache-path)
      (slurp cache-path)
      (let [response (:body
                      (http/get
                       url
                       {:headers (read-headers)}))]
        (fs/create-dirs *cache-dir*)
        (spit cache-path response)
        response))))

(defn download-image-aisyst
  [epub-dir url]
  (let [cache-file (url-to-cache-file url)
        cache-path (str *cache-dir* "/" cache-file)]
    (when-not (fs/exists? cache-path)
      (fs/create-dirs *cache-dir*)
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
