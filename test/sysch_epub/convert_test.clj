(ns sysch-epub.convert-test
  (:require [clojure.test :refer :all]
            [babashka.json :as json]
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
