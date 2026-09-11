# ADR-0001: DiagnosticsAdvisor ⊣ Electrical Install Governor architecture

## Status

Accepted. `cloud-itonami-unspsc-39` promoted from blueprint to
`:implemented`, following the verified fresh-scaffold protocol
established by prior actors in this fleet.

## Context

`cloud-itonami-unspsc-39` publishes an OSS blueprint for an
independent solar and EV-charging install-and-diagnostics contractor:
panel/charger diagnostic-reading data logging, repair/install-visit
scheduling against customer sites, live-circuit/grid-interconnection
safety-concern flagging, and commissioning-record coordination against
an electrical circuit. Like every actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the same
langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established across the cloud-itonami fleet.

The closest domain analog is `cloud-itonami-isic-3091` (Manufacture of
motorcycles, `motomfg`): both are back-office coordination actors with
a real physical safety dimension, both share the same four-op shape
(`:log-X`/`:schedule-Y`/`:flag-safety-concern`/`:coordinate-Z`), and
both share the two-entity verified/registered gate structure (one
entity for the scheduling op, a second for the coordination op). This
build mirrors `motomfg`'s architecture closely but adapts the hazard
profile and vocabulary to electrical install/diagnostics work: this
vertical's `sites` entity (an installation site a repair/install visit
is scheduled against) plays `motomfg.equipment`'s structural role, and
`circuits` (the panel/circuit record a commissioning record is issued
against, with a cumulative `:committed-load-amps` recomputed against a
registered `:rated-capacity-amps` ceiling) plays `motomfg.batches`'s
role.

This vertical has NO pre-existing `kotoba-lang/elecinstall`-style
capability library to wrap (verified: no such repo exists). This build
therefore uses self-contained domain logic -- pure functions in
`elecinstall.registry` (site/circuit verification, cumulative
committed-load recompute, fault-type validation, voltage/current
plausibility validation) are re-verified independently by the
governor, the same "ground truth, not self-report" discipline
established across prior actors (most directly `motomfg.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:electrical-install-governor`, was grep-verified UNIQUE fleet-wide
(`grep -rl electrical-install-governor orgs/cloud-itonami`, zero other
hits before this repo's implementation) before writing any code here.

### Decision 0: Real-standard grounding for the circuit-actuation block (verified, not fabricated)

Unlike a domain with no available real citation, electrical work
safety practice is governed by well-documented, real, verifiable
standards. This build cites exactly two, both web-verified 2026-07-19
against their own authoritative sources before being referenced in
code or docs:

- **OSHA 29 CFR 1910.333** ("Selection and use of work practices") --
  live parts must be de-energized before an employee works on or near
  them (unless de-energizing is infeasible), and while de-energized,
  the circuit must be locked out and/or tagged
  (`1910.333(a)(1)`/`1910.333(b)`). Verified against osha.gov's own
  published regulation text.
- **NFPA 70E** ("Standard for Electrical Safety in the Workplace") --
  the industry-standard companion to NFPA 70 (the National Electrical
  Code) covering safe work practices, arc-flash/shock risk assessment,
  and PPE requirements for work on or near energized equipment.
  Verified against nfpa.org's own product page.

These ground `elecinstall.governor/circuit-actuate-blocked-violations`
honestly: this actor is never the one that determines a circuit is
safely de-energized or performs lockout/tagout -- that remains the
qualified electrician's physical act, outside this actor's authority
in every phase, unconditionally. No fabricated citation (no invented
statute number, no invented agency, no invented certification body) is
used anywhere in this build; where a bound is physical-plausibility
only (voltage/current sensor ranges), it is documented as such, not
dressed up with an invented standard.

## Decision

### Decision 1: Self-contained domain logic (no external field-service capability library to wrap)

The site/circuit-verification / cumulative-committed-load / fault-type
/ voltage / current validation functions live as pure functions in
`elecinstall.registry` and are re-verified independently by
`elecinstall.governor` -- the same "ground truth, not self-report"
discipline established across prior actors (most directly
`motomfg.registry`).

### Decision 2: Coordination, not control -- scope boundary at the back-office

This actor is **strictly back-office coordination** of install and
diagnostics operations. It does NOT:
- Energize, de-energize, or otherwise actuate a circuit/breaker directly
- Make electrical-safety or interconnection-approval decisions (exclusive to the human qualified electrician / AHJ inspector / utility)
- Actuate any circuit
- Self-issue a commissioning/interconnection-approval certification mark

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human qualified-
electrician approval. This is not a replacement for the electrician's
authority or the AHJ/utility's authority -- it is a proposal-screening
and documentation layer.

**CRITICAL SAFETY BOUNDARY**: electrical install work is a
safety-critical domain (live-circuit shock/arc-flash hazard, grid-
interconnection hazard, thermal-runaway risk on battery/inverter
equipment). Safety-concern flagging NEVER auto-commits. All safety
concerns escalate immediately to human review.

### Decision 3: Safety-concern escalation -- always human sign-off

`:flag-safety-concern` (live-circuit hazard, grid-interconnection
hazard, thermal-runaway risk) ALWAYS escalates, never auto-commits.
This is not a "low-stakes proposal" -- it is a circuit-breaker (pun
intentional, matching this fleet's terse house style) that must reach
human authority.

### Decision 4: Two independent verified/registered gates (site AND circuit), not one

Like `motomfg`, this vertical has TWO entity kinds each gating a
different op: `:schedule-repair` independently verifies the referenced
**site**'s own `:verified?`/`:registered?` fields;
`:issue-commissioning-record` independently verifies the referenced
**circuit**'s own `:verified?`/`:registered?` fields. Both are the
same "site/circuit record must be independently verified/registered
before any action" HARD invariant applied to the two distinct record
kinds this domain actually has. `:issue-commissioning-record`
additionally independently recomputes whether a circuit's own recorded
cumulative committed load plus the proposal's own claimed load would
exceed the circuit's own recorded rated capacity -- never taken on the
advisor's self-report.

### Decision 5: HARD invariants (no override)

Four HARD governor invariants (elaborated into twelve concrete checks
in `elecinstall.governor`, mirroring `motomfg.governor`'s own
elaboration of its HARD invariants into concrete checks) block
proposals and cannot be overridden by human approval:
1. Site/circuit record (site for repair scheduling, circuit for commissioning coordination) must be independently verified/registered before any action is taken against it, and a commissioning's load must independently recompute within the circuit's own logged rated capacity
2. Proposals must be `:effect :propose` only (never direct circuit control)
3. Direct circuit actuation (energize/de-energize, bypass lockout/tagout), or self-issued commissioning/interconnection-approval certification, is permanently blocked
4. The op allowlist is closed -- `:log-diagnostic-reading`/`:schedule-repair`/`:flag-safety-concern`/`:issue-commissioning-record` only

## Consequences

(+) Solar/EV-charging install-and-diagnostics operations back-office
now has a documented, governed, auditable coordination layer that
funnels all decisions through independent validation before human
approval.

(+) The "coordination, not control" boundary is explicit in code: all
`:effect :propose`, all real-world circuit actuation requires human
qualified-electrician sign-off (grounded in OSHA 29 CFR 1910.333 and
NFPA 70E), and no commissioning/interconnection-approval certification
can ever be self-issued.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into twelve concrete governor checks) protect against scope creep into
unauthorized circuit actuation or certification self-issuance. Safety
concerns are a circuit-breaker, not a threshold.

(+) Safety-critical discipline is explicit: safety-concern flagging
cannot be rate-limited, suppressed, or auto-decided by phase gate.
Human review is mandatory.

(-) Still a simulation/proposal layer, not a real field-service
control system. Circuit actuation, electrical-safety decisions, and
interconnection-approval issuance remain human-/institution-controlled
via external channels.

(-) No integration with real field-service databases (panel/charger
telemetry, site scheduling, AHJ/utility interconnection-approval APIs)
-- this is a standalone coordinator blueprint.

## Verification

- `cloud-itonami-unspsc-39`: `kbb -M:test` green (77 tests / 211
  assertions, 0 failures, 0 errors, verified from a fresh worktree
  checkout), demo narrative (`kbb -M:dev:run`) exercises proposal
  submission, escalation, and every HARD-hold scenario directly
  (not-propose-effect, unknown-op, site-not-verified, circuit-not-
  verified, load-exceeds-rated-capacity, circuit-actuate-blocked,
  certification-authority-blocked, already-scheduled, invalid-fault-
  type, invalid-voltage, invalid-current), exit code 0.
- All source is `.cljc` (portable ClojureScript / JVM / nbb) -- no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `kbb -M:test` resolves offline inside the monorepo checkout.
- OSHA 29 CFR 1910.333 and NFPA 70E were web-verified 2026-07-19
  against osha.gov / nfpa.org before being cited in
  `elecinstall.registry` and `elecinstall.governor` docstrings -- no
  fabricated citation.
