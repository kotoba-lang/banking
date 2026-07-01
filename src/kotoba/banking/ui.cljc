(ns kotoba.banking.ui
  "Operator-facing dashboard for a community banking actor.

  Renders an HTML read-only panel from banking records (accounts, postings,
  clearing batches) using kotoba-lang/html + css. Pure data → markup: no
  network, no DOM. The governor and audit ledger decide writes; this view
  only observes, so it can never leak a write path."
  (:require [html.core :as html]
            [css.core :as css]
            [kotoba.banking :as bank]))

(def ^:private sheet
  {:rules
   {"body"              {:font-family "system-ui,-apple-system,sans-serif"
                        :margin 0 :color "#1a1a1a" :background "#fafafa"}
    "header.bar"        {:display :flex :align-items :center :gap 12
                        :padding "12px 20px" :background "#fff"
                        :border-bottom "1px solid #e5e5e5"}
    "header.bar h1"     {:font-size 18 :margin 0 :font-weight 600}
    "header.bar .badge" {:margin-left :auto :font-size 12 :color "#666"}
    "main"              {:max-width 960 :margin "24px auto" :padding "0 20px"}
    ".card"             {:background "#fff" :border "1px solid #e5e5e5"
                        :border-radius 8 :padding 16 :margin-bottom 16}
    "h2"                {:margin-top 0 :font-size 15}
    "table"             {:width "100%" :border-collapse :collapse :font-size 14}
    "th, td"            {:text-align :left :padding "8px 10px"
                        :border-bottom "1px solid #f0f0f0"}
    "th"                {:font-weight 600 :color "#555" :font-size 12
                        :text-transform :uppercase :letter-spacing "0.04em"}
    "td.amt"            {:font-variant-numeric :tabular-nums :text-align :right}
    ".ok"               {:color "#137a3f"}
    ".warn"             {:color "#b25c00" :background "#fff8e1"
                        :padding "2px 6px" :border-radius 4}
    ".err"              {:color "#b3261e" :background "#fbe9e7"
                        :padding "2px 6px" :border-radius 4}
    ".muted"            {:color "#888"}}})

(defn- stylesheet []
  (html/->html (css/style-node sheet)))

(defn- money [n currency]
  (str (or n 0) " " (or currency "USD")))

(defn- status-badge [posting]
  (if (:ledger/unbalanced posting)
    [:span.err "unbalanced"]
    [:span.ok "balanced"]))

(defn accounts-table [accounts]
  [:table
   [:thead [:tr [:th "Account"] [:th "Holder"] [:th "Type"] [:th.amt "Balance"]]]
   [:tbody
    (for [a accounts]
      [:tr [:td (:account/id a)]
           [:td (or (:account/holder a) "—")]
           [:td (name (or (:account/type a) :checking))]
           [:td.amt (money (:account/balance a) (:account/currency a))]])]])

(defn postings-table [postings]
  [:table
   [:thead [:tr [:th "Posting"] [:th "Entries"] [:th "Status"] [:th "Memo"]]]
   [:tbody
    (for [p postings]
      [:tr [:td (:ledger/posting p)]
           [:td (count (:ledger/entries p))]
           [:td (status-badge p)]
           [:td (or (:ledger/memo p) [:span.muted "—"])]])]])

(defn clearing-table [batches]
  [:table
   [:thead [:tr [:th "Batch"] [:th "Postings"] [:th "Currency"] [:th "Status"]]]
   [:tbody (for [b batches]
             [:tr [:td (:clearing/id b)]
                  [:td (count (:clearing/postings b))]
                  [:td (:clearing/currency b)]
                  [:td (name (:clearing/status b))]])]])

(defn dashboard
  "Render a full HTML dashboard page for a banking operator."
  [{:keys [accounts postings clearing-batches] :as ctx}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · banking"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar
       [:h1 "Community Banking — Operator Console"]
       [:span.badge "read-only · governor-gated"]]
      [:main
       [:section.card [:h2 "Accounts"] (accounts-table accounts)]
       (when (seq postings)
         [:section.card [:h2 "Recent postings"] (postings-table postings)])
       (when (seq clearing-batches)
         [:section.card [:h2 "Clearing batches"] (clearing-table clearing-batches)])]]]))
