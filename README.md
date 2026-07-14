# kotoba-banking

[![CI](https://github.com/kotoba-lang/banking/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/banking/actions/workflows/ci.yml)

**Banking accounts, double-entry ledger, clearing, and an Open Banking
(PSD2/XS2A) API layer, in pure Clojure.** A
[kotoba-lang](https://github.com/kotoba-lang) capability library that gives
the [`cloud-itonami-6419`](https://github.com/gftdcojp/cloud-itonami-6419)
community monetary-intermediation open business — and other actors such as
`cloud-itonami-isic-6493` (factoring) that need REAL banking-API shapes to
eventually integrate against — the records a banking operator keeps: IBAN
(ISO 13616) identification with mod-97 checksum validation, account
records, a double-entry ledger with balanced-posting validation, a
clearing-batch contract for settlement, and `kotoba.banking.api`'s
Open Banking payment-initiation/account-information request/response
construction (see below).

No network, no I/O. Amounts are plain numbers in the smallest unit of the
account currency (e.g. cents) — no BigDecimal assumption, keeping the
library portable `.cljc` across JVM / ClojureScript / SCI / GraalVM.


## Maturity

| | |
|---|---|
| Role | capability |
| Tests | 143 assertions, all green |
| Open Banking API layer | yes (`kotoba.banking.api`, Berlin Group NextGenPSD2 XS2A v1.3.11) |
| Operator console (UI/UX) | yes |
| Export (CSV/JSON) | yes |
| Shared CSS design system | yes (css.core/operator-theme) |

## Contract

```clojure
(require '[kotoba.banking :as bank])

(bank/iban-valid? "GB82WEST12345698765432")           ; => true
(bank/parse-iban "GB82WEST12345698765432")            ; => {:iban/country "GB" ...}
(bank/account "A1" "USD" :holder "Acme" :type :savings)
(bank/balanced? [(bank/entry "A" :debit 100 "USD")
                 (bank/entry "B" :credit 100 "USD")]) ; => true
(bank/posting "P1" [(bank/entry "A" :debit 100 "USD")
                    (bank/entry "B" :credit  90 "USD")]) ; flagged :ledger/unbalanced
```

## Open Banking API layer (`kotoba.banking.api`)

Real, spec-accurate REST/JSON payment-initiation and account-information
request/response construction — distinct from `kotoba-lang/swift`'s
interbank *messaging* (SWIFT MT / ISO 20022) — targeting the **Berlin Group
NextGenPSD2 XS2A Framework**, Implementation Guidelines + OpenAPI
definition **version 1.3.11 (2021-09-24)**, the EU/DEU PSD2 Open Banking
standard this fleet already anchors to (BaFin, in `cloud-itonami-isic-6419`'s
`banking.facts` catalog). Field names and the `transactionStatus` enum are
transcribed from the Berlin Group's own OpenAPI schema (mirrored in
[adorsys/xs2a](https://github.com/adorsys/xs2a), the Berlin-Group-affiliated
reference open-source XS2A implementation), cross-checked against a live
ASPSP's generated API docs
([Memo Bank](https://docs-nextgenpsd2.api.memo.bank/operation/operation-initiatepayment))
for the field list and `tppMessages` error-code catalog. See
`docs/adr/0001-open-banking-api-layer.md` for the full source list.

**Covered:** single payment-initiation request/response
(`POST /v1/payments/{payment-product}`, the four JSON-bodied payment
products), the real 14-value `transactionStatus` enum (`RCVD`/`PDNG`/
`ACTC`/`ACSC`/`RJCT`/...), the `tppMessages`-shaped 4xx error body, and
account information (`GET /v1/accounts`, `GET
/v1/accounts/{account-id}/balances`) — all wired to/from the
`kotoba.banking` `account` record and `iban-valid?`, not a duplicate data
model.

**NOT covered** (this is a scoped slice of the XS2A catalogue, not the
whole thing): consent management (`/v1/consents`), any
strong-customer-authentication (SCA) flow (redirect/OAuth2/decoupled/
embedded authorisation sub-resources), bulk or periodic payments, payment
cancellation, the transaction list (`/transactions`), card accounts, and
the `pain.001`-XML-bodied payment products.

**Secondary jurisdictions (documented in the namespace docstring, NOT
implemented):** UK Open Banking (OBIE) Payment Initiation API
domestic-payments (PascalCase fields, sort-code+account-number
identification instead of IBAN-first) and Japan's 全銀協 (Japanese Bankers
Association) 銀行分野のオープンAPIに係る電文仕様標準 (fund-transfer/振込
is a defined category, though update-type bank Open APIs remain rare in
production per the Association's own reporting). Neither is
spec-conformantly implemented here — report accordingly, don't overclaim.

```clojure
(require '[kotoba.banking.api :as api])

(def payer   (bank/account "GB82WEST12345698765432" "GBP" :holder "Alice Ltd"))
(def payee   (bank/account "DE89370400440532013000" "EUR" :holder "Acme GmbH"))

(api/payment-initiation-request
  {:payment-product "sepa-credit-transfers"
   :debtor-account payer :creditor-account payee
   :creditor-name "Acme GmbH" :amount 1050 :currency "EUR"})
;; => {"debtorAccount" {"iban" "GB82WEST..." "currency" "GBP"}
;;     "instructedAmount" {"currency" "EUR" "amount" "10.50"}
;;     "creditorAccount" {"iban" "DE893704..." "currency" "EUR"}
;;     "creditorName" "Acme GmbH"}

(api/payment-initiation-response "1234-wertiq-983" "RCVD")
;; => {"transactionStatus" "RCVD" "paymentId" "1234-wertiq-983" "_links" {}}

(api/account->balances-response payee)
;; => {"account" {"iban" "DE893704..." "currency" "EUR"}
;;     "balances" [{"balanceAmount" {"currency" "EUR" "amount" "0.00"} "balanceType" "interimBooked"}]}
```

## Operator console (UI/UX)

A read-only HTML dashboard renders accounts, postings, clearing batches,
Open Banking payment-initiation responses and account-details for an
operator. Built on [`kotoba-lang/html`](https://github.com/kotoba-lang/html)
(Hiccup→HTML) + [`kotoba-lang/css`](https://github.com/kotoba-lang/css)
(EDN→CSS). Pure data → markup; the console never exposes a write surface
(no `<form>`/`<button>`) — writes stay behind the governor.

```clojure
(require '[kotoba.banking.ui :as ui])

(ui/dashboard
  {:accounts [(bank/account "A1" "USD" :holder "Acme")]
   :postings [(bank/posting "P1" [(bank/entry "A" :debit 100 "USD")
                                  (bank/entry "B" :credit  90 "USD")])]
   :clearing-batches [(bank/clearing-batch "C1" [])]
   :payment-initiations [(api/payment-initiation-response "p1" "ACSC")]})
;; => "<html>...read-only · governor-gated...unbalanced...</html>"
```

## Export (CSV / JSON)

Audit-grade CSV (RFC-4180 quoting) and JSON (quote/backslash/newline
escaped) for reconciliation, audit export and downstream reporting —
including the Open Banking payloads, whose plain string keys already
mirror the real JSON field names.

```clojure
(require '[kotoba.banking.export :as ex])

(ex/accounts->csv accounts)                     ; header + rows, commas quoted
(ex/postings->csv postings)                     ; flags unbalanced postings
(ex/accounts->json accounts)                    ; JSON array
(ex/payment-initiations->csv payment-initiations)  ; paymentId, transactionStatus
(ex/account-details->json accounts-details)     ; XS2A accountDetails array
```

## Why

A community bank must never commit a posting whose debits and credits do not
balance, and must identify accounts by a verifiable IBAN before any external
transfer; a payment-initiation request must be built to a real ASPSP's real
JSON shape before an actor ever attempts to plug in real bank credentials.
`kotoba-banking` is the pure-data layer a `PolicyGovernor` checks against;
the actor (`cloud-itonami-6419`, or another actor like
`cloud-itonami-isic-6493`) decides permission, the audit ledger records
proof.

## License

Apache License 2.0.

## Test

```bash
clojure -M:test
```
