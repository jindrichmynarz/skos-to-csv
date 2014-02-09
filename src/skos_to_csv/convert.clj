(ns skos-to-csv.convert
  (:import [com.hp.hpl.jena.rdf.model Model]
           [org.apache.jena.riot RDFDataMgr]
           [com.hp.hpl.jena.update GraphStoreFactory UpdateAction])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [writer]]
            [clojure.data.csv :refer [write-csv]]
            [incanter.core :as incanter]
            [skos-to-csv.sparql :as sparql])) 

; Private functions

(comment
  (defn- add-top-concepts
    "Add top concepts of the skos:ConceptScheme (@model)
     based on simple inference in SPARQL Update request"
    [^Model model]
    (let [graph-store (GraphStoreFactory/create model)]
      (UpdateAction/parseExecute (sparql/render-sparql "add_top_concepts") graph-store)
      (.toDataset graph-store)))

  (defn- has-top-concepts?
    "Check if the @model contains skos:topConceptOf"
    [^Model model]
    (sparql/execute-query (sparql/render-sparql "ask_top_concepts") model))

  (defn- get-convert-query
    "Renders SPARQL query to convert SKOS concept scheme of @max-depth into CSV
     using skos:prefLabels in given @language."
    [max-depth language]
    (let [depths (range 1 (inc max-depth))
          depth-pairs (map
                        (fn [depth] {:base depth :inc (inc depth)})
                        (range 1 max-depth))]
      (sparql/render-sparql "convert" :data {:depths depths
                                             :depth-pairs depth-pairs 
                                             :language language})))

  (defn- get-concept-scheme-depth
    "Get the maximum depth of hierarchy in the SKOS concept scheme (@model)"
    [^Model model]
    (.getInt (-> (sparql/execute-query (sparql/render-sparql "concept_scheme_depth") model)
                 :results
                 first)))

  (defn- load-model
    "Load RDF file into an RDF Model.
     Perform enrichment if no skos:topConceptOf triples are found."
    [file-path]
    (let [model (RDFDataMgr/loadModel file-path)]
      (if-not (has-top-concepts? model)
              (add-top-concepts model)
              model)))
  )

(defn- get-paths
  "Returns hierarchical paths (pairs) in @model.
   Concept labels are filtered by @language."
  [^Model model
   ^String language]
  (let [query-string (sparql/render-sparql "paths" :data {:language language})]
    (sparql/execute-query query-string model)))

(defn- get-labels
  "Extracts a map of URI-label pairs from Incanter dataset with @paths"
  [paths]
  (let [labels1 (incanter/$ [:concept1 :concept1Label] paths)
        labels2 (incanter/$ [:concept2 :concept2Label] paths)
        labels (incanter/conj-rows labels1 labels2)]
    (into {} (incanter/$map vector [:concept1 :concept1Label] labels))))

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

(comment
  (def model (load-model "cpv-2008.ttl"))
  (def paths (get-paths model "en"))
  (def labels (get-labels paths))
  (def parent-links (into {} (incanter/$map vector [:concept2 :concept1] paths)))

  (path-to-top parent-links (rand-nth (keys labels)))
 
  (def my-fn (comp
               flatten 
               (juxt identity (comp (partial map labels)
               (partial path-to-top parent-links)))))
  (require '[clojure.pprint :refer [pprint]])
  (with-open [out-file (writer *out*)]
    (write-csv out-file (map my-fn (keys labels))))

  (def parent-links {:b :a
                     :c :d
                     :d :c
                     :e :d
                     :f :e
                     :g :f})
  (path-to-top :g parent-links) 
  (path-to-top "http://linked.opendata.cz/resource/cpv-2008/concept/15981310")
  (parent-links "bork")
  (with-open [out-file (writer "out-file.csv")]
    (write-csv out-file [[]]))) 

; Public functions

(defn convert
  "Execute the conversion of SKOS at @file-path to Linked CSV"
  [file-path & {:keys [language output]}]
  (let [_ (log/info (str "Converting " file-path "..."))
        model (RDFDataMgr/loadModel file-path)
        paths (get-paths model language)
        parent-links (get-parents paths)
        labels (get-labels paths)
        concept->path (comp flatten
                            (juxt identity
                                  (comp (partial map labels)
                                  (partial path-to-top parent-links))))]
    (with-open [out-file (writer output)]
      (write-csv out-file (map concept->path (keys labels))))))

(comment
    (let [enriched-model (if-not (has-top-concepts? model)
                                 (add-top-concepts model)
                                 model)
        scheme-depth (get-concept-scheme-depth enriched-model)
        convert-query (get-convert-query scheme-depth language)]
    (println (str "Convert query: " convert-query))
    (comment (sparql/select-query convert-query model println))))
