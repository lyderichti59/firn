(ns firn.util
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [sci.core :as sci]))

(defn get-files-of-type
  "Takes an io/file sequence and gets all files of a specific extension."
  [fileseq ext]
  (filter (fn [f]
            (let [is-file        (.isFile ^java.io.File f)
                  file-name      (.getName ^java.io.File f)
                  file-ends-with (s/ends-with? file-name ext)]
              (and is-file file-ends-with)))
          fileseq))

(defn native-image?
  "Check if we are in the native-image or REPL."
  []
  (and (= "Substrate VM" (System/getProperty "java.vm.name"))
       (= "runtime" (System/getProperty "org.graalvm.nativeimage.imagecode"))))

(defn print-err!
  "A custom error function.
  Prints errors, expecting a type to specified (:warning, :error etc.)
  Currently, also returns false after printing error message, so we can
  use that for control flow or for tests.
  TODO: read up on error testing and how to best handle these things.
  "
  [typ & args]
  (let [err-types   {:warning       "🚧 Warning:"
                     :error         "❗ Error:"
                     :uncategorized "🗒 Uncategorized Error:"}
        sel-log-typ (get err-types typ (get err-types :uncategorized))]
    (apply println sel-log-typ args)
    (System/exit 1))) ;; FIXME: this is the correct usage, but makes testing difficult as it interrupts lein test.

(defn str->keywrd
  "Converts a string to a keyword"
  [& args]
  (keyword (apply str args)))

(def ^{:doc "Current working directory. This cannot be changed in the JVM.
             Changing this will only change the working directory for functions
             in this library."
       :dynamic true}
  *cwd* (.getCanonicalFile (io/file ".")))

(defn ^java.io.File file
  "If `path` is a period, replaces it with cwd and creates a new File object
   out of it and `paths`. Or, if the resulting File object does not constitute
   an absolute path, makes it absolutely by creating a new File object out of
   the `paths` and cwd."
  [path & paths]
  (when-let [path (apply io/file (if (= path ".") *cwd* path) paths)]
    (if (.isAbsolute ^java.io.File path)
      path
      (io/file *cwd* path))))

(defn find-files*
  "Find files in `path` by `pred`."
  [path pred]
  (filter pred (-> path file file-seq)))

(defn find-files
  "Find files matching given `pattern`."
  [path pattern]
  (find-files* path #(re-matches pattern (.getName ^java.io.File %))))

(defn find-files-by-ext
  "Traverses a directory for all files of a specific extension."
  [dir ext]
  (let [ext-regex (re-pattern (str "^.*\\.(" ext ")$"))
        files     (find-files dir ext-regex)]
    (if (= 0 (count files))
      (do (println "No" ext "files found at " dir) files)
      files)))

(defn file-name-no-ext
  "Removes an extension from a filename"
  [io-file]
  (let [f (.getName ^java.io.File io-file)]
    (-> f (s/split #"\.") (first))))

(defn remove-ext
  "removes an extension from a string. TODO: test me"
  ([s]
   (-> s (s/split #"\.") first))
  ([s ext]
   (let [split (s/split s #"\.")
         filename (first split)
         -ext (last split)]
     (if (= ext -ext) filename s))))

(defn io-file->keyword
  "Turn a filename into a keyword."
  [io-file]
  (-> io-file file-name-no-ext keyword))

(defn load-fns-into-map
  "Takes a list of files and returns a map of filenames as :keywords -> file
  NOTE: It also EVALS (using sci) the files so they are in memory functions!
 
  so:                  `[my-file.clj my-layout.clj]`
  ------------------------------- ▼ ▼ ▼ ----------------------------------------
  becomes:    {:my-file fn-evald-1, :my-layout fn-evald-2}"

  [file-list]
  (let [file-path #(.getPath ^java.io.File %)
        eval-file #(-> % file-path slurp sci/eval-string)]
    (into {} (map #(hash-map (io-file->keyword %) (eval-file %)) file-list))))

(defn find-first
  "Find the first item in a collection."
  [f coll]
  (first (filter f coll)))

(defn get-cwd
  "Because *fs/cwd* gives out the at-the-time jvm path. this works with graal."
  []
  (s/join "/" (-> (java.io.File. ".")
                  .getAbsolutePath
                  (s/split #"/")
                  drop-last)))

(defn dupe-name-in-dir-path?
  "Takes a str path of a directory and checks if a folder name appears more than
  once in the path"
  [dir-path dir-name]
  (> (get (frequencies (s/split dir-path #"/")) dir-name) 1))

(def spy #(do (println "DEBUG:" %) %))
