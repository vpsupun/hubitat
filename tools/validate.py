#!/usr/bin/env python3
"""Repo consistency checks for the vpsupun/hubitat HPM repository.

Run: python tools/validate.py   (from the repo root)

Checks:
  1. Every JSON file parses.
  2. Each packageManifest.json version matches the driver's state.version and
     the driver's entry in resources/version.json.
  3. Manifest driver name/namespace match the Groovy definition() block.
  4. Manifest/repository location URLs and definition() importUrl resolve to
     real files in this repo (raw.githubusercontent.com/vpsupun/hubitat/...).
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW_PREFIX = "https://raw.githubusercontent.com/vpsupun/hubitat/master/"

errors = []


def err(msg):
    errors.append(msg)
    print(f"FAIL  {msg}")


def load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        err(f"{path.relative_to(ROOT)}: invalid JSON ({e})")
        return None


def groovy_file(path):
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        err(f"{path.relative_to(ROOT)} not found")
        return None


def check_raw_url(url, origin):
    """A raw.githubusercontent URL for this repo must map to an existing file."""
    if not url:
        return
    if not url.startswith(RAW_PREFIX):
        err(f"{origin}: URL not on this repo's master branch: {url}")
        return
    rel = url[len(RAW_PREFIX):]
    if not (ROOT / rel).is_file():
        err(f"{origin}: {rel} does not exist")


def main():
    manifests = sorted(ROOT.glob("*/packageManifest.json"))
    repo = load_json(ROOT / "repository.json")
    versions = load_json(ROOT / "resources" / "version.json")
    for m in manifests:
        load_json(m)

    # Check repository.json package locations
    for pkg in (repo or {}).get("packages", []):
        check_raw_url(pkg.get("location"), f"repository.json package '{pkg.get('name')}'")

    version_table = (versions or {}).get("driver", {})

    for manifest_path in manifests:
        pkg_dir = manifest_path.parent
        manifest = load_json(manifest_path)
        if manifest is None:
            continue

        drivers = manifest.get("drivers", [])
        if len(drivers) != 1:
            err(f"{manifest_path.relative_to(ROOT)}: expected exactly 1 driver, found {len(drivers)}")
            continue
        driver = drivers[0]

        groovy_path = pkg_dir / f"{pkg_dir.name}.groovy"
        source = groovy_file(groovy_path)
        if source is None:
            continue

        # state.version must match manifest version
        m_ver = re.search(r'state\.version\s*=\s*"([^"]+)"', source)
        if not m_ver:
            err(f"{groovy_path.name}: no state.version found")
        elif manifest.get("version") != m_ver.group(1):
            err(f"{pkg_dir.name}: manifest {manifest.get('version')} != state.version {m_ver.group(1)}")

        # appName -> version.json entry must match
        m_app = re.search(r'state\.appName\s*=\s*"([^"]+)"', source)
        if m_app:
            entry = version_table.get(m_app.group(1))
            if entry is None:
                err(f"version.json: no entry for {m_app.group(1)}")
            elif entry.get("version") != manifest.get("version"):
                err(f"{pkg_dir.name}: version.json {entry.get('version')} != manifest {manifest.get('version')}")

        # definition name/namespace match manifest
        m_def = re.search(r'definition\(name:\s*"([^"]+)",\s*namespace:\s*"([^"]+)"', source)
        if m_def:
            if m_def.group(1) != driver.get("name"):
                err(f"{pkg_dir.name}: definition name '{m_def.group(1)}' != manifest '{driver.get('name')}'")
            if m_def.group(2) != driver.get("namespace"):
                err(f"{pkg_dir.name}: namespace '{m_def.group(2)}' != manifest '{driver.get('namespace')}'")

        # URLs resolve to real files
        check_raw_url(driver.get("location"), f"{pkg_dir.name} manifest location")
        m_imp = re.search(r'importUrl:\s*"([^"]+)"', source)
        if m_imp:
            check_raw_url(m_imp.group(1), f"{pkg_dir.name} importUrl")

    # Standalone drivers: version.json entries must match state.version
    for app, entry in version_table.items():
        hit = list(ROOT.glob(f"**/{app}.groovy")) or list(ROOT.glob("*.groovy"))
        for g in hit:
            src = g.read_text(encoding="utf-8")
            if f'state.appName = "{app}"' not in src:
                continue
            m_ver = re.search(r'state\.version\s*=\s*"([^"]+)"', src)
            if m_ver and m_ver.group(1) != entry.get("version"):
                err(f"{g.name}: state.version {m_ver.group(1)} != version.json {entry.get('version')} ({app})")
            break

    if errors:
        print(f"\n{len(errors)} problem(s) found")
        sys.exit(1)
    print("All checks passed")


if __name__ == "__main__":
    main()
