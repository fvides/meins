(ns meins.runner
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [meins.jvm.client-store-test]
            [meins.electron.renderer.client-store-entry-test]
            [meins.electron.renderer.client-store-search-test]
            [meins.jvm.ui-markdown-test]
            [meins.jvm.ui-pomodoros-test]
            [meins.jvm.parse-test]
            [meins.jvm.misc-utils-test]
            [clojure.string :as s]))

(enable-console-print!)

(defn tests []
  (run-tests 'meins.jvm.client-store-test
             'meins.electron.renderer.client-store-entry-test
             'meins.electron.renderer.client-store-search-test
             'meins.jvm.ui-markdown-test
             'meins.jvm.parse-test
             'meins.jvm.ui-pomodoros-test
             'meins.jvm.misc-utils-test))

(defn ^:export run []
  (tests)
  (let [res (with-out-str (tests))]
    (if (s/includes? res "0 failures, 0 errors") 0 1)))
