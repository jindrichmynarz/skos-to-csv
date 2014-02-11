(defproject skos-to-csv "0.1.0-SNAPSHOT"
  :description "Convert SKOS in RDF to Linked CSV"
  :url "http://github.com/jindrichmynarz/skos-to-csv"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.taoensso/timbre "3.0.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [org.clojure/tools.cli "0.3.0"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.apache.jena/jena-arq "2.11.1"]
                 [org.apache.jena/jena-core "2.11.1"]
                 [org.apache.jena/jena-tdb "1.0.1"]
                 [incanter/incanter-core "1.5.4"]]
  :main skos-to-csv.core)
