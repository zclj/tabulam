(ns tabulam.core
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clj-time.format :as tf]
            [org.httpkit.client :as http]
            [taoensso.nippy :as nippy]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn to-number [s]
  {:pre [(string? s)]}
  (let [prepared-string (clojure.string/replace s #" " "")]
    (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
          (Double/parseDouble (clojure.string/replace prepared-string #"," "."))
          (re-seq #"^[-+]?\d+$" prepared-string)
          (Integer/parseInt (clojure.string/replace prepared-string #"\+" ""))
          :else s)))

(defn ->map [f out-ks pull-ks]
  (zipmap out-ks (map #(to-number (f %)) pull-ks)))

;; Move to storage
(defn serialize-to-file [file-name data]
  (with-open [w (clojure.java.io/output-stream file-name)]
    (nippy/freeze-to-out! (java.io.DataOutputStream. w) data)))

(defn deserialize-from-file [file-name]
  (with-open [r (clojure.java.io/input-stream file-name)]
    (nippy/thaw-from-in! (java.io.DataInputStream. r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing of XML-api
(defn- rank->map [rank-loc]
  (->map (fn [k] (zx/attr rank-loc k))
         [:type :id :name :friendly-name :value :bayes-average]
         [:type :id :name :friendlyname :value :bayesaverage]))

(defn- values->map [loc out-ks pull-ks]
  (->map (fn [k] (zx/attr (first (zx/xml-> loc k)) :value))
         out-ks
         pull-ks))

(defn- ratings-values->map [rating-loc]
  (values->map rating-loc
               [:users-rated :average :bayes-average :std-deviation
                :median :owned :trading :wanting :wishing :number-of-comments
                :number-of-weights :average-weight]
               [:usersrated :average :bayesaverage :stddev :median :owned
                :trading :wanting :wishing :numcomments :numweights :averageweight]))

(defn- game-info->map [item-loc]
  (values->map item-loc
               [:year-published :minimum-players :max-players :playing-time :minimum-age]
               [:yearpublished :minplayers :maxplayers :playingtime :minage]))

(defn- rating->map [rating]
  (merge
   {:date (tf/parse (tf/formatter "yyyyMMdd") (zx/attr rating :date))
    :ranks (mapv rank->map (zx/xml-> rating :ranks :rank))}
   (ratings-values->map rating)))

(defn- get-name [loc]
  (first
   (filter #(not (nil? %))
           (for [names-loc (zx/xml-> loc :item :name)]
             (if (= (zx/attr names-loc :type) "primary")
               (zx/attr names-loc :value))))))

(defn- poll->map [poll-name loc]  
  (let [[parse-f k]
        (condp = poll-name
          "suggested_numplayers" [(fn [poll-value]
                                    (let [nrof-players (zx/attr loc :numplayers)]
                                      (str (if (re-find #"\+" nrof-players)
                                             "more-than-max-players-"
                                             (str nrof-players "-players-"))
                                           poll-value)))
                                  :value]
          "suggested_playerage" [(fn [poll-value] poll-value) :value]
          "language_dependence" [(fn [poll-value] (str "level-" poll-value)) :level])]
    (for [result-loc (zx/xml-> loc :result)]
      {(keyword (parse-f (clojure.string/replace
                          (clojure.string/lower-case (zx/attr result-loc k)) " " "-")))
       (to-number (zx/attr result-loc :numvotes))})))

(defn- polls->map [loc]
  (for [poll-loc (zx/xml-> loc :item :poll)]
    (let [total-votes (to-number (zx/attr poll-loc :totalvotes))
          title (zx/attr poll-loc :title)
          name (zx/attr poll-loc :name)
          results (zx/xml-> poll-loc :results)]
      {(keyword (clojure.string/replace name "_" "-"))
       {:total-votes total-votes
        :title title
        :results (apply merge (mapcat #(poll->map name %) results))}})))

(defn- link->map [link-loc]
  {:type (zx/attr link-loc :type)
   :id (to-number (zx/attr link-loc :id))
   :value (zx/attr link-loc :value)})

(defn- merge-ratings [pages-locs]
  (into
   []
   (flatten
    (for [ratings (map #(zx/xml-> % :item :statistics :ratings) pages-locs)]
      (map rating->map ratings)))))

(defmacro from-page [& form]
  `(to-number (first (zx/xml-> ~'game-root-loc :item ~@form))))

(defn game->map [pages-locs]
  (let [game-root-loc (first pages-locs)]
    (merge
     {:type (from-page (zx/attr :type))
      :id (from-page (zx/attr :id))
      :name (get-name game-root-loc)
      :polls (into [] (polls->map game-root-loc))
      :links (mapv link->map (zx/xml-> game-root-loc :item :link))
      :ratings (merge-ratings pages-locs)
      :thumbnail-url (from-page :thumbnail zx/text)
      :image-url (from-page :image zx/text)
      :description (from-page :description zx/text)}
     (game-info->map (first (zx/xml-> game-root-loc :item))))))

(defn get-game-page [thing-id page-number]
  (let [url (str "http://www.boardgamegeek.com/xmlapi2/thing?id="
                 thing-id "&page=" page-number "&historical=1")
        body (:body @(http/get url))]
    (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes body))))))

(defn get-game-history [thing-id starting-page]
  (loop [page starting-page
         games []]
    (let [game (get-game-page thing-id page)]
      (if (= 100 (count (zx/xml-> game :item :statistics :ratings)))
        (do
          (Thread/sleep 5000)
          (recur (inc page) (conj games game)))
        {:game-data (game->map (conj games game))
         :number-of-pages page
         :time-of-data (clj-time.core/now)}))))
