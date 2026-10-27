# AGENTS.md — vpsupun/hubitat

Community drivers for Hubitat Elevation, written in Groovy and distributed via
[Hubitat Package Manager (HPM)](https://hubitatpackagemanager.hubitatcommunity.com/).
HPM fetches code straight from `raw.githubusercontent.com/vpsupun/hubitat/master/...`,
so **merging to `master` publishes to users immediately** — there is no staging step.

## Layout

- `*/<Name>.groovy` + `*/packageManifest.json` — one folder per HPM package:
  `EnvoyLocalData`, `IthoNRG`, `NukiWebAPI`, `RitualsPerfumeGenie`
- `BasicAuthHTTPSwitch.groovy`, `EatonXComfortSwitch.groovy`, `EatonXComfortDimmer.groovy` —
  standalone drivers at the root (not packaged in `repository.json`)
- `repository.json` — the HPM repo manifest listing all packages
- `resources/version.json` — legacy in-driver update checker consumed by `updateCheck()`
- `tools/validate.py` — repo consistency checker (run `python tools/validate.py`)

## Hard constraints — read before editing

- This code **only runs inside the Hubitat hub**. It cannot be executed or
  unit-tested locally. Platform calls (`metadata {}`, `preferences`, `input`,
  `sendEvent`, `state`, `settings`, `httpGet`/`httpPost`/`httpPostJson`, `runIn`,
  `schedule`, `unschedule`, `pauseExecution`, `device.updateSetting`) do not exist
  outside the hub — do not try to "run" or mock them.
- Target Groovy ~2.4-era syntax. No external dependencies. Only imports used are
  `groovy.json.*` and `groovy.transform.Field` (`@Field` for shared constants).
- Verification is `python tools/validate.py` + the GitHub Actions workflow
  (`.github/workflows/validate.yml`, includes a `groovyc` syntax check).

## Conventions to follow

- `namespace: "community"`, author "Supun Vidana Pathiranage" in `definition()`.
- Every driver has the same boilerplate: `setVersion()`, `updateCheck()`,
  `updateSchedule()`, `setPolling()`, `logsOff()` (debug logging auto-disables
  after 30 min via `runIn(1800, logsOff)`), and a `logEnable` bool preference.
  Keep new drivers consistent with this shape.
- **Version is stored in three places and must stay in sync**:
  `state.version` inside the driver, `<pkg>/packageManifest.json` → `version` +
  `dateReleased` (YYYY-MM-DD), and `resources/version.json` → `driver.<appName>.version`.
  `state.appName` is the key used in `version.json`.
- `importUrl` in `definition()` must point to `.../hubitat/master/<path-to-file>`
  (drivers that live in a subfolder include the folder in the path).
- `settings.*` holds user preferences; `state.*` holds runtime values.
  Attributes declared in `definition()` are visible on the device page —
  never store secrets in them.

## Security rules (non-negotiable)

- Secrets may only be collected with `input "x", "password"` — never `"text"`.
- **Never log unmasked credentials, tokens, session IDs, or `Authorization`
  headers.** Mask with the `maskSecret()` helper (first 4 chars + length) or a
  `redact*` helper that clones params/body before logging. Debuggability must be
  preserved — mask, don't delete.
- Never store tokens/passwords as device attributes or in `sendEvent` values;
  keep them in `state.*` only.
- `ignoreSSLIssues` in HTTP params is only acceptable where the target device
  uses a self-signed certificate, and must be an explicit user preference
  (see `EnvoyLocalData`'s `ignoreSSL` input).
- Never commit credentials, tokens, IPs of real home devices, or `.env` files.
  `.devin/*.local.json` is for local secrets and is gitignored.
- User-supplied values (`settings.URI`, `settings.ip`, names) may contain quotes
  or extra slashes — sanitize before interpolating into URLs.

## Releasing

Follow `.devin/skills/hubitat-release/SKILL.md`. In short: bump the three
version locations, set `dateReleased`, update `updateInfo`, run
`tools/validate.py`, push to `master` (= publish).
