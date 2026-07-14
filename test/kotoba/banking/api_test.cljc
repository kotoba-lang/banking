(ns kotoba.banking.api-test
  "Tests for kotoba.banking.api — construct/parse round-trips, malformed
  input rejection, and field-name/status-enum verification against the
  Berlin Group NextGenPSD2 XS2A OpenAPI definition v1.3.11 (2021-09-24):
  https://github.com/adorsys/xs2a/blob/develop/xs2a-impl/src/main/resources/static/psd2-api_v1.3.11-2021-10-01v1.yaml"
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.banking :as bank]
            [kotoba.banking.api :as api]))

(def gbr-account (bank/account "GB82WEST12345698765432" "GBP" :holder "Alice Ltd"))
(def deu-account (bank/account "DE89370400440532013000" "EUR" :holder "Acme GmbH"))

;; ---------------------------------------------------------------------------
;; Enums verified against the real OpenAPI schema
;; ---------------------------------------------------------------------------

(deftest transaction-status-enum-matches-spec
  (testing "exactly the 14 codes in schema #/components/schemas/transactionStatus"
    (is (= #{"ACCC" "ACCP" "ACSC" "ACSP" "ACTC" "ACWC" "ACWP"
             "RCVD" "PDNG" "RJCT" "CANC" "ACFC" "PATC" "PART"}
           (set (keys api/transaction-statuses)))))
  (testing "the five codes called out in the task's payload spec are real and correctly described"
    (is (api/transaction-status? "RCVD"))
    (is (re-find #"(?i)received" (get api/transaction-statuses "RCVD")))
    (is (api/transaction-status? "PDNG"))
    (is (re-find #"(?i)pending" (get api/transaction-statuses "PDNG")))
    (is (api/transaction-status? "ACTC"))
    (is (re-find #"(?i)technical.?validation" (get api/transaction-statuses "ACTC")))
    (is (api/transaction-status? "ACSC"))
    (is (re-find #"(?i)settlement" (get api/transaction-statuses "ACSC")))
    (is (api/transaction-status? "RJCT"))
    (is (re-find #"(?i)rejected" (get api/transaction-statuses "RJCT"))))
  (testing "rejects an invented code"
    (is (not (api/transaction-status? "MADE_UP")))
    (is (not (api/transaction-status? nil)))))

(deftest payment-products-are-the-four-json-bodied-products
  (is (= #{"sepa-credit-transfers" "instant-sepa-credit-transfers"
           "target-2-payments" "cross-border-credit-transfers"}
         api/payment-products)))

(deftest balance-type-and-cash-account-type-enums
  (is (contains? api/balance-types "closingBooked"))
  (is (contains? api/balance-types "interimBooked"))
  (is (not (contains? api/balance-types "madeUp")))
  (testing "cashAccountType draws from ISO 20022 ExternalCashAccountType1Code"
    (is (contains? api/cash-account-types "CACC"))   ; current account
    (is (contains? api/cash-account-types "SVGS"))    ; savings account
    (is (not (contains? api/cash-account-types "ZZZZ")))))

;; ---------------------------------------------------------------------------
;; accountReference construction / parsing
;; ---------------------------------------------------------------------------

(deftest account-reference-round-trip
  (testing "IBAN account maps to the iban form"
    (let [ref (api/account->account-reference gbr-account)]
      (is (= "GB82WEST12345698765432" (get ref "iban")))
      (is (= "GBP" (get ref "currency")))
      (is (nil? (get ref "other")))))
  (testing "non-IBAN account id maps to the schema's other/identification fallback"
    (let [acc (bank/account "internal-A1" "USD")
          ref (api/account->account-reference acc)]
      (is (= "internal-A1" (get-in ref ["other" "identification"])))
      (is (nil? (get ref "iban")))))
  (testing "round trip recovers id and currency"
    (let [ref (api/account->account-reference deu-account)
          acc (api/account-reference->account ref)]
      (is (= "DE89370400440532013000" (:account/id acc)))
      (is (= "EUR" (:account/currency acc)))))
  (testing "malformed reference (neither iban nor other) parses to nil"
    (is (nil? (api/account-reference->account {"currency" "EUR"})))))

;; ---------------------------------------------------------------------------
;; Payment initiation request — POST /v1/payments/{payment-product}
;; ---------------------------------------------------------------------------

(def valid-request-fields
  {:payment-product "sepa-credit-transfers"
   :debtor-account   gbr-account
   :creditor-account deu-account
   :creditor-name    "Acme GmbH"
   :amount           1050
   :currency         "EUR"
   :end-to-end-identification "E2E-1"
   :remittance-information-unstructured "Invoice 2026-07"})

(deftest payment-initiation-request-real-field-names
  (let [req (api/payment-initiation-request valid-request-fields)]
    (testing "top-level required fields, matching schema paymentInitiation_json"
      (is (= #{"debtorAccount" "instructedAmount" "creditorAccount" "creditorName"
               "endToEndIdentification" "remittanceInformationUnstructured"}
             (set (keys req)))))
    (testing "instructedAmount is {currency, amount} with amount as a decimal STRING"
      (is (= "EUR" (get-in req ["instructedAmount" "currency"])))
      (is (= "10.50" (get-in req ["instructedAmount" "amount"])))
      (is (string? (get-in req ["instructedAmount" "amount"]))))
    (testing "debtor/creditor accounts carry real iban/currency keys"
      (is (= "GB82WEST12345698765432" (get-in req ["debtorAccount" "iban"])))
      (is (= "DE89370400440532013000" (get-in req ["creditorAccount" "iban"])))
      (is (= "Acme GmbH" (get req "creditorName"))))))

(deftest payment-initiation-request-rejects-malformed-input
  (testing "invalid debtor IBAN"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                           #"invalid payment initiation request"
                           (api/payment-initiation-request
                             (assoc valid-request-fields :debtor-account "NOT-AN-IBAN")))))
  (testing "invalid creditor IBAN surfaces :bad-creditor-iban"
    (let [e (try (api/payment-initiation-request
                   (assoc valid-request-fields :creditor-account "NOT-AN-IBAN"))
                 (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e))]
      (is (some #{:bad-creditor-iban} (:api/errors (ex-data e))))))
  (testing "missing creditor name"
    (let [e (try (api/payment-initiation-request (assoc valid-request-fields :creditor-name nil))
                 (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e))]
      (is (some #{:missing-creditor-name} (:api/errors (ex-data e))))))
  (testing "zero/negative amount"
    (let [e (try (api/payment-initiation-request (assoc valid-request-fields :amount 0))
                 (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e))]
      (is (some #{:bad-amount} (:api/errors (ex-data e))))))
  (testing "malformed currency"
    (let [e (try (api/payment-initiation-request (assoc valid-request-fields :currency "euro"))
                 (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e))]
      (is (some #{:bad-currency} (:api/errors (ex-data e))))))
  (testing "unknown payment product"
    (let [e (try (api/payment-initiation-request (assoc valid-request-fields :payment-product "bogus-product"))
                 (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e e))]
      (is (some #{:bad-payment-product} (:api/errors (ex-data e)))))))

(deftest payment-initiation-request-round-trip
  (let [req    (api/payment-initiation-request valid-request-fields)
        parsed (api/parse-payment-initiation-request req)]
    (is (= 1050 (:api/amount parsed)))
    (is (= "EUR" (:api/currency parsed)))
    (is (= "Acme GmbH" (:api/creditor-name parsed)))
    (is (= "E2E-1" (:api/end-to-end-identification parsed)))
    (is (= "Invoice 2026-07" (:api/remittance-information-unstructured parsed)))
    (is (= "GB82WEST12345698765432" (:account/id (:api/debtor-account parsed))))
    (is (= "DE89370400440532013000" (:account/id (:api/creditor-account parsed))))))

(deftest parse-payment-initiation-request-rejects-malformed-body
  (is (nil? (api/parse-payment-initiation-request {})))
  (is (nil? (api/parse-payment-initiation-request {"debtorAccount" {"iban" "x"}})))
  (is (nil? (api/parse-payment-initiation-request "not-a-map"))))

;; ---------------------------------------------------------------------------
;; Payment initiation response — 201 body
;; ---------------------------------------------------------------------------

(deftest payment-initiation-response-real-field-names
  (let [resp (api/payment-initiation-response "1234-wertiq-983" "RCVD"
                                               :self-path "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                                               :status-path "/v1/payments/sepa-credit-transfers/1234-wertiq-983/status")]
    (is (= "1234-wertiq-983" (get resp "paymentId")))
    (is (= "RCVD" (get resp "transactionStatus")))
    (is (= "/v1/payments/sepa-credit-transfers/1234-wertiq-983" (get-in resp ["_links" "self" "href"])))
    (is (= "/v1/payments/sepa-credit-transfers/1234-wertiq-983/status" (get-in resp ["_links" "status" "href"])))))

(deftest payment-initiation-response-rejects-unknown-status
  (is (nil? (api/payment-initiation-response "p1" "NOT_A_REAL_STATUS"))))

(deftest payment-initiation-response-round-trip
  (let [resp   (api/payment-initiation-response "p1" "ACSC")
        parsed (api/parse-payment-initiation-response resp)]
    (is (:api/valid? parsed))
    (is (= "p1" (:api/payment-id parsed)))
    (is (= "ACSC" (:api/transaction-status parsed))))
  (testing "malformed response body parses invalid"
    (is (not (:api/valid? (api/parse-payment-initiation-response {"transactionStatus" "RCVD"}))))
    (is (not (:api/valid? (api/parse-payment-initiation-response {"paymentId" "p1"}))))))

;; ---------------------------------------------------------------------------
;; tppMessages error body
;; ---------------------------------------------------------------------------

(deftest tpp-message-and-error-response
  (testing "well-formed ERROR message, code drawn from the verified catalog"
    (is (contains? api/tpp-message-codes "FORMAT_ERROR"))
    (let [m (api/tpp-message "ERROR" "FORMAT_ERROR" :path "instructedAmount" :text "bad amount")]
      (is (= "ERROR" (get m "category")))
      (is (= "FORMAT_ERROR" (get m "code")))
      (is (= "instructedAmount" (get m "path")))))
  (testing "unknown category rejected"
    (is (nil? (api/tpp-message "BOGUS" "FORMAT_ERROR"))))
  (testing "error-response wraps tppMessages and drops nils"
    (let [body (api/error-response [(api/tpp-message "ERROR" "FORMAT_ERROR")
                                     (api/tpp-message "BOGUS" "X")])]
      (is (= 1 (count (get body "tppMessages"))))
      (is (= "FORMAT_ERROR" (get-in body ["tppMessages" 0 "code"]))))))

;; ---------------------------------------------------------------------------
;; Account information — GET /v1/accounts, GET .../balances
;; ---------------------------------------------------------------------------

(deftest account-details-round-trip
  (let [details (api/account->account-details deu-account "res-1"
                                                :product "Girokonto"
                                                :cash-account-type "CACC"
                                                :status "enabled")]
    (is (= "res-1" (get details "resourceId")))
    (is (= "DE89370400440532013000" (get details "iban")))
    (is (= "EUR" (get details "currency")))
    (is (= "Girokonto" (get details "product")))
    (is (= "CACC" (get details "cashAccountType")))
    (is (= "enabled" (get details "status")))
    (is (= "Acme GmbH" (get details "name")))
    (let [acc (api/account-details->account details)]
      (is (= "DE89370400440532013000" (:account/id acc)))
      (is (= "EUR" (:account/currency acc))))))

(deftest account-details-ignores-unknown-cash-account-type-and-status
  (let [details (api/account->account-details deu-account "res-2"
                                                :cash-account-type "BOGUS"
                                                :status "not-real")]
    (is (nil? (get details "cashAccountType")))
    (is (nil? (get details "status")))))

(deftest accounts->account-list-response-wraps-accounts
  (let [details (api/account->account-details deu-account "res-1")
        body    (api/accounts->account-list-response [details])]
    (is (= [details] (get body "accounts")))))

(deftest balance-and-balances-response
  (let [funded (assoc deu-account :account/balance 250000)]
    (testing "single balance object"
      (let [b (api/account->balance funded :balance-type "closingBooked" :reference-date "2026-07-14")]
        (is (= "closingBooked" (get b "balanceType")))
        (is (= "EUR" (get-in b ["balanceAmount" "currency"])))
        (is (= "2500.00" (get-in b ["balanceAmount" "amount"])))
        (is (= "2026-07-14" (get b "referenceDate")))))
    (testing "unknown balance-type rejected"
      (is (nil? (api/account->balance funded :balance-type "madeUp"))))
    (testing "balances response shape"
      (let [resp (api/account->balances-response funded :types ["interimBooked" "expected"])]
        (is (= "DE89370400440532013000" (get-in resp ["account" "iban"])))
        (is (= 2 (count (get resp "balances"))))
        (is (= #{"interimBooked" "expected"} (set (map #(get % "balanceType") (get resp "balances")))))))))
