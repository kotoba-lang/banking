(ns kotoba.banking.export-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.banking :as bank]
            [kotoba.banking.export :as ex]))

(def accs [(bank/account "A1" "USD" :holder "Acme")
           (bank/account "A2" "EUR" :holder "Be,ta" :type :savings)])

(def posts [(bank/posting "P1"
              [(bank/entry "A1" :debit 100 "USD")
               (bank/entry "A2" :credit 90 "USD")]
              :memo "review, needed")])

(deftest csv-export
  (testing "accounts CSV has header and rows"
    (let [csv (ex/accounts->csv accs)]
      (is (re-find #"id,holder,type,balance,currency" csv))
      (is (re-find #"A1,Acme,checking" csv))))
  (testing "comma in holder is quoted"
    (is (re-find #"\"Be,ta\"" (ex/accounts->csv accs))))
  (testing "postings CSV flags unbalanced"
    (let [csv (ex/postings->csv posts)]
      (is (re-find #"unbalanced" csv))
      (is (re-find #"\d,2,unbalanced" csv)))))

(deftest json-export
  (testing "accounts JSON is a non-empty array"
    (let [j (ex/accounts->json accs)]
      (is (re-find #"^\[" j))
      (is (re-find #"\]$" j))
      (is (re-find #"\"id\":\"A1\"" j))))
  (testing "JSON escapes quotes and commas stay literal in string"
    (let [j (ex/accounts->json accs)]
      (is (re-find #"\"holder\":\"Be,ta\"" j))
      (is (not (re-find #"\"\"Be" j))))))  ; no double-quote escaping artifact
