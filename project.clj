(defproject clojars-web "0.6.2"
  :main clojars.core
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.apache.maven/maven-ant-tasks "2.0.10"]
                 [org.apache.maven/maven-artifact-manager "2.2.1"]
                 [org.apache.maven/maven-model "2.2.1"]
                 [org.apache.maven/maven-project "2.2.1"]
                 [compojure "0.5.2"]
                 [ring/ring-jetty-adapter "0.3.1"]
                 [hiccup "0.3.0"]
                 [org.clojars.ato/nailgun "0.7.1"]
                 [org.xerial/sqlite-jdbc "3.6.17"]
                 [org.apache.commons/commons-email "1.2"]]
  :dev-dependencies [[lein-ring "0.4.5"]
                     [org.cloudhoist/pallet "0.6.2"]
                     [org.cloudhoist/automated-admin-user "0.5.0"]
                     [vmfest "0.2.3"]]
  :ring {:handler clojars.web/clojars-app}
  :repositories
  {"sonatype" "https://oss.sonatype.org/content/repositories/releases/"})
