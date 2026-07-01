# kotoba-banking

[![CI](https://github.com/kotoba-lang/banking/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/banking/actions/workflows/ci.yml)

**Banking accounts, double-entry ledger and clearing in pure Clojure.** A
[kotoba-lang](https://github.com/kotoba-lang) capability library that gives
the [`cloud-itonami-6419`](https://github.com/gftdcojp/cloud-itonami-6419)
community monetary-intermediation open business the records a banking
operator keeps: IBAN (ISO 13616) identification with mod-97 checksum
validation, account records, a double-entry ledger with balanced-posting
validation, and a clearing-batch contract for settlement.

No network, no I/O. Amounts are plain numbers in the smallest unit of the
account currency (e.g. cents) — no BigDecimal assumption, keeping the
library portable `.cljc` across JVM / ClojureScript / SCI / GraalVM.


## Maturity

| | |
|---|---|
| Role | capability |
| Tests | 53 assertions, all green |
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

## Operator console (UI/UX)

A read-only HTML dashboard renders accounts, postings and clearing batches
for an operator. Built on [`kotoba-lang/html`](https://github.com/kotoba-lang/html)
(Hiccup→HTML) + [`kotoba-lang/css`](https://github.com/kotoba-lang/css)
(EDN→CSS). Pure data → markup; the console never exposes a write surface
(no `<form>`/`<button>`) — writes stay behind the governor.

```clojure
(require '[kotoba.banking.ui :as ui])

(ui/dashboard
  {:accounts [(bank/account "A1" "USD" :holder "Acme")]
   :postings [(bank/posting "P1" [(bank/entry "A" :debit 100 "USD")
                                  (bank/entry "B" :credit  90 "USD")])]
   :clearing-batches [(bank/clearing-batch "C1" [])]})
;; => "<html>...read-only · governor-gated...unbalanced...</html>"
```

## Export (CSV / JSON)

Audit-grade CSV (RFC-4180 quoting) and JSON (quote/backslash/newline
escaped) for reconciliation, audit export and downstream reporting.

```clojure
(require '[kotoba.banking.export :as ex])

(ex/accounts->csv accounts)    ; header + rows, commas quoted
(ex/postings->csv postings)    ; flags unbalanced postings
(ex/accounts->json accounts)   ; JSON array
```

## Why

A community bank must never commit a posting whose debits and credits do not
balance, and must identify accounts by a verifiable IBAN before any external
transfer. `kotoba-banking` is the pure-data layer a `PolicyGovernor` checks
against; the actor (`cloud-itonami-6419`) decides permission, the audit
ledger records proof.

## License

Apache License 2.0.

## Test

```bash
clojure -M:test
```
