# cloud-itonami-unspsc-39

Open UNSPSC Blueprint for **UNSPSC segment 39**: Electrical Systems and
Lighting and Components and Accessories and Supplies.

This repository designs a forkable OSS business for an independent solar
and EV-charging install-and-diagnostics contractor: an inspection robot
performs panel/charger fault detection under a governor-gated actor, so an
independent electrician-led practice keeps auditable install and
commissioning records instead of renting a closed field-service SaaS.
Complements
[`cloud-itonami-3512`](https://github.com/cloud-itonami/cloud-itonami-3512)
(Community Renewable Energy Operations) at the install/diagnostics layer.

**Status: design blueprint, no code implemented yet.** This repository
has zero files under `src/` and no `test/` directory — the
Diagnostics Advisor and Electrical Install Governor described below do
not exist in code. It is not (yet) a governed Advisor⊣Governor
actuation actor; the Core Contract section specifies what that
pipeline is intended to enforce once built, not current behavior. See
[`cloud-itonami-isco-1324`](https://github.com/cloud-itonami/cloud-itonami-isco-1324)
for this fleet's minimal implemented reference (`actor`/`advisor`/
`governor`/`store`), and the `cloud-itonami-assoc-*` /
`cloud-itonami-municipality-*` / `cloud-itonami-lei-*` repos for this
fleet's honest not-an-actuation-actor disclaimer pattern.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a thermal/electrical-fault
inspection robot performs panel and charger diagnostics under an actor
that proposes an install or repair action and an independent **Electrical
Install Governor** that gates it. The governor never dispatches hardware
itself; `:high`/`:safety-critical` actions (such as work on live circuits
or grid interconnection) require human sign-off.

## Core Contract (design intent — not yet implemented)

```text
site survey + install/repair request
        |
        v
Diagnostics Advisor -> Electrical Install Governor -> install/repair, or human sign-off
        |
        v
robot inspection actions (gated) + commissioning record + audit ledger
```

**No code exists yet in this repo** — no `src/`, no `test/`, only this
design document plus `blueprint.edn` and `docs/`. Once built, no
automated diagnosis will be able to dispatch a robot action the
governor refuses, suppress a commissioning record, or skip a
live-circuit safety gate without governor approval and audit evidence
— but none of that is enforced today.

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
