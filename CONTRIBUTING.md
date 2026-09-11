# Contributing

`cloud-itonami-unspsc-39` accepts contributions to the OSS actor, governor tests,
documentation, examples and open business blueprint.

## Development

```bash
kbb -M:dev:test
kbb -M:lint
```

Keep changes small and include tests for governor, audit or disclosure
behavior.

## Rules

- Do not commit real resident, municipal or site data.
- Keep production actions behind the Electrical Install Governor.
- Treat solar and EV-charging install and diagnostics as a safety-relevant domain: add tests for permission,
  safety-class gating and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator or certification docs need updates
