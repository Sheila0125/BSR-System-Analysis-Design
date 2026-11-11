# Invoice and Simulated Payment Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans, task by task. BSR is single-agent serial; review is explicitly self-review. Existing uncommitted work stays in place and is snapshotted before edits; do not commit user changes.

**Goal:** Deliver the approved customer invoice → simulated payment → staff issue demonstration with persistent, auditable results.

**Architecture:** Add a read-only invoice projection and customer simulation operation within rental. Extend the existing transaction table with provenance, reuse contract locks and settlement rules, and expose the same invoice to both clients.

**Tech Stack:** Existing Java 24/Spring Boot/MySQL, Vue 3/TypeScript, native WeChat; no dependency changes.

**Spec:** [电子发票与虚拟付款](spec.md)

**执行状态：** 后端、两端页面与本地验证见 [tasks.md](tasks.md)。下列已完成步骤依据 2026-10-27 的执行记录回填。微信审核与完整真机验收继续单列。

## Global Constraints

- Existing fee calculations, currencies, equipment issue/refund rules and eight modules remain authoritative.
- Server controls amounts, identity and timestamps; simulated records are explicitly distinguishable.
- No real payment API or new fee; no arbitrary changes to user edits, Word, or submission folder.
- Owner sequence: T02 migration/schema; T03 customer simulation permission; T04 minimal public billing identity lookup; T05 rental API/UI; T01-WX page registration; T10 checks. One agent serially assumes each owner.

## Review Focus

- Retry after uncertain network response must reuse its request key and never pay twice.
- Concurrent different keys must settle a charge at most once, using real MySQL connections.
- A refund is not a payable charge; failed/partial transactions must not corrupt invoice balances.
- Access loss or contract switching must clear old sensitive invoice details.
- Existing external records retain their original employee, semantics, and idempotency.

## Task 1: Backend invoice and durable simulation

Files: V008 migration; PaymentTransactionRow, TransactionPersistence/JpaTransactionPersistence, TransactionService/Controller; new InvoiceService/Controller; schema contracts/tests and RentalCreationIT.

Interfaces: GET /api/contracts/{id}/invoice returns decimal strings and charges with outstanding amounts and transaction provenance. POST /api/rental-charges/{id}/simulated-payments accepts idempotencyKey (UUID), outcome (SUCCEEDED/FAILED), and returns the recorded transaction. Existing transactions endpoint gains provenance fields.

- [x] Write HTTP regression cases using existing fixture and routes; run them RED (missing endpoints).
- [x] Implement provenance migration with historical EXTERNAL default and mutually exclusive recorder constraints; update schema contracts/tests.
- [x] Implement invoice projection, server-calculated simulation, locking, replay checks and audit in rental.
- [x] Run targeted real MySQL tests GREEN, including failed/success/replay, changed request, unpaid remainder, refunds, access/CSRF, two-connection races and rollback; run backend full unit/IT.

## Task 2: Customer and employee invoice UI

Files: mini rental API, new invoice page and tests, detail/confirm links, app registration; Web rental API, invoice component and tests, RentalDeskView integration.

- [x] Write behavior tests RED for the invoice and simulated-payment flows.
- [x] Add independent invoice page and shared backend projection consumers; display simulation provenance, successful/failed outcomes and live balances.
- [x] Keep uncertain-result keys for retry; refresh invoice after definitive outcomes; prevent duplicate local submissions.
- [x] Run both complete client unit/type/lint/build checks and inspect real browser mobile/desktop layouts.

## Task 3: Integration and delivery

- [x] Run architecture, migration layout and changed-file diff checks; single-agent self-review against spec and existing behavior.
- [x] Prepare and deploy the course demonstration build with backup and migration preservation checks.
- [x] Verify HTTPS invoice and simulated-payment boundaries with test records, and compile the ten mini-program pages.
- [x] Save execution evidence and recovery state for the course demonstration.
- [ ] Complete WeChat review and full end-to-end device acceptance for the submitted build.
