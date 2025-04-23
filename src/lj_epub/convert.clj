(ns lj-epub.convert
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hickory.core :as hc]
   [selmer.parser :as selmer])
  (:import
   [org.jsoup Jsoup]
   [org.jsoup.nodes Attribute Document Element])
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
      (jsoup-select-doc "article.entry-content")
      first
      :outer-html
      (str/replace #"<br>--" "<li>")))

(defn extract-title-jsoup-doc
  [^Document jsoup-doc]
  (-> (jsoup-select-doc jsoup-doc "meta[property=og:title]")
      first
      (get-in [:attrs "content"])))

(defn extract-date-jsoup-doc
  [^Document jsoup-doc]
  (-> (jsoup-select-doc jsoup-doc "time")
      first
      :text ;"2025-04-04 16:40:00"
      (str/split #" ")
      first))

(defn derive-file-name
  [^Document jsoup-doc]
  (let [title (extract-title-jsoup-doc jsoup-doc)
        pub-date (extract-date-jsoup-doc jsoup-doc)]
    (if (= "lytdybr" title)
      (str title "-" pub-date)
      title)))

(defn download-image
  [epub-dir url]
  (io/copy
   (:body (http/get url {:as :stream}))
   (fs/file (fs/path epub-dir (fs/file-name url)))))

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
          (->>
           (jsoup-select-doc art-jsoup "img")
           (mapv #(get-in % [:attrs "src"]))))
    (spit (str target-path)
          (selmer/render-file "content.opf"
                              {:title title
                               :uuid (java.util.UUID/randomUUID)
                               :now (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.Instant/now))}))
    (spit (str target-path-section)
          (selmer/render-file "Section0001.xhtml"
                              {:title title
                               :article (str/replace article
                                                     #"http[^ ]*ic.pics.livejournal.com/[^ ]+/([^\"']+)"
                                                     "../Images/$1")}))
    (fs/zip (str epub-dir ".epub")
            (str epub-dir)
            {:root (str epub-dir)})))

(comment
  (def sourse-html (slurp "1759764.html"))
  (def jsoup-doc (Jsoup/parse sourse-html))
  (def title (extract-title-jsoup-doc jsoup-doc))
  (def article (extract-article-jsoup-doc jsoup-doc))

  (def art-jsoup (Jsoup/parse article))

  (def image-url "http dfa https://ic.pics.livejournal.com/ailev/696279/285800/285800_600.png")
  (str/replace image-url
               #"http[^ ]*ic.pics.livejournal.com/[^ ]+/([^\"']+)"
               "../Images/$1")

  (fs/file-name image-url)

  (->>
   (jsoup-select-doc art-jsoup "img")
   (map #(get-in % [:attrs "src"])))


  (spit "article.html" article)
  (derive-file-name jsoup-doc)

  (str (fs/path "target" "ququ" "content.opf"))

  (fs/zip "Попсовости обучения в нашем клубе не будет.epub"
          "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны"
          {:root "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны"})


  :rcf)