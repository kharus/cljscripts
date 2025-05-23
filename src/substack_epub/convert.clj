(ns substack-epub.convert
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hickory.core :as hc]
   [selmer.parser :as selmer]
   [babashka.json :as json])
  (:import
   [org.jsoup Jsoup]
   [org.jsoup.nodes Attribute Document Element Entities])
  (:gen-class))

(defn hickory-html [raw-html]
  (->> (hc/as-hickory raw-html)
       (filter #(= (:tag %) :html))
       first))

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

(defn jsoup-select-html
  [html css-query]
  (-> (Jsoup/parse html)
      (jsoup-select-doc css-query)))

(defn extract-article-jsoup-doc
  [^Document jsoup-doc]
  (-> jsoup-doc
      (jsoup-select-doc "div.body")
      first
      :outer-html))

(defn extract-title-jsoup-doc
  [^Document jsoup-doc]
  (-> (jsoup-select-doc jsoup-doc "h1.post-title")
      first
      :text))

(defn extract-date-jsoup-doc
  [^Document jsoup-doc]
  (-> (jsoup-select-doc jsoup-doc "time")
      first
      :text ;"2025-04-04 16:40:00"
      (str/split #" ")
      first))

(defn derive-file-name
  [^Document jsoup-doc]
  (let [title (extract-title-jsoup-doc jsoup-doc)]
    (print title)
    title))

(defn image-url-to-file
  [image-url]
  (-> image-url
      fs/file-name
      java.net.URLDecoder/decode
      fs/file-name))

(defn download-image
  [epub-dir url]
  (io/copy
   (:body (http/get url {:as :stream}))
   (fs/file (fs/path epub-dir (image-url-to-file url)))))

(defn embed-image-urls
  "Change path of the images to relative URL inside epub"
  [article]
  (-> article
      (str/replace
       #"img src=\"https://substackcdn.com/image.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*?)\""
       "img src=\"../Images/$1\"")
      (str/replace
       #"(?s)<div id=\"datawrapper-iframe\".*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*?png).*?</div>"
       "<img src=\"../Images/$1\">")))

(defn extract-image-urls
  [art-jsoup]
  (->>
   (jsoup-select-doc art-jsoup "img")
   (mapv #(get-in % [:attrs "src"]))))

(defn extract-data-attrs
  [attrs]
  (json/read-str (get attrs "data-attrs")))

(defn extract-iframe-image-urls
  [art-jsoup]
  (->> (jsoup-select-doc art-jsoup "div#datawrapper-iframe")
       (map :attrs)
       (map extract-data-attrs)
       (map :thumbnail_url)))



(defn -main [& args]
  (let [sourse-url (first args)
        sourse-html (slurp sourse-url)
        jsoup-doc (Jsoup/parse sourse-html)
        epub-file (derive-file-name jsoup-doc)
        title (extract-title-jsoup-doc jsoup-doc)
        epub-dir (fs/path "target" epub-file)
        target-path (fs/path epub-dir "OEBPS" "content.opf")
        target-path-section (fs/path epub-dir "OEBPS" "Text" "Section0001.xhtml")
        article (extract-article-jsoup-doc jsoup-doc)
        art-jsoup (Jsoup/parse article)]
    (fs/create-dirs "target")
    (fs/copy-tree "resources/epub-template" epub-dir)
    (run! (partial download-image (fs/path epub-dir "OEBPS" "Images"))
          (concat 
           (extract-image-urls art-jsoup)
           (extract-iframe-image-urls art-jsoup)))
    (spit (str target-path)
          (selmer/render-file "content.opf"
                              {:title title
                               :uuid (java.util.UUID/randomUUID)
                               :now (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.Instant/now))}))
    (spit (str target-path-section)
          (selmer/render-file "Section0001.xhtml"
                              {:title title
                               :article (embed-image-urls article)}))
    (fs/zip (str epub-dir ".epub")
            (str epub-dir)
            {:root (str epub-dir)})))

(comment

  (def sourse-url "resources/substack/how-japan-invented-modern-shipbuilding.html")
  (def sourse-html (slurp sourse-url))
  (def jsoup-doc (Jsoup/parse sourse-html))
  (def epub-file (derive-file-name jsoup-doc))
  (extract-title-jsoup-doc jsoup-doc)
  (def article (extract-article-jsoup-doc jsoup-doc))
  (def art-jsoup (Jsoup/parse article))

  (def image-urls (extract-image-urls art-jsoup))

  (first image-urls)
  (Entities/unescape

   (fs/file-name (first image-urls)))
  (->>
   (jsoup-select-doc art-jsoup "div#datawrapper-iframe")
   (map :attrs)
   (map extract-data-attrs)
   (map :thumbnail_url))

  (def long-string (slurp "qq.text"))

  (str/replace "<img src=\"https://substackcdn.com/image/fetch/w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fa9913c07-4ddd-4be7-a731-b96e5a25efc7_700x466.png\""
               #"img src=\"https://substackcdn.com/image.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*?)\""
               "img src=\"../Images/$1")


  (str/replace long-string
               #"(?s)<div id=\"datawrapper-iframe\".*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*?png).*?</div>"
               "<img src=\"../Images/$1\">")

  (-> jsoup-doc
      (jsoup-select-doc "div.body")
      first
      :outer-html)
  :rcf)