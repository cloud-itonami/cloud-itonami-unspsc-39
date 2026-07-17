# Operator Quickstart — Independent Solar & EV-Charging Install & Diagnostics

Shortest path for **UNSPSC segment 39** (`cloud-itonami-unspsc-39`).

## Prerequisites

- Git
- Optional: static file server for `docs/`

## 1. Clone

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-unspsc-39.git
cd cloud-itonami-unspsc-39
```

## 2. Open the product face

```bash
open docs/index.html
# or: python3 -m http.server -d docs 8080
```

Publish: GitHub Pages on `main` `/docs`.

## 3. Governor

- Blueprint governor key: `electrical-install-governor`
- Domain: `energy/solar-ev-electrical`

## 4. Claim / go-live

- Free claim: https://itonami.cloud/isco-1212/
- Paid path: https://itonami.cloud/docs/go-live.md
- Blueprint: `blueprint.edn`

## Constraints

- No invented users/revenue
- No secrets in repo
