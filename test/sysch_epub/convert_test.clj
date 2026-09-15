(ns sysch-epub.convert-test
  (:require [clojure.test :refer :all]
            [babashka.json :as json]
            [babashka.fs :as fs]
            [sysch-epub.convert :refer :all]))

(defn load-sample [filename]
  (json/read-str (slurp (str "test/resources/sysch_epub/" filename))))

(deftest extract-latest-passing-test
  (let [passings (load-sample "courses-passing-sample.json")]
    (testing "returns the non-archived row when an archived one for the same course also exists"
      (is (= 48607 (:id (extract-latest-passing passings "firefighting-1")))))
    (testing "returns the active passing even though status is NONE"
      (is (= 50001 (:id (extract-latest-passing passings "modeling-1-r2")))))
    (testing "returns nil when the course isn't present"
      (is (nil? (extract-latest-passing passings "no-such-course"))))))

(deftest latest-test
  (let [course-meta (load-sample "course-versions-modeling-1-r2-sample.json")]
    (testing "picks the chronologically latest version by :version, not array order"
      (is (= 772 (:id (latest course-meta)))))))

(deftest ->course-enrollment-test
  (let [passings (load-sample "courses-passing-sample.json")
        raw (extract-latest-passing passings "modeling-1-r2")]
    (testing "translates raw courses-passing JSON into domain vocabulary"
      (is (= {:id 50001
              :course-path "modeling-1-r2"
              :course-version-id 772
              :current-section-id 79933}
             (->course-enrollment raw))))))

(deftest convert-course-golden-test
  ;; golden-cache is trimmed to 1 course version with 4 sections
  ;; (HEADER 79932, TEXT 79933 [no images], TEST 79934 [excluded from output],
  ;; TEXT 79965 [3 images]) - just enough to exercise every branch once.
  (testing "produces the expected epub entirely from the golden cache, no network"
    (let [output-root "target-golden-test"]
      (try
        (binding [*cache-dir* "test/resources/sysch_epub/golden-cache"]
          (convert-course "modeling-1-r2" output-root))
        (is (fs/exists? (fs/path output-root "modeling-1-r2.epub")))
        (is (= 2 (->> (fs/list-dir (fs/path output-root "modeling-1-r2" "OEBPS" "Text"))
                       (remove #(= "nav.xhtml" (fs/file-name %)))
                       count))
            "only the 2 TEXT sections get rendered, HEADER/TEST excluded")
        (is (= 3 (->> (fs/list-dir (fs/path output-root "modeling-1-r2" "OEBPS" "Images"))
                       (remove #(= ".gitkeep" (fs/file-name %)))
                       count))
            "the 3 images referenced by the image-bearing section are downloaded")
        (finally
          (fs/delete-tree (fs/path output-root)))))))
