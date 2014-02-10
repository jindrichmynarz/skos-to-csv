(ns skos-to-csv.util
  (:require [clojure.java.io :refer [file]]))

(defn exit
  "Exit with @status and message @msg"
  [status msg]
  (println msg)
  (System/exit status))

(defn join-file-path
  "Join a collection into file path joined by OS-specific separator"
  [& args]
  (clojure.string/join java.io.File/separator args))

(defn list-directory
  "List files in @directory"
  [directory]
  (let [directory-file (file directory)]
    (filter (partial not= directory-file)
            (file-seq directory-file))))
