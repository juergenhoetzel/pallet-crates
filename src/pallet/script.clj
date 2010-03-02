(ns #^{:doc "Base infrastructure for script generation"}
  pallet.script
  (:use [pallet.target :only [*target-template*]]
        [clojure.contrib.def :only [defvar-]]
        clojure.contrib.logging))

;; map from script name to implementations
;; where implementations is a map from keywords to function
(def  *scripts* {})

(def *script-line* nil)
(def *script-file* nil)

(defmacro with-line-number
  "Record the source line number"
  [& body]
  `(do ;(defvar- ln# nil)
       ;(binding [*script-line* (:line (meta (var ln#)))
       ; *script-file* (:file (meta (var ln#)))]
         (ns-unmap *ns* 'ln#)
         ~@body));)

(defn print-args [args]
  (str "(" (apply str (interpose " " args)) ")"))

(defn- matches?
  "Return the keys that match the template, or nil if any of the keys are not in
  the template."
  [keys]
  (every? #(some (partial = %) *target-template*) keys))

(defn- more-explicit? [current candidate]
  (or (= current :default)
      (> (count candidate) (count current))))

(defn- better-match? [current candidate]
  (if (and (matches? (first candidate))
           (more-explicit? (first current) (first candidate)))
    candidate
    current))

(defn best-match [script]
  (debug (str "Looking up script " script))
  (when-let [impls (*scripts* script)]
    (debug "Found implementations")
    (second (reduce better-match?
                    [:default (impls :default)]
                    (dissoc impls :default)))
;;     (impls (first (keys impls)))
    )) ;; TODO fix this

(defn dispatch-target
  "Invoke target, raising if there is no implementation."
  [script & args]
  (trace (str "dispatch-target " script " " (print-args ~@args)))
  (let [f (best-match script)]
    (if f
      (apply f args)
      (throw (str "No implementation for " (name script))))))

(defn invoke-target
  "Invoke target when possible, otherwise return nil"
  [script args]
  (trace (str "invoke-target [" *script-file* ":" *script-line* "] "
              script " " (print-args args)))
  (when-let [f (best-match (keyword (name script)))]
    (debug (str "Found implementation for " script " - " f
                " invoking with " (print-args args)))
    (apply f args)))

;; TODO - ensure that metadat is correctlplaced on the generated function
(defmacro defscript
  "Define a script fragment"
  [name [& args]]
  `(defn ~name [~@args]
     (apply dispatch-target (keyword (name ~name)) ~@(filter #(not (= '& %)) args))))

(defn add-to-scripts [scripts script-name specialisers f]
  (assoc scripts script-name
         (assoc (get *scripts* script-name {})
           specialisers f)))

