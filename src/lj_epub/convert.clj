(ns lj-epub.convert
  (:require [hickory.core :as hc]
            [selmer.parser :as selmer]
            [clojure.string :as string])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Attribute Attributes Comment DataNode Document
            DocumentType Element TextNode XmlDeclaration]
           [org.jsoup.parser Tag Parser])
  (:gen-class))

(defn hickory-html [raw-html]
  (->> (hc/as-hickory raw-html)
       (filter #(= (:tag %) :html))
       first))

(selmer/set-resource-path! "/home/ruslan/src/bbscripts/templates")

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

(defn extract-data-jsoup-doc
  [^Document jsoup-doc]
    (-> (jsoup-select-doc jsoup-doc "time")
         first
         :text ;"2025-04-04 16:40:00"
         (string/split #" ")
         first))

(defn -main [& args]
  (let [sourse-url (first args)
        sourse-html (slurp sourse-url)
        jsoup-doc (Jsoup/parse sourse-html)]
    (prn (subs (extract-article-jsoup-doc jsoup-doc) 0 80))
    (prn (extract-title-jsoup-doc jsoup-doc))))

(comment
  (def sourse-html (slurp "https://ailev.livejournal.com/1759764.html"))
  (def jsoup-doc (Jsoup/parse sourse-html))
  (def title (extract-title-jsoup-doc jsoup-doc))

  (spit "content.opf"
        (selmer/render-file "content.opf"
                            {:title title
                             :uuid (java.util.UUID/randomUUID)
                             :now (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.Instant/now))}))

  (fs/zip "Попсовости обучения в нашем клубе не будет.epub"
          "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны"
          {:root "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны"})


  (spit "Section0001.xhtml"
        (selmer/render-file "Section0001.xhtml"
                            {:title "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны"
                             :article (:outer-html html-article)}))

  (first (jsoup/select (slurp "quq.html") "meta[property=og:title]"))

  (fs/copy-tree "epub-template" "Попсовости обучения в нашем клубе не будет, давать \"короткие концерты по справочнику\" не согласны")

  :rcf)