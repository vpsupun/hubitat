# Tiny Hubitat Drivers and Apps

Community drivers for [Hubitat Elevation](https://hubitat.com/), written in
Groovy. Distributed via
[Hubitat Package Manager (HPM)](https://hubitatpackagemanager.hubitatcommunity.com/).

## Install via HPM

1. In HPM, choose **Install → Search by Keywords** or **Install from a URL** and
   point it at this repository manifest:

   ```
   https://raw.githubusercontent.com/vpsupun/hubitat/master/repository.json
   ```

2. Pick a package, then configure the device preferences listed below.

## Packages

| Package | Driver | What it does |
| ------- | ------ | ------------ |
| Nuki Web API | `NukiWebAPI/NukiWebAPI.groovy` | Control Nuki smart locks via the Nuki Web API (no bridge needed) — lock, unlock, unlatch, lock'n'go, battery status |
| Rituals Perfume Genie | `RitualsPerfumeGenie/RitualsPerfumeGenie.groovy` | On/off, intensity level (1–3), Wi-Fi strength, cartridge fill |
| Envoy Local Data | `EnvoyLocalData/EnvoyLocalData.groovy` | Local production & consumption data from Enphase Envoy-S (metered, token-auth firmware) |
| Itho NRG | `IthoNRG/IthoNRG.groovy` | Itho NRG Wi-Fi fan controller — speed get/set |

## Standalone drivers (not in HPM)

| Driver | What it does |
| ------ | ------------ |
| `EatonXComfortSwitch.groovy` | Eaton xComfort switch actuators via the xComfort JSON-RPC interface |
| `EatonXComfortDimmer.groovy` | Same, with `SwitchLevel` dimming |
| `BasicAuthHTTPSwitch.groovy` | Generic HTTP/HTTPS switch with optional Basic auth — POST a configurable on/off body to any endpoint |

## Configuration

**Nuki Web API** — Nuki API token (Web API enabled in the Nuki app), lock name
(case sensitive), polling interval.

**Rituals Perfume Genie** — Rituals account email + password, genie name
(case sensitive), polling interval.

**Envoy Local Data** — Envoy local IP, Enlighten email + password, Envoy serial
number, polling interval. *Ignore SSL errors* defaults on (required for the
Envoy's self-signed certificate); turn it off only if your Envoy serves a valid
certificate.

**Itho NRG** — Controller IP, polling interval.

**Eaton XComfort** — Controller URI, username, password, optional zone & device
names (leave empty and enable debug logging to discover them from the logs),
polling interval. The xComfort hub speaks plain HTTP — keep it on a trusted LAN.

**Basic Auth HTTP/HTTPS Switch** — URI, path, username + password (optional),
JSON bodies for the ON and OFF actions.

## Security notes

- Passwords/tokens are entered via masked preference fields and never written
  to device attributes.
- Debug logging auto-disables after 30 minutes and masks secrets
  (e.g. `abcd****(64 chars)`) rather than printing them.
- If you ever posted a log containing an unmasked token or password anywhere,
  rotate that credential.
- See `SECURITY.md` for vulnerability reporting and `AGENTS.md` for contributor
  conventions.

## License

[Apache-2.0](LICENSE)
