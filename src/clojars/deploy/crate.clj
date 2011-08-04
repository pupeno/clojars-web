(ns clojars.deploy.crate
  "A pallet crate to deploy clojars"
  (:require
   [pallet.action.directory :as directory]
   [pallet.action.exec-script :as exec-script]
   [pallet.action.package :as package]
   [pallet.action.file :as file]
   [pallet.action.remote-file :as remote-file]
   [pallet.action.service :as service]
   [pallet.action.user :as user]
   [pallet.crate.automated-admin-user :as automated-admin-user]
   [pallet.core :as core]
   [pallet.parameter :as parameter]
   [pallet.phase :as phase]
   [pallet.script.lib :as lib]
   [pallet.stevedore :as stevedore]
   [pallet.thread-expr :as thread-expr]))

(defn lein
  "Install latest stable lein"
  [session]
  (->
   session
   (remote-file/remote-file
    "/usr/local/bin/lein"
    :url "https://github.com/technomancy/leiningen/raw/stable/bin/lein"
    :mode "0755")))

(defn settings
  [session & {:keys [instance]}]
  (->
   session
   (parameter/assoc-target-settings
    :clojars instance
    {:user "clojars"
     :branch "vagrant"
     ;; :git-root "where to checkout"
     ;; :data-root "where to put data"
     ;; :repo-root "where to put repo"
     })))

(defn stop-clojars
  [session]
  (->
   session
   (exec-script/exec-checked-script
    "stop clojars"
    (stop clojars "2>" "/dev/null")
    (stop clojars-scp-balance "2>" "/dev/null"))))

(defn start-clojars
  [session]
  (->
   session
   (exec-script/exec-checked-script
    "stop clojars"
    (start clojars)
    (start clojars-scp-balance))))

(defn configure-user
  [session & {:keys [instance]}]
  (let [settings (parameter/get-for-target :clojars instance)
        user (:user settings)]
    (->
     session
     (user/user user :create-home true)
     (exec-script/exec-checked-script
      "Update sshd configuration"
      (if-not (fgrep (quoted "Match User clojars")
                     "/etc/ssh/sshd_config")
        ((println (quoted "Match User clojars
PasswordAuthentication no")) >> "/etc/ssh/sshd_config")))
     (directory/directories
      (map
       #(str (stevedore/script (~lib/user-home ~user)) "/" %)
       ["data" "repo" ".ssh"])
      :owner user :mode "0755"))))

(defn configure-webapp
  [session & {:keys [instance]}]
  (let [settings (parameter/get-for-target :clojars instance)
        user (:user settings)
        home (stevedore/script (~lib/user-home ~user))
        git-root (str home "/" (:git-root settings (str home "/prod")))
        data-root (str home "/" (:data-root settings (str home "/data")))
        repo-root (str home "/" (:data-root settings (str home "/repo")))]
    (->
     session
     (directory/directory
      (str (stevedore/script (~lib/pkg-log-root)) "/clojars")
      :owner user :mode "0755")
     (directory/directories [data-root repo-root] :owner user :mode "0755")
     (exec-script/exec-checked-script
      "checkout clojars"
      (if-not (directory? ~git-root)
        (do
          (git clone git://github.com/technomancy/clojars-web ~git-root)
          (cd "/home/clojars/prod")
          (git checkout ~(:branch settings "prod"))
          (cd -))))
     (file/symbolic-link ~data-root (str git-root "/data"))
     (file/file ~data-root (str git-root "/data/authorized_keys"))
     (file/symbolic-link
      (str git-root "/data/authorized_keys")
      (str home "/.ssh/authorized_keys"))
     (exec-script/exec-checked-script
      "initialise db"
      (sudo -u clojars
            (sqlite3 ~(str data-root "/db") < ~(str git-root "/clojars.sql"))))
     (exec-script/exec-checked-script
      "build clojars"
      (cd ~git-root)
      (sudo -u clojars
            (env (str "HOME=" ~home) lein uberjar))
      (cd -))
     (remote-file/remote-file
      "/etc/init/clojars.conf"
      :remote-file (str git-root "/config/clojars.conf"))
     (remote-file/remote-file
      "/etc/init/clojars-scp-balance.conf"
      :remote-file (str git-root "/config/clojars-scp-balance.conf"))
     (remote-file/remote-file
      "/etc/nginx/sites-available/clojars"
      :remote-file (str git-root "/config/nginx-clojars"))
     (file/symbolic-link
      "/etc/nginx/sites-available/clojars"
      "/etc/nginx/sites-enabled/clojars")
     (remote-file/remote-file
      "/etc/nginx/sites-enabled/default" :action :delete :force true))))


(defn configure-nailgun
  "TODO Install nailgun"
  [session]
  (->
   session
   (file/symbolic-link "/usr/bin/ng-nailgun" "/usr/local/bin/ng")))

(defn configure
  [session & {:keys [instance]}]
  (let [settings (parameter/get-for-target :clojars instance)
        user (:user settings)]
    (->
     session
     (package/package-manager :update)
     (package/package-manager :upgrade)
     (package/packages
      "git-core" "git-core"
      "openjdk-6-jdk" "tmux" "sqlite3" "subversion" "nginx"
      "balance" "cronolog" "emacs23" "tree" "unzip" "rlwrap"
      "tmux" "curl" "nailgun")
     lein
     (configure-user :instance instance)
     (configure-webapp :instance instance)
     configure-nailgun
     (service/service "nginx" :action :restart)
     (stop-clojars)
     (start-clojars))))

(def clojars-server
  (core/server-spec
   :phases {:settings settings
            :bootstrap automated-admin-user/automated-admin-user
            :configure configure}))

(def clojars-node-spec
  (core/node-spec
   :image {:os-family :ubuntu :os-version-matches "10.10"}))

(def clojars
  (core/group-spec
   "clojars"
   :extends [clojars-server]
   :node-spec clojars-node-spec))
