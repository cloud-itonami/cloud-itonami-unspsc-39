# cloud-itonami-unspsc-39

Open UNSPSC Blueprint (implemented actor) for **UNSPSC segment 39**:
Electrical Systems and Lighting and Components and Accessories and
Supplies.

This repository publishes a forkable OSS business for an independent solar
and EV-charging install-and-diagnostics contractor: an inspection robot
performs panel/charger fault detection under a governor-gated actor, so an
independent electrician-led practice keeps auditable install and
commissioning records instead of renting a closed field-service SaaS.
Complements
[`cloud-itonami-3512`](https://github.com/cloud-itonami/cloud-itonami-3512)
(Community Renewable Energy Operations) at the install/diagnostics layer.

Built on this workspace's `langgraph-clj` StateGraph runtime -- the
same actor pattern as [`cloud-itonami-isic-3091`](https://github.com/cloud-itonami/cloud-itonami-isic-3091)
(MotoAdvisor ⊣ Motorcycle Plant Operations Governor, whose structure
this actor ports). Here it is **DiagnosticsAdvisor ⊣ Electrical
Install Governor**.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a thermal/electrical-fault
inspection robot performs panel and charger diagnostics under an actor
that proposes an install or repair action and an independent **Electrical
Install Governor** that gates it. The governor never dispatches hardware
itself; `:high`/`:safety-critical` actions (such as work on live circuits
or grid interconnection) require human sign-off.

## Core Contract

```text
site survey + install/repair request
        |
        v
Diagnostics Advisor -> Electrical Install Governor -> install/repair, or human sign-off
        |
        v
robot inspection actions (gated) + commissioning record + audit ledger
```

No automated diagnosis can dispatch a robot action the governor
refuses, suppress a commissioning record, or skip a live-circuit
safety gate without governor approval and audit evidence.

## Implementation

Portable `.cljc` namespaces under `src/elecinstall/`:

- `registry` -- pure domain logic: site/circuit verified+registered
  checks, cumulative committed-load recompute against a circuit's own
  rated capacity, fault-type/voltage/current plausibility validation,
  draft repair-schedule/commissioning-record construction. Circuit-
  actuation block grounded in OSHA 29 CFR 1910.333 (de-energization +
  lockout/tagout) and NFPA 70E -- see docs/adr/0001-architecture.md
  Decision 0 for the verified citation record.
- `store` -- SSoT behind a `Store` protocol (`MemStore`); circuits,
  sites, repairs, commissionings, safety concerns and the audit ledger
  all live here.
- `advisor` -- the contained intelligence node (`mock-advisor` default,
  `llm-advisor` swap-in); returns proposals only.
- `governor` -- the independent Electrical Install Governor (twelve
  concrete checks, four HARD invariants).
- `phase` -- 0->3 staged rollout; repair/install-visit scheduling is
  never auto-committed at any phase.
- `operation` -- the StateGraph (1 run = 1 coordination request);
  `sim` drives the demo.

`kbb -M:test` (77 tests, 211 assertions, 0 failures). See
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for
the full design.

## Capability layer

Resolves via [`kotoba-lang/unspsc`](https://github.com/kotoba-lang/unspsc)
(UNSPSC segment `39`). Required capabilities:

- :robotics
- :telemetry
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
