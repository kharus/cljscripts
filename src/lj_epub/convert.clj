(ns lj-epub.convert
  (:require
   [clojure.java.io :refer [make-parents]]
   [clojure.string :as string]
   [hickory.core :as hc]
   [selmer.parser :as selmer]
   [babashka.fs :as fs])
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

(defn extract-article [sourse-html]
  (:outer-html (first (jsoup-select-html sourse-html "article.entry-content"))))

(defn extract-article-jsoup-doc
  [^Document jsoup-doc]
  (:outer-html (first (jsoup-select-doc jsoup-doc "article.entry-content"))))

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
      (string/split #" ")
      first))

(defn derive-file-name
  [^Document jsoup-doc]
  (let [title (extract-title-jsoup-doc jsoup-doc)
        pub-date (extract-date-jsoup-doc jsoup-doc)]
    (if (= "lytdybr" title)
      (str title "-" pub-date)
      title)))

(defn -main [& args]
  (let [sourse-url (first args)
        sourse-html (slurp sourse-url)
        jsoup-doc (Jsoup/parse sourse-html)
        epub-file (derive-file-name jsoup-doc)
        title (extract-title-jsoup-doc jsoup-doc)
        epub-dir (fs/path "target" epub-file)
        target-path (fs/path epub-dir "OEBPS" "content.opf")
        target-path-section (fs/path epub-dir "OEBPS" "Text" "Section0001.xhtml")
        article (extract-article-jsoup-doc jsoup-doc)]
    (fs/create-dirs "target")
    (fs/copy-tree "resources/epub-template" epub-dir)
    (spit (str target-path)
          (selmer/render-file "content.opf"
                              {:title title
                               :uuid (java.util.UUID/randomUUID)
                               :now (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.Instant/now))}))
    (spit (str target-path-section)
          (selmer/render-file "Section0001.xhtml"
                              {:title title
                               :article article}))
    (fs/zip (str epub-dir ".epub")
            (str epub-dir)
            {:root (str epub-dir)})
    ))

(comment
  (def sourse-html (slurp "https://ailev.livejournal.com/1759764.html"))
  (def jsoup-doc (Jsoup/parse sourse-html))
  (def title (extract-title-jsoup-doc jsoup-doc))
  (def pub-date (extract-date-jsoup-doc jsoup-doc))

  (derive-file-name jsoup-doc)

  (str (fs/path "target" "ququ" "content.opf"))

  (fs/zip "Попсовости обучения в нашем клубе не будет.epub"
          "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны"
          {:root "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны"})


  :rcf)