(ns kotoba.banking.export
  "Operator-facing export for a community banking actor.

  Renders banking records to CSV and JSON for reconciliation, audit export
  and downstream reporting. Pure data → text: no network, no I/O. Exports
  are read-only evidence the audit ledger can append; they never mutate
  accounts or postings."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [kotoba.banking :as bank]))

(defn- csv-cell [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[\",\n]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [vals]
  (str/join "," (map csv-cell vals)))

(defn- json-str [v]
  (-> (str (if (nil? v) "" v))
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")))

(defn accounts->csv
  "Export accounts to CSV. Header row + one row per account."
  [accounts]
  (str/join "\n"
    (cons (csv-row ["id" "holder" "type" "balance" "currency"])
          (for [a accounts]
            (csv-row [(:account/id a)
                      (:account/holder a)
                      (name (or (:account/type a) :checking))
                      (:account/balance a)
                      (:account/currency a)])))))

(defn postings->csv
  "Export postings to CSV. Flags unbalanced postings in a `status` column."
  [postings]
  (str/join "\n"
    (cons (csv-row ["posting_id" "entries" "status" "memo"])
          (for [p postings]
            (csv-row [(:ledger/posting p)
                      (count (:ledger/entries p))
                      (if (:ledger/unbalanced p) "unbalanced" "balanced")
                      (or (:ledger/memo p) "")])))))

(defn accounts->json
  "Export accounts to a JSON string."
  [accounts]
  (str "["
       (str/join ","
                 (for [a accounts]
                   (str "{\"id\":\"" (json-str (:account/id a)) "\","
                        "\"holder\":\"" (json-str (:account/holder a)) "\","
                        "\"type\":\"" (name (or (:account/type a) :checking)) "\","
                        "\"balance\":" (or (:account/balance a) 0) ","
                        "\"currency\":\"" (or (:account/currency a) "USD") "\"}")))
       "]"))

(defn postings->json
  "Export postings to a JSON string."
  [postings]
  (str "["
       (str/join ","
                 (for [p postings]
                   (str "{\"posting_id\":\"" (json-str (:ledger/posting p)) "\","
                        "\"entries\":" (count (:ledger/entries p)) ","
                        "\"status\":\"" (if (:ledger/unbalanced p) "unbalanced" "balanced") "\","
                        "\"memo\":\"" (json-str (:ledger/memo p)) "\"}")))
       "]"))
