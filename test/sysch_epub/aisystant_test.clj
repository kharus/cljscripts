(ns sysch-epub.aisystant-test
  (:require [clojure.test :refer :all]
            [sysch-epub.aisystant :refer :all]))

(deftest section-text-url-test
  (testing "builds the section text URL with course-passing when a passing id is given"
    (is (= "https://aisystant.system-school.ru/api/courses/text/79936?course-passing=50001"
           (section-text-url 50001 79936))))
  (testing "omits course-passing when there's no passing id"
    (is (= "https://aisystant.system-school.ru/api/courses/text/79936"
           (section-text-url nil 79936)))))

(deftest resolve-image-url-test
  (testing "prepends the host to a relative image path"
    (is (= "https://aisystant.system-school.ru/text/modeling-1-r2/2026-06-30T0620/1650/0.jpeg"
           (resolve-image-url "/text/modeling-1-r2/2026-06-30T0620/1650/0.jpeg")))))
