(ns skos-to-csv.convert
  (:import [org.apache.jena.riot RDFDataMgr]
           [com.hp.hpl.jena.sparql.core DatasetImpl]
           [com.hp.hpl.jena.tdb TDBFactory])
  (:require [taoensso.timbre :as timbre]
            [clojure.java.io :refer [delete-file writer]]
            [clojure.string :refer [join]]
            [clojure.data.csv :refer [write-csv]]
            [incanter.core :as incanter]
            [skos-to-csv.util :refer [directory-exists? exit list-directory]]
            [skos-to-csv.sparql :as sparql])) 

(def tdb-directory "db")

; Private functions

(defn- init-dataset
  "Creates TDB dataset in @tdb-directory and loads an RDF file on @file-path into it.
   Returns the dataset."
  [^String tdb-directory
   ^String file-path]
  {:pre [(not (directory-exists? tdb-directory))]}
  (let [_ (.mkdir (java.io.File. tdb-directory))
        dataset (TDBFactory/createDataset tdb-directory)]
    (do (RDFDataMgr/read dataset file-path)
        (timbre/debug "Dataset loaded.")
        dataset)))

(defn- has-many-concept-schemes?
  "If @model contains more than 1 instance of skos:ConceptScheme
   returns prompt to user to specify skos:ConceptScheme."
  [^DatasetImpl dataset]
  (let [query-string (sparql/render-sparql "concept_schemes")
        concept-schemes (incanter/$ :scheme (sparql/execute-query query-string dataset))]
    (if-not (<= (count concept-schemes) 1)
            (exit 0 (str "While no skos:ConceptScheme was specified, "
                         "the provided data contains more than 1 skos:ConceptScheme."
                         \newline
                         "Please provide one of the following URIs of skos:ConceptSchemes "
                         "using the -s parameter:"
                         \newline
                         (join \newline (map (partial str "  ") concept-schemes)))))))

(defn- get-parents
  "Returns a map relating concepts to their parents
   based on hierarchical paths in @dataset."
  [^DatasetImpl dataset & {:keys [scheme]}]
  (let [query-string (sparql/render-sparql "paths" :data {:scheme scheme})]
    (into {} (incanter/$map vector
                            [:broader :narrower]
                            (sparql/execute-query query-string dataset)))))

(defn- get-labels
  "Extracts a map of concept (skos:Concept) and label (skos:prefLabel) pairs from @dataset.
   Concept may be drawn from specified @scheme. Labels are retrieved in specified @language tag."
  [^DatasetImpl dataset & {:keys [language scheme]}]
  (let [query-string (sparql/render-sparql "labels" :data {:language language
                                                           :scheme scheme})]
    (into {} (incanter/$map vector
                            [:label :concept]
                            (sparql/execute-query query-string dataset)))))

(defn- path-to-top
  "Follows @parent-links map from @node to top concept.
   Returns path from top to @node as list."
  [parent-links node]
  (loop [path (list node)
         visited-nodes (list)]
    (let [child (first path)
          parent-link (parent-links child)
          _ (assert (not= parent-link child) "Parent cannot be the same as child.")
          _ (assert (not (some #{parent-link} visited-nodes)) "No cycles permitted!")]
      (if-not parent-link
              path
              (recur (conj path parent-link) (conj visited-nodes parent-link))))))

(defn- create-prolog
  "Create Linked CSV prolog based on @data and @language"
  [data language]
  (let [max-columns (dec (reduce max (map count data)))
        header (conj (map #(str "level" % "ConceptLabel") (range 1 max-columns)) "$id" "#")
        lang-header (conj (repeat (dec max-columns) language) "" "lang")]
    (list lang-header header)))

(defn- delete-tdb-files
  "Delete TDB directory"
  [^String tdb-directory]
  (do (dorun (map delete-file
                  (list-directory tdb-directory)))
      (delete-file (java.io.File. tdb-directory))))

(defn- tdb-directory-empty?
  "Check if TDB directory is empty"
  [^String tdb-directory]
  (empty? (list-directory tdb-directory)))

; Public functions

(defn convert
  "Execute the conversion of SKOS at @file-path to Linked CSV"
  [^String file-path & {:keys [language output scheme]}]
  (try
    (let [_ (timbre/info (str "Converting " file-path "..."))
          dataset (init-dataset tdb-directory file-path)
          _ (timbre/debug "Dataset loaded.")
          _ (if-not scheme (has-many-concept-schemes? dataset))
          _ (timbre/debug "Getting parents...")
          parent-links (get-parents dataset :scheme scheme)
          _ (timbre/debug "Parents loaded.")
          _ (timbre/debug "Getting labels...")
          labels (get-labels dataset :language language :scheme scheme)
          _ (timbre/debug "Labels loaded.")
          concept->path (comp #(conj % "")
                              (comp flatten
                                    (juxt identity
                                          (comp (partial map labels)
                                          (partial path-to-top parent-links)))))
          data (sort-by second (map concept->path (keys labels)))
          _ (timbre/debug "Data extracted.")
          prolog (create-prolog data language)]
      (with-open [out-file (writer output)]
        (write-csv out-file (into data prolog))))
    (finally (delete-tdb-files tdb-directory))))
