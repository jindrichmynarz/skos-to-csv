(ns skos-to-csv.convert
  (:import [com.hp.hpl.jena.rdf.model Model]
           [org.apache.jena.riot RDFDataMgr])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [writer]]
            [clojure.string :refer [join]]
            [clojure.data.csv :refer [write-csv]]
            [incanter.core :as incanter]
            [skos-to-csv.util :refer [exit]]
            [skos-to-csv.sparql :as sparql])) 

; Private functions

(defn- has-many-concept-schemes?
  "If @model contains more than 1 instance of skos:ConceptScheme
   returns prompt to user to specify skos:ConceptScheme."
  [^Model model]
  (let [query-string (sparql/render-sparql "concept_schemes")
        concept-schemes (incanter/$ :scheme (sparql/execute-query query-string model))]
    (if (> (count concept-schemes) 1)
        (str "While no skos:ConceptScheme was specified, "
             "the provided data contains more than 1 skos:ConceptScheme."
             \newline
             "Please provide one of the following URIs of skos:ConceptSchemes "
             "using the -s parameter:"
             \newline
             (join \newline (map (partial str "  ") concept-schemes))))))

(defn- get-paths
  "Returns hierarchical paths (pairs) in @model.
   Concept labels are filtered by @language."
  [^Model model & {:keys [language scheme]}]
  (let [query-string (sparql/render-sparql "paths" :data {:language language
                                                          :scheme scheme})]
    (sparql/execute-query query-string model)))

(defn- get-labels
  "Extracts a map of URI-label pairs from Incanter dataset with @paths"
  [paths]
  (let [keyword-str (comp keyword str)
        columns (fn [index] (vector (keyword-str "concept" index)
                                    (keyword-str "concept" index "Label")))
        labels1 (incanter/$ (columns 1) paths)
        labels2 (incanter/$ (columns 2) paths)
        labels (incanter/conj-rows labels1 labels2)]
    (into {} (incanter/$map vector (columns 1) labels))))

(defn- get-parents
  "Returns a map relating concepts to their parents
   based on hierarchical @paths."
  [paths]
  (into {} (incanter/$map vector [:concept2 :concept1] paths)))

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
        header (conj (map #(str "level" % "ConceptLabel") (range 1 max-columns)) "@id" "#")
        lang-header (conj (repeat (dec max-columns) language) "" "lang")]
    (list lang-header header)))

(comment
  (def model (RDFDataMgr/loadModel "cpv-2008.ttl"))
  (def concept-schemes (has-many-concept-schemes? model))
  (def paths (get-paths model :language "en"))
  (def parent-links (get-parents paths))
  (def labels (get-labels paths))
  (def concept->path (comp flatten
                           (juxt identity
                                 (comp (partial map labels)
                                 (partial path-to-top parent-links)))))
  (def data (map concept->path (keys labels)))
  (take 1 data)

  (def prolog (create-prolog data :language "en"))
  (def max-columns (dec (reduce max (map count data))))
  (def header (conj (map #(str "level" % "ConceptLabel") (range 1 max-columns)) "@id" "#"))
  (def lang-header (conj (repeat max-columns "en") "" "lang"))
  (def full (into (map #(conj % "") data) (list lang-header header)))
  (take 4 full)
  )

; Public functions

(defn convert
  "Execute the conversion of SKOS at @file-path to Linked CSV"
  [file-path & {:keys [language output scheme]}]
  (let [_ (log/info (str "Converting " file-path "..."))
        model (RDFDataMgr/loadModel file-path)
        _ (if-not scheme (if-let [concept-scheme-prompt (has-many-concept-schemes? model)]
                           (exit 0 concept-scheme-prompt)))
        paths (get-paths model :language language
                               :scheme scheme)
        parent-links (get-parents paths)
        labels (get-labels paths)
        concept->path (comp #(conj % "")
                            (comp flatten
                                  (juxt identity
                                        (comp (partial map labels)
                                        (partial path-to-top parent-links)))))
        data (sort-by first (map concept->path (keys labels)))
        prolog (create-prolog data language)]
    (with-open [out-file (writer output)]
      (write-csv out-file (into data prolog)))))
