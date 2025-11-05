/*
 * Rituals Perfume Genie driver
 *
 * Hubitat connecting to the Rituals Perfume Genie to on, off and get details
 *
 *
 * Tested : Rituals Perfume Genie 3.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at:
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 */
import groovy.transform.Field
import groovy.json.JsonSlurper
void setVersion(){
    state.version = "0.0.2"
    state.appName = "RitualsPerfumeGenie"
}

metadata {
    definition(name: "Rituals Perfume Genie", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat/master/RitualsPerfumeGenie.groovy") {
        capability "SignalStrength"
        capability "Switch"
        capability "Refresh"
        capability "Polling"

        attribute "lastToken", "string"
        attribute "genie_id", "string"
        attribute "speed", "number"
        attribute "wifi_percentage", "number"
        attribute "cartridge_fill", "string"

        command "on"
        command "off"
        command "refreshToken"
        command "refreshGenieName"
        command "setPerfumeIntensityLevel", ["NUMBER"]
    }
}

preferences {
    section("URI Data") {
        input "email", "text", title: "Login Email", required: true
        input "pass", "password", title: "Login password", required: true
        input "genie_name", "text", title: "Genie Name (case sensitive)", required: true
        input "polling", "text", title: "Polling Interval (mins)", required: true, defaultValue: "15", range: 2..59
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

void logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
    cleanup()
    setToken()
    setGenie()
    updateSchedule()
    setPolling()
}

void on() {
    genieAction("on")
}

void off() {
    genieAction("off")
}

void refresh() {
    setStatus()
}

void refreshToken() {
    setToken()
}

void refreshGenieName() {
    setGenie()
}

String generateToken() {
    String token
    String login_url = "https://rituals.apiv2.sense-company.com/apiv2/account/token"

    if (logEnable) log.debug "Generating a token"
    Map<String> data = [
            "email"   : settings.email,
            "password": settings.pass
    ]
    Map<String, Object> httpParams = [
            "uri" : login_url,
            "body": data
    ]
    try {
        httpPost(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                if (resp.data?.success != "false") {
                    token = resp.data?.success
                } else {
                    log.warn "Incorrect email or password"
                }
            }
        }
    } catch (Exception e) {
        log.warn "HTTP post failed: ${e.message}"
    }
    if (logEnable) log.debug "Token : ${token}"
    return token
}

String setToken() {
    if (logEnable) log.debug "Current token seems expired. Generating a new one."
    String token = generateToken()
    if (token != null) {
        sendEvent(name: "lastToken", value: token, displayed: false)
        state.lastToken = token
    } else {
        log.warn "Generated token is null. Investigate with debug logs"
    }
}

List getGenieData(String token) {
    List hubs
    String hub_url = "https://rituals.apiv2.sense-company.com/apiv2/account/hubs"
    if (logEnable) log.debug "Fetching hubs"
    Map<String, Object> httpParams = [
            "uri"               : hub_url,
            "contentType"       : "application/json",
            "headers"           : ["Accept" : "*/*", "Authorization" : token ],
    ]
    try {
        httpGet(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "HTTP response: ${resp.data}"
            }
            if (resp.success) {
                hubs = resp.data
            }
        }
    } catch (Exception e) {
        log.warn "HTTP get failed: ${e.message}"
    }
    return hubs
}

String setGenie() {
    String token = state.lastToken
    String genie_id = state.genie_id
    String genie_name = settings.genie_name
    if (token == null) {
        log.warn "The token is not set. Trying to set the token automatically"
        setToken()
        token = state.lastToken
    }
    List hubs = getGenieData(token)
    if (hubs instanceof List && hubs.size() >= 1) {
        def targetDevice = hubs.find { device ->
            try { return device?.attributeValues?.roomnamec == genie_name } catch (ignored) { return false }
        }
        if (targetDevice) {
            // Use the hash identifier for attribute endpoints (fallback to hublot)
            genie_id = targetDevice.hash ?: targetDevice.hublot
            sendEvent(name: "genie_id", value: genie_id, displayed: false)
            state.genie_id = genie_id
            log.info "Successfully found device : ${genie_name}"
            log.info "Using hub identifier: ${genie_id}"
        } else {
            log.info "Error: Could not find any device entry for room: ${genie_name}"
        }
    }
}

void genieAction(String action, boolean retried = false) {
    log.debug "Initiating : ${action}"
    String hub_hash = state.genie_id
    String token = state.lastToken
    if (!hub_hash) { log.warn "Genie ID not set. Use refreshGenieName"; return }
    if (!token) { log.warn "Token not set. Use refreshToken"; return }

    String fancValue = (action == "on") ? "1" : (action == "off" ? "0" : null)
    if (fancValue == null) { log.warn "Unsupported action: ${action}"; return }

    String url = "https://rituals.apiv2.sense-company.com/apiv2/hubs/${hub_hash}/attributes/fanc"
    Map<String, Object> httpParams = [
            "uri"               : url,
            "contentType"       : "application/json",
            "requestContentType": "application/x-www-form-urlencoded",
            "headers"           : ["Accept": "*/*", "Authorization": token],
            "body"              : ["fanc": fancValue]
    ]
    try {
        httpPost(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.status == 401 || (resp.data?.status == "Unauthorized")) {
                if (!retried) { setToken(); genieAction(action, true); return }
            }
            if (resp.success) {
                String sw = (fancValue == "1") ? "on" : "off"
                sendEvent(name: "switch", value: sw, isStateChange: true)
            }
        }
    } catch (Exception e) {
        log.warn "HTTP POST failed: ${e.message}"
    }
}

void setPerfumeAmount(Integer amount, boolean retried = false) {
    Integer a = (amount as Integer)
    if (!(a in [1,2,3])) { log.warn "Perfume amount must be 1, 2 or 3"; return }
    String hub_hash = state.genie_id
    String token = state.lastToken
    if (!hub_hash) { log.warn "Genie ID not set. Use refreshGenieName"; return }
    if (!token) { log.warn "Token not set. Use refreshToken"; return }

    String url = "https://rituals.apiv2.sense-company.com/apiv2/hubs/${hub_hash}/attributes/speedc"
    Map<String, Object> httpParams = [
            "uri"               : url,
            "contentType"       : "application/json",
            "requestContentType": "application/x-www-form-urlencoded",
            "headers"           : ["Accept": "*/*", "Authorization": token],
            "body"              : ["speedc": "${a}"]
    ]
    try {
        httpPost(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.status == 401 || (resp.data?.status == "Unauthorized")) {
                if (!retried) { setToken(); setPerfumeAmount(a, true); return }
            }
            if (resp.success) {
                sendEvent(name: "speed", value: a, isStateChange: true)
            }
        }
    } catch (Exception e) {
        log.warn "HTTP POST failed: ${e.message}"
    }
}

void setStatus(boolean retried = false) {
    String hub_hash = state.genie_id
    String token = state.lastToken
    if (!hub_hash || !token) { if (logEnable) log.debug "Status skipped: missing hub or token"; return }
    try {
        Map fanc
        Map speed
        Map wific
        Map fillc
        def fancParams = [
                uri: "https://rituals.apiv2.sense-company.com/apiv2/hubs/${hub_hash}/attributes/fanc",
                contentType: "application/json",
                headers: ["Accept": "*/*", "Authorization": token]
        ]
        httpGet(fancParams) { resp ->
            if (resp.status == 401 || (resp.data?.status == "Unauthorized")) {
                if (!retried) { setToken(); setStatus(true); return }
            }
            if (resp.success) fanc = resp.data as Map
        }
        def speedParams = [
                uri: "https://rituals.apiv2.sense-company.com/apiv2/hubs/${hub_hash}/attributes/speedc",
                contentType: "application/json",
                headers: ["Accept": "*/*", "Authorization": token]
        ]
        httpGet(speedParams) { resp ->
            if (resp.status == 401 || (resp.data?.status == "Unauthorized")) {
                if (!retried) { setToken(); setStatus(true); return }
            }
            if (resp.success) speed = resp.data as Map
        }
        def wificParams = [
                uri: "https://rituals.apiv2.sense-company.com/apiv2/hubs/${hub_hash}/sensors/wific",
                contentType: "application/json",
                headers: ["Accept": "*/*", "Authorization": token]
        ]
        httpGet(wificParams) { resp ->
            if (resp.status == 401 || (resp.data?.status == "Unauthorized")) {
                if (!retried) { setToken(); setStatus(true); return }
            }
            if (resp.success) wific = resp.data as Map
        }
        def fillcParams = [
                uri: "https://rituals.apiv2.sense-company.com/apiv2/hubs/${hub_hash}/sensors/fillc",
                contentType: "application/json",
                headers: ["Accept": "*/*", "Authorization": token]
        ]
        httpGet(fillcParams) { resp ->
            if (resp.status == 401 || (resp.data?.status == "Unauthorized")) {
                if (!retried) { setToken(); setStatus(true); return }
            }
            if (resp.success) fillc = resp.data as Map
        }
        String fancVal = (fanc?.value as String) ?: "0"
        String sw = (fancVal == "1") ? "on" : "off"
        sendEvent(name: "switch", value: sw, isStateChange: true)
        Integer spd = (speed?.value as Integer) ?: 1
        sendEvent(name: "speed", value: spd, isStateChange: true)

        Integer wifiPct = mapWifiIconToPct(wific?.icon as String)
        if (wifiPct != null) {
            sendEvent(name: "wifi_percentage", value: wifiPct, unit: "%", isStateChange: true)
        }
        String fillTitle = (fillc?.title as String)
        if (fillTitle) {
            sendEvent(name: "cartridge_fill", value: fillTitle, isStateChange: true)
        }
    } catch (Exception e) {
        log.warn "HTTP GET failed: ${e.message}"
    }
}

private Integer mapWifiIconToPct(String icon) {
    if (!icon) return null
    switch (icon) {
        case "icon-signal.png": return 100
        case "icon-signal-75.png": return 75
        case "icon-signal-low.png": return 25
        case "icon-signal-0.png": return 0
        default: return null
    }
}

void cleanup() {
    state.remove("genie_name")
    state.remove("genie_id")
}

void updateCheck(){
    setVersion()
    String updateMsg = ""
    Map params = [uri: "https://raw.githubusercontent.com/vpsupun/hubitat/master/resources/version.json", contentType: "application/json; charset=utf-8"]
    try {
        httpGet(params) { resp ->
            if (logEnable) log.debug " Version Checking - Response Data: ${resp.data}"
            if (logEnable) log.debug " ${state.appName} Debug Driver info : ${resp.data.driver.EatonXComfort}"
            Map driverInfo = resp.data.driver.get(state.appName)
            if (driverInfo != null) {
                if (logEnable) log.debug " Debug Driver info : ${driverInfo}"
                String newVerRaw = driverInfo?.version as String
                Integer newVer = newVerRaw?.replace(".", "") as Integer
                Integer currentVer = state.version.replace(".", "") as Integer
                String updateInfo = driverInfo?.updateInfo
                switch (newVer) {
                    case 999:
                        updateMsg = "<b>** This driver is no longer supported by the auther, ${state.author} **</b>"
                        log.warn "** This driver is no longer supported by the auther, ${state.author} **"
                        break;
                    case 000:
                        updateMsg = "<b>** This driver is still in beta **</b>"
                        log.warn "** This driver is still in beta **"
                        break;
                    case currentVer:
                        updateMsg = "up to date"
                        log.info "You are using the current version of this driver"
                        state.remove("newVersionChangeLog")
                        break;
                    default :
                        updateMsg = "<b>** A new version is availabe (version: ${newVerRaw}) **</b>"
                        log.warn "** A new version is availabe (version: ${newVerRaw}) **"
                        state.newVersionChangeLog = updateInfo
                }
                state.author = resp.data.author
                state.versionInfo = updateMsg
            } else {
                if (logEnable) log.warn "Version update is not implemented for this app !"
                state.remove("newVersionChangeLog")
                state.remove("versionInfo")
            }
        }
    }
    catch (e) {
        log.error "Error while fetching the version information ${e}"
    }
}

void updateSchedule(){
    unschedule(updateCheck)
    def hour = Math.round(Math.floor(Math.random() * 23))
    String cron = "0 0 ${hour} * * ? *"
    updateCheck()
    schedule(cron, updateCheck)
}

void setPolling() {
    unschedule(setStatus)
    def sec = Math.round(Math.floor(Math.random() * 60))
    def min = Math.round(Math.floor(Math.random() * settings.polling.toInteger()))
    String cron = "${sec} ${min}/${settings.polling.toInteger()} * * * ?"
    schedule(cron, setStatus)
}