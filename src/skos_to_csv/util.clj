(ns skos-to-csv.util
  (:require [clojure.java.io :refer [as-file file]]))

(defn directory-exists?
  "Tests if directory exists"
  [^String directory]
  (.exists (as-file directory)))

(defn exit
  "Exit with @status and message @msg"
  [^Integer status
   ^String msg]
  (println msg)
  (System/exit status))

(defn join-file-path
  "Join a collection into file path joined by OS-specific separator"
  [& args]
  (clojure.string/join java.io.File/separator args))

(defn list-directory
  "List files in @directory"
  [^String directory]
  (let [directory-file (file directory)]
    (filter (partial not= directory-file)
            (file-seq directory-file))))
