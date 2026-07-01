(ns kotoba.banking.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.banking :as bank]
            [kotoba.banking.ui :as ui]))

(deftest dashboard-renders-contracts
  (testing "empty dashboard still renders a page"
    (let [html (ui/dashboard {})]
      (is (re-find #"<html>" html))
      (is (re-find #"Operator Console" html))))
  (testing "accounts table carries id, holder and balance"
    (let [html (ui/dashboard {:accounts [(bank/account "A1" "USD" :holder "Acme")]})]
      (is (re-find #"A1" html))
      (is (re-find #"Acme" html))
      (is (re-find #"<table>" html))))
  (testing "unbalanced posting shows the warn badge"
    (let [html (ui/dashboard {:postings [(bank/posting "P1"
                                           [(bank/entry "A" :debit 100 "USD")
                                            (bank/entry "B" :credit 90 "USD")])]})]
      (is (re-find #"unbalanced" html))))
  (testing "balanced posting shows the ok badge"
    (let [html (ui/dashboard {:postings [(bank/posting "P1"
                                           [(bank/entry "A" :debit 100 "USD")
                                            (bank/entry "B" :credit 100 "USD")])]})]
      (is (re-find #"balanced" html))
      (is (not (re-find #"unbalanced" html))))))

(deftest dashboard-is-read-only
  (testing "the console never renders a write surface"
    (let [html (ui/dashboard {:accounts [(bank/account "A1" "USD")]})]
      (is (re-find #"read-only · governor-gated" html))
      (is (not (re-find #"<form" html)))
      (is (not (re-find #"<button" html))))))
