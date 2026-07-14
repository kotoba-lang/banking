# ADR 0001: kotoba.banking.api — a real Open Banking (PSD2/XS2A) request/response layer

- **Status**: Accepted — landed (2026-07-14), tests green
- **Date**: 2026-07-14
- **Deciders**: Jun Kawasaki (owner directive: implement code "genuinely
  identical to the real specification — not a simplified placeholder", so a
  licensed operator could later plug this into a real bank's actual API
  with minimal translation work)
- **Context tags**: banking, open-banking, psd2, xs2a, berlin-group, api,
  cljc
- **Related**: `90-docs/adr/2607141900-kotoba-banking-open-banking-xs2a-api-layer.md`
  (superproject, this ADR's counterpart), `kotoba-lang/swift` (the sibling
  interbank-*messaging* library this repo does NOT duplicate),
  `kotoba-lang/kessai` (the payment-gateway abstraction that already
  bridges `kotoba-banking`'s IBAN + a wire rail, precedent for this
  namespace's construct/validate-via-`ex-info` style)

## Problem

`kotoba-banking` (`src/kotoba/banking.cljc`) modeled internal bookkeeping —
IBAN validation, account records, a double-entry ledger, a clearing-batch
contract — but had no external-facing API/message layer. The
`cloud-itonami-isic-6493` factoring actor (and any future actor needing
real banking capability) needs a REST/JSON payment-initiation and
account-information shape it can eventually wire a licensed bank
connection into. No live bank connection is in scope here (that requires a
licensed financial institution's real credentials, which this codebase
must not fabricate) — but the request/response SHAPES must be real, not
invented, so the translation cost when a real connection is finally added
is minimal.

## Research

Primary target: the **Berlin Group NextGenPSD2 XS2A Framework**,
Implementation Guidelines + OpenAPI definition **version 1.3.11
(2021-09-24)**. Chosen because (a) it is a real, published, widely-adopted
standard — Berlin Group reports adoption by >75% of European banks and
implementation in all EU countries — and (b) this fleet already anchors to
it indirectly: `cloud-itonami-isic-6419`'s `banking.facts` catalog cites
BaFin (Germany's supervisory authority) as the DEU jurisdiction's
owner-authority, and Berlin Group XS2A is Germany/EU's Open Banking API
standard.

Sources actually fetched and read (not recalled from training data):

1. The Berlin Group's own machine-readable OpenAPI 3.0 schema, as mirrored
   verbatim in [`adorsys/xs2a`](https://github.com/adorsys/xs2a) (the
   Berlin-Group-affiliated open-source reference XS2A implementation):
   `https://github.com/adorsys/xs2a/blob/develop/xs2a-impl/src/main/resources/static/psd2-api_v1.3.11-2021-10-01v1.yaml`
   (14,069 lines, fetched and read in full). Source of: the
   `transactionStatus` 14-value enum with descriptions, the
   `paymentInitiation_json` request schema (required `debtorAccount`/
   `instructedAmount`/`creditorAccount`/`creditorName`, optional
   `endToEndIdentification`/`remittanceInformationUnstructured`/
   `requestedExecutionDate`/...), the `paymentInitationRequestResponse-201`
   response schema (`transactionStatus`/`paymentId`/`_links`), the
   `accountReference`/`accountDetails`/`accountList`/`balance`/
   `readAccountBalanceResponse-200` schemas, the `amountValue` field's real
   type (a decimal STRING with pattern
   `-?[0-9]{1,14}(\.[0-9]{1,3})?`, not a JSON number — an easy detail to
   get wrong from memory), the `cashAccountType` ISO 20022
   ExternalCashAccountType1Code list, and the `Error400_NG_PIS`-shaped
   `tppMessages` error body (`category`/`code`/`path`/`text`).
2. A live ASPSP's generated API reference,
   `https://docs-nextgenpsd2.api.memo.bank/operation/operation-initiatepayment`
   (Memo Bank, France) — cross-checked the request/response field list
   against source (1) (matched exactly) and supplied the `tppMessages`
   code-catalog enumeration (`FORMAT_ERROR`, `PSU_CREDENTIALS_INVALID`,
   `RESOURCE_UNKNOWN`, etc.) that the OpenAPI file itself leaves as a free
   string (the concrete catalog lives in the Implementation Guidelines'
   prose tables, not the machine-readable schema).
3. Corroborating secondary sources for the `tppMessages` code catalog
   (Salt Edge, Landsbanki, IBM Open Banking Sandbox docs — all
   independently generated from the same Berlin Group spec, all agreeing
   on `FORMAT_ERROR`/`PSU_CREDENTIALS_INVALID`/`RESOURCE_UNKNOWN`'s names
   and meaning).
4. Secondary-jurisdiction citations (documented only, NOT implemented —
   see "What's NOT covered" below): UK Open Banking (OBIE) Payment
   Initiation API domestic-payments docs
   (`openbankinguk.github.io/read-write-api-site3`) for the PascalCase
   `InstructedAmount`/`DebtorAccount`/`CreditorAccount` shape; Japan's
   全銀協 (Japanese Bankers Association) 銀行分野のオープンAPIに係る
   電文仕様標準 (`zenginkyo.or.jp`) for the fund-transfer/振込 category
   and the honest note that update-type (vs. inquiry-type) bank Open APIs
   remain rare in production there.

## Decision

Add `src/kotoba/banking/api.cljc` (`kotoba.banking.api`), a pure `.cljc`
namespace (no network, no I/O — same discipline as `kotoba.banking`
itself) that:

1. Constructs and parses the Berlin Group XS2A payment-initiation request
   body (`POST /v1/payments/{payment-product}`) and 201 response body,
   with real field names and the real 14-value `transactionStatus` enum.
2. Constructs and parses XS2A account-information payloads
   (`GET /v1/accounts`, `GET /v1/accounts/{account-id}/balances`).
3. Constructs the `tppMessages`-shaped 4xx error body.
4. Bridges to/from `kotoba.banking`'s EXISTING `account` record and
   `iban-valid?`/`parse-iban` — an account whose id is a valid IBAN maps to
   the schema's `iban` form, a non-IBAN account id maps to the schema's own
   `other`/`identification` proprietary-ID fallback (both are real options
   the schema documents, not an invented escape hatch). Amount conversion
   between `kotoba.banking`'s smallest-unit-integer convention and XS2A's
   decimal-string `amountValue` assumes 2 minor units (documented
   limitation — no ISO 4217 minor-unit table exists in this fleet yet, so
   JPY/BHD/KWD/OMR are not correctly handled).
5. Rejects malformed input via `ex-info` + `:api/errors` (the same pattern
   `kotoba-lang/kessai`'s `wire.cljc` already established for bridging
   `kotoba-banking` IBANs into a real message shape).

Updated `kotoba.banking.ui` (operator console — new read-only "Payment
initiations" and "Account information" sections) and
`kotoba.banking.export` (new `payment-initiations->csv/json`,
`account-details->csv/json`) to surface the new payloads, matching this
repo's existing UI/export conventions.

## What's covered

Single payment-initiation request/response for the four JSON-bodied
payment products (`sepa-credit-transfers`, `instant-sepa-credit-transfers`,
`target-2-payments`, `cross-border-credit-transfers`); the real
`transactionStatus` enum; the `tppMessages` 4xx error shape; account list
and balance retrieval.

## What's explicitly NOT covered

- Consent management (`/v1/consents`) — a real XS2A integration cannot
  function without this; it is out of scope for this pass.
- Any SCA (strong customer authentication) flow: redirect, OAuth2,
  decoupled, or embedded approaches; `scaMethods`/`challengeData`/
  authorisation sub-resources.
- Bulk payments, periodic (standing-order) payments, payment cancellation.
- The transaction list (`GET /v1/accounts/{id}/transactions`), card
  accounts.
- The `pain.001`-XML-bodied payment product variants (only the four
  JSON-bodied products are modeled).
- UK OBIE and Japan's 全銀協 standard — cited and documented for the
  fleet's honest multi-jurisdiction-coverage discipline, but NOT
  spec-conformantly implemented. A future ADR should scope that work
  separately if/when needed.
- Any actual HTTP client / network call to a real bank. This remains a
  pure request/response SHAPE library, same as `kessai`'s wire rail.

## Consequences

- `cloud-itonami-isic-6493` (and any other actor) can construct/parse
  real-shaped Open Banking JSON payloads today, purely in-memory, with the
  confidence that the shapes match a real, current (v1.3.11) Berlin Group
  ASPSP — reducing the eventual integration cost when a licensed bank
  connection is added, without this codebase ever fabricating one.
- The `other`/proprietary-ID accountReference fallback means
  `kotoba-banking`'s existing non-IBAN test accounts (e.g. `"A1"`) still
  round-trip through the API layer without forcing every caller onto IBAN
  identifiers.
- Tests: 143 assertions total (up from 53), all green;
  `clj-kondo` lint introduces zero new warnings (the 4 pre-existing
  warnings in `export.cljc`/`ui.cljc` predate this change and are
  unrelated to it).
