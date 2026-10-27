# Security Policy

## Supported Versions

Only the latest release of each driver is supported. Updates are distributed
through Hubitat Package Manager (HPM) — run *Match Up* in HPM to stay current.

| Package | Supported |
| ------- | --------- |
| Latest version of every driver in this repo | Yes |
| Older versions | No |

## Reporting a Vulnerability

**Do not open a public issue for security problems.**

Use GitHub's
[private vulnerability reporting](https://github.com/vpsupun/hubitat/security/advisories/new)
instead. If that is unavailable, reach out via the
[Hubitat Community](https://community.hubitat.com/) forums by private message
to `@vpsupun`.

Please include:

- The driver name and version (`state.version` on the device page)
- A description of the issue and potential impact
- Steps to reproduce (mask any real credentials, tokens, or IP addresses)

You can expect an acknowledgement within a few days. Fixes ship through HPM
as a normal version bump.

## Never share secrets

These drivers handle real credentials (lock API tokens, cloud passwords,
device basic-auth). When filing issues or posting logs:

- Remove or mask tokens, passwords, session IDs and `Authorization` headers
- Debug logging may print masked values (e.g. `abcd****(64 chars)`) — that is
  expected and safe to share; full values must never appear

## Notes for contributors

The security conventions enforced in this codebase are documented in
`AGENTS.md` ("Security rules"). The CI workflow and `tools/validate.py` check
manifest/version consistency but are not a substitute for review.
