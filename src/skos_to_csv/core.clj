(ns skos-to-csv.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [taoensso.timbre :as timbre]
            [clojure.java.io :refer [as-file]]
            [skos-to-csv.util :refer [join-file-path exit]]
            [skos-to-csv.convert :refer [convert]])
  (:gen-class))

(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] (join-file-path "log" "logger.log"))

(def cli-options
  [["-i" "--input RDF" "Path to input RDF file"
    :validate [#(.exists (as-file %)) "The input RDF file must exist"]]
   ["-l" "--language language-code" "Required language code (ISO 639-2) of labels"
    :default "en"]
   ["-o" "--output CSV" "Path to output CSV file"
    :default *out*]
   ["-s" "--scheme skos:ConceptScheme" "URI of skos:Concept scheme to convert"] 
   ["-h" "--help" "Display this help message"]])

(defn- error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn- usage
  [options-summary]
  (->> ["SKOS to Linked CSV converter."
        ""
        "Usage: skos2csv [options]"
        ""
        "Options:"
        options-summary]
       (clojure.string/join \newline)))

(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      errors (exit 1 (error-msg errors)) 
      (or (empty? options) (:help options)) (exit 0 (usage summary))
      :else (convert (:input options)
                      :language (:language options)
                      :output (:output options)
                      :scheme (:scheme options)))))
