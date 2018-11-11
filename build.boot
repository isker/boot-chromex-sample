(set-env!
 :source-paths    #{"src"}
 :asset-paths     #{"assets/images"}
 :dependencies '[[adzerk/boot-cljs              "2.1.5"      :scope "test"]
                 [adzerk/boot-cljs-repl         "0.4.0"      :scope "test"]
                 [cider/piggieback              "0.3.10"     :scope "test"]
                 [nrepl                         "0.4.5"      :scope "test"]
                 [weasel                        "0.7.0"      :scope "test"]
                 [powerlaces/boot-cljs-devtools "0.2.0"      :scope "test"]
                 [powerlaces/boot-figreload     "0.5.14"     :scope "test"]
                 [binaryage/devtools            "0.9.10"     :scope "test"]
                 [hiccup                        "1.0.5"      :scope "test"]
                 [org.clojure/clojure           "1.9.0"]
                 [org.clojure/clojurescript     "1.10.439"]
                 [binaryage/chromex             "0.6.5"]])

(require
 '[adzerk.boot-cljs :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [start-repl cljs-repl-env]]
 '[clojure.java.io :as io]
 '[clojure.string :as str]
 '[hiccup.core :refer [html]]
 '[powerlaces.boot-cljs-devtools :refer [cljs-devtools]]
 '[powerlaces.boot-figreload :refer [reload]])

;;; We produce the manifest as a part of the build because CLJS compilation will
;;; put entrypoints in dynamic places in dev, or just a different place in prod
(def manifest-template
  {:manifest_version 2
   :name "Getting started example"
   :description "Foos the bars"
   :version "0.0.1"
   :background {:persistent false
                ;; dynamically set: scripts
                }
   :browser_action {:default_icon "icon.png"
                    ;; popup.html is dynamically produced
                    :default_popup "popup.html"}
   :content_scripts [{:matches ["<all_urls>"]
                      :js ["script.js"]
                      :run_at "document_end"}]
   :permissions ["activeTab"
                 "storage"]})

(defn- spit-manifest
  [manifest dir]
  (spit
   (io/file dir "manifest.json")
   (json-generate manifest {:pretty true})))

(defn- popup-html [script-paths]
  "Produces the contents of popup.html; we need this to be dynamic so that we
  can load arbitrary scripts."
  (html [:html
         [:head (for [p script-paths] [:script {:src p}])]
         [:body [:p "hello world"]]]))

(defn- spit-popup-html
  [scripts dir]
  (spit
   (io/file dir "popup.html")
   (popup-html scripts)))

(deftask prod-manifest
  "Produce the production manifest.json."
  []
  (let [tmp (tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (empty-dir! tmp)
        (let [prod-manifest (assoc-in manifest-template
                                      [:background :scripts]
                                      ["background.js"])]
          (spit-manifest prod-manifest tmp)
          (spit-popup-html ["popup.js"] tmp))
        (-> fileset
            (add-asset tmp)
            commit!
            next-handler)))))

(deftask remove-uncompiled-dirs
  "In preparation for release, purge all unnecessary output files."
  []
  (with-pre-wrap fileset
    (let [files (by-re [#"^\w+\.out/.*$"] (output-files fileset))]
      (rm fileset files))))

(deftask prod
  "Run the production build.  Do this in preparation for a release."
  []
  (task-options! cljs {:optimizations :advanced :pretty-print false})
  (comp
   (prod-manifest)
   (cljs)
   (remove-uncompiled-dirs)
   (target)))

(deftask dev-manifest
  "Produce the dev manifest.json.  Must be run after cljs compilation (i.e. at
  the very end)."
  []
  (let [tmp (tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (empty-dir! tmp)
        ;; I tried using boot-cljs metadata to find these paths but they only
        ;; had the namespaces, not the FS paths.
        (let [main-paths (map :path (by-re [#".*/boot/cljs/main\d+.js$"]
                                           (ls fileset)))
              popup-main (first (filter #(str/starts-with? % "popup") main-paths))
              background-main (first (filter #(str/starts-with? % "background") main-paths))
              dev-manifest (-> manifest-template
                               (assoc-in
                                [:background :scripts]
                                ["background.out/goog/base.js" "background.out/cljs_deps.js" background-main])
                               (assoc
                                :content_security_policy
                                "script-src 'self' 'unsafe-eval'; object-src 'self'"))]
          (spit-manifest dev-manifest tmp)
          (spit-popup-html ["popup.out/goog/base.js" "popup.out/cljs_deps.js" popup-main] tmp))
        (-> fileset
            (add-asset tmp)
            commit!
            next-handler)))))

(def contentless-targets #{"background" "popup"})

(deftask dev
  "Serves the assets and compiles the scripts in a watch loop."
  [c exclude-content bool "Excludes the content script from continuous compilation"]  ; it requires closure optimizations and is thus terribly slow, plus can't be hot reloaded anyway

  (comp
   ;; Just compile it once.
   (when exclude-content (cljs :ids #{"script"}))
   (watch)
   ;; Papa Chrome says: no fun allowed in your content script; exclude it from
   ;; all of the nice dev stuff.
   (cljs-devtools :ids contentless-targets) ; TODO this doesn't seem to be working...
   (reload :ids contentless-targets)
   (cljs-repl-env :ids contentless-targets)
   (if exclude-content
     (cljs :ids contentless-targets)
     (cljs))
   (dev-manifest)
   (target)))
