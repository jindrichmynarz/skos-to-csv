(ns skos-to-csv.sparql
  (:import [com.hp.hpl.jena.query Query QueryExecutionFactory QueryFactory QuerySolution]
           [com.hp.hpl.jena.rdf.model Literal])
  (:require [clojure.java.io :as io] 
            [incanter.core :as incanter]
            [clostache.parser :refer [render-resource]]
            [skos-to-csv.util :refer [join-file-path]]))

; Multimethods

(defmulti RDF->string
  "Returns string representation of RDF node"
  class)

(defmethod RDF->string Literal [RDF] (.toString (.getString RDF)))
(defmethod RDF->string :default [RDF] (.toString RDF))

; Private functions

(defn- get-query-path
  "Returns resource path for @query-path vector"
  [query-file-name]
  (str (apply join-file-path (conj ["queries"] query-file-name)) ".mustache"))

(defn- open-query-file
  "Opens @query-path filename"
  [query-file-name]
  (let [query (get-query-path query-file-name)
        query-file (io/resource query)]
    (try
      (assert ((complement nil?) query-file))
      (catch AssertionError e
        (throw (Exception. (str "Query file " query " doesn't exist.")))))
    query-file))

(defn- process-select-binding
  "Process SPARQL select binding of @var-name from @solution into string"
  [^QuerySolution solution
   ^String var-name]
  (RDF->string (.get solution var-name)))

(defn- process-select-solution
  "Process SPARQL SELECT @solution"
  [result-vars
   ^QuerySolution solution]
  (mapv (partial process-select-binding solution) (iterator-seq (.varNames solution))))

; Public functions

(defn render-query
  "Pass in a vector with @query-path (without its .mustache
  filename extension), the @data for the query (a map), and a list of
  @partials (keywords) corresponding to like-named template filenames.

  Use: (render-query [path to query-file-name] {:key value} [:file-name-of-partial])

  Adapted from: <https://github.com/fhd/clostache/wiki/Using-Partials-as-Includes>"
  [query-file-name & {:keys [data partials]}]
  (render-resource
    (get-query-path query-file-name)
    data
    (reduce (fn [accum pt] ;; "pt" is the name (as a keyword) of the partial.
              (assoc accum pt (slurp (open-query-file (name pt)))))
            {}
            partials)))

(defn render-sparql
  "Render SPARQL @query-path using @data with prefixes added automatically."
  [query-path & {:keys [data partials]}]
  (render-query query-path
                :data data
                :partials (distinct (conj partials :prefixes))))

(defn execute-query
  "Execute a SPARQL query (@query-string) on an RDF graph (@model).
  The query results may be passed to optional @callback function."
  [query-string model]
  (let [query (QueryFactory/create query-string)
        query-type (.getQueryType query)
        qexec (QueryExecutionFactory/create query model)]
    (try
      (condp = query-type
        Query/QueryTypeAsk (.execAsk qexec)
        Query/QueryTypeSelect (doall (let [results (.execSelect qexec)
                                           result-vars (.getResultVars results)]
                                       (incanter/dataset result-vars
                                                         (mapv
                                                           (partial process-select-solution result-vars)
                                                           (iterator-seq results))))))
      (finally (.close qexec)))))
