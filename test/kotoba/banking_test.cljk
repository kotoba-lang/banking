(ns kotoba.banking-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.banking :as bank]))

(deftest iban-valid-test
  (testing "accepts well-known valid IBANs"
    (is (bank/iban-valid? "GB82WEST12345698765432"))
    (is (bank/iban-valid? "DE89370400440532013000")))
  (testing "rejects a bad checksum (last digit changed)"
    (is (not (bank/iban-valid? "GB82WEST12345698765433"))))
  (testing "rejects malformed"
    (is (not (bank/iban-valid? "GB")))
    (is (not (bank/iban-valid? 42))))
  (testing "ignores spaces and case"
    (is (bank/iban-valid? "gb82 west 1234 5698 7654 32"))))

(deftest parse-iban-test
  (let [p (bank/parse-iban "GB82WEST12345698765432")]
    (is (= "GB" (:iban/country p)))
    (is (= "82" (:iban/check-digits p)))
    (is (= "WEST12345698765432" (:iban/bban p))))
  (is (nil? (bank/parse-iban "BAD"))))

(deftest account-test
  (is (= {:account/id "A1" :account/currency "USD"
          :account/holder "Acme" :account/type :savings :account/balance 0}
         (bank/account "A1" "USD" :holder "Acme" :type :savings))))

(deftest balanced-test
  (testing "balanced per currency"
    (is (bank/balanced? [(bank/entry "A" :debit 100 "USD")
                         (bank/entry "B" :credit 100 "USD")])))
  (testing "unbalanced"
    (is (not (bank/balanced? [(bank/entry "A" :debit 100 "USD")
                              (bank/entry "B" :credit 90 "USD")]))))
  (testing "balanced across currencies but not within"
    (is (not (bank/balanced? [(bank/entry "A" :debit 100 "USD")
                              (bank/entry "B" :credit 100 "EUR")]))))
  (testing "rejects unknown side"
    (is (nil? (bank/entry "A" :wrong 100 "USD")))))

(deftest posting-test
  (testing "balanced posting is clean"
    (let [p (bank/posting "P1" [(bank/entry "A" :debit 100 "USD")
                                (bank/entry "B" :credit 100 "USD")])]
      (is (:ledger/balanced? p))
      (is (nil? (:ledger/unbalanced p)))))
  (testing "unbalanced posting is flagged"
    (let [p (bank/posting "P2" [(bank/entry "A" :debit 100 "USD")
                                (bank/entry "B" :credit 90 "USD")]
                          :memo "needs review")]
      (is (false? (:ledger/balanced? p)))
      (is (:ledger/unbalanced p))
      (is (= "needs review" (:ledger/memo p))))))

(deftest clearing-batch-test
  (let [b (bank/clearing-batch "C1" [] :settlement-currency "USD")]
    (is (= "C1" (:clearing/id b)))
    (is (= :pending (:clearing/status b)))
    (is (= "USD" (:clearing/currency b)))))

(deftest iban-edge-cases
  (testing "lowercase IBAN is normalized and valid"
    (is (bank/iban-valid? "gb82west12345698765432")))
  (testing "too short is rejected"
    (is (not (bank/iban-valid? "GB82WEST")))
    (is (not (bank/iban-valid? "G1"))))
  (testing "non-string and empty are rejected"
    (is (not (bank/iban-valid? nil)))
    (is (not (bank/iban-valid? ""))))
  (testing "malformed returns nil parse"
    (is (nil? (bank/parse-iban "BAD")))))

(deftest ledger-edge-cases
  (testing "empty entries are balanced (vacuously)"
    (is (bank/balanced? [])))
  (testing "multi-currency imbalance is detected per currency"
    (is (not (bank/balanced? [(bank/entry "A" :debit 100 "USD")
                              (bank/entry "B" :credit 100 "EUR")]))))
  (testing "balanced across multiple currencies when each balances"
    (is (bank/balanced? [(bank/entry "A" :debit 100 "USD")
                         (bank/entry "B" :credit 100 "USD")
                         (bank/entry "C" :debit 50 "EUR")
                         (bank/entry "D" :credit 50 "EUR")]))))
