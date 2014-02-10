(ns skos-to-csv.util)

(defn exit
  "Exit with @status and message @msg"
  [status msg]
  (println msg)
  (System/exit status))

