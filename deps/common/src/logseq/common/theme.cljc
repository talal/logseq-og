(ns logseq.common.theme
  (:require [clojure.string :as string]))

(defn- normalize-relative-path
  [url]
  (when (string? url)
    (let [url (string/replace url "\\" "/")
          parts (string/split url #"/")
          parts (if (= "." (first parts)) (rest parts) parts)]
      (when (and (seq parts)
                 (not (some #(or (string/blank? %) (= ".." %)) parts)))
        (string/join "/" parts)))))

(defn local-css-path?
  "Returns true when URL is a relative path to a CSS file."
  [url]
  (let [normalized (normalize-relative-path url)]
    (and normalized
         (not (re-find #"(?i)^(?:[a-z][a-z0-9+.-]*:|//|/|[a-z]:)" normalized))
         (string/ends-with? (string/lower-case normalized) ".css"))))
(defn theme-selection-key
  [{:keys [theme-id url name mode]}]
  (str theme-id "/" url "/" (or mode "any") "/" name))

(defn- valid-mode?
  [mode]
  (or (nil? mode)
      (contains? #{"light" "dark"} mode)))

(defn manifest->themes
  "Converts a theme package manifest into safe, selectable CSS themes.

  Theme package manifests are read from graph-local theme directories. Only the
  observed `:logseq {:themes [...]}` format is accepted; executable package
  entries and remote URLs are intentionally ignored.
  "
  [theme-id manifest]
  (let [entries (get-in manifest [:logseq :themes])]
    (if (and (string? theme-id) (vector? entries))
      (->> entries
           (keep (fn [{:keys [name url mode description]}]
                   (let [url (normalize-relative-path url)]
                     (when (and (string? name)
                                (not (string/blank? name))
                                (local-css-path? url)
                                (valid-mode? mode))
                       (cond-> {:id       (theme-selection-key {:theme-id theme-id
                                                                :url      url
                                                                :name     name
                                                                :mode     mode})
                                :theme-id theme-id
                                :name     name
                                :url      url
                                :mode     mode}
                         (some? description) (assoc :description description))))))
           vec)
      [])))

(defn sort-themes
  [themes]
  (sort-by (juxt :theme-id :name :url) themes))

(defn active-for-mode?
  [{:keys [mode]} current-mode]
  (or (nil? mode) (= mode current-mode)))
