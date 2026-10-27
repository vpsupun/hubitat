---
name: hubitat-release
description: Release a new version of a Hubitat driver in this repo — bump the three version locations (state.version, packageManifest.json, resources/version.json), validate, and publish via push to master (HPM reads raw master immediately).
---

# Hubitat release checklist

Use this whenever changing driver code or cutting a release. A push to `master`
**is** the release: HPM fetches `repository.json` → `packageManifest.json` →
`.groovy` straight from `raw.githubusercontent.com/.../master/`.

## The three version locations (must all match)

For driver `Foo/Foo.groovy` with `state.appName = "Foo"`:

1. `Foo/Foo.groovy` → `setVersion()` → `state.version`
2. `Foo/packageManifest.json` → `"version"` and `"dateReleased"` (`YYYY-MM-DD`)
3. `resources/version.json` → `driver.<appName>.version` + `updateInfo`
   (HTML `<br>`-separated changelog lines shown by the in-driver update checker)

Root-level drivers (`BasicAuthHTTPSwitch`, `EatonXComfortSwitch`,
`EatonXComfortDimmer`) are NOT HPM packages — only `state.version` +
`version.json` apply (and Eaton is the only one still wired into `version.json`).

## Steps

1. Make the code change in the `.groovy` file.
2. Bump `state.version` (patch increment, `x.y.z`).
3. Bump `packageManifest.json` `version` + set `dateReleased` to today.
4. Update `resources/version.json` entry + `updateInfo`.
5. Check `importUrl` still points at `.../hubitat/master/<correct-path>`
   (subfolder drivers include the folder).
6. Run `python tools/validate.py` — must pass before pushing.
7. Commit, push to `master`. Users get the update on their next HPM match-up /
   in-driver `updateCheck()` poll.

## Safety rules when editing

- Never weaken the masking rules in `AGENTS.md` — secrets are masked in logs,
  not deleted (debugging must keep working).
- Secrets only via `input ..., "password"`. Never into attributes/`sendEvent`.
- `ignoreSSLIssues` only behind an explicit user preference.
