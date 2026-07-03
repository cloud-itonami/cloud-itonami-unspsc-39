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

No automated diagnosis can dispatch a robot action the governor refuses,
suppress a commissioning record, or skip a live-circuit safety gate
without governor approval and audit evidence.

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
