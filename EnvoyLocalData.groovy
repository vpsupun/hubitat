/*
 * Enphase Envoy-S (metered) get production data with token
 *
 * Hubitat connecting to the Enphase Envoy-S (metered) with new firmware that requires a token to access local data
 *
 * Production output from Envoy : [wattHoursToday:xx, wattHoursSevenDays:xx, wattHoursLifetime:xx, wattsNow:xx]
 * Consumption data from Envoy : [wattHoursToday:xx, wattHoursSevenDays:xx, wattHoursLifetime:xx, wattsNow:xx]
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

void setVersion(){
    state.version = "0.0.6"
    state.appName = "EnvoyLocalData"
}

metadata {
    definition(name: "Enphase Envoy-S Production Data", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/EnvoyLocalData.groovy") {
        capability "Sensor"
        capability "Power Meter"
        capability "Energy Meter"
        capability "Refresh"
        capability "Polling"

        attribute "production_energy_today", "number"
        attribute "production_energy_last7days", "number"
        attribute "production_energy_life", "number"
        attribute "production_energy_now", "number"
        attribute "consumption_energy_today", "number"
        attribute "consumption_energy_last7days", "number"
        attribute "consumption_energy_life", "number"
        attribute "consumption_energy_now", "number"
        attribute "jwt_token", "string"
    }
}

preferences {
    section("URI Data") {
        input "ip", "text", title: "Envoy local IP", required: true
        input "email", "text", title: "Enlighten Email", required: true
        input "pass", "password", title: "Enlighten password", required: true
        input "serial", "text", title: "Envoy Serial Number", required: true
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
    setPolling()
    updateSchedule()
}

void poll() {
    pullData()
}

void refresh() {
    pullData()
}

void pullData() {
    String ip = settings.ip - "https://" - "http://" - "/"
    String production_url = "https://" + ip + "/api/v1/energy"
    List energy_data = []

    if (logEnable) log.debug "Pulling data..."
    String token = getToken()
    if (token != null) {
        Map<String> headers = [
                "Authorization": "Bearer " + token
        ]
        Map<String, Object> httpParams = [
                "uri"               : production_url,
                "contentType"       : "application/json",
                "requestContentType": "application/json",
                "ignoreSSLIssues"   : true,
                "headers"           : headers
        ]

        try {
            httpGet(httpParams) { resp ->
                if (logEnable) {
                    if (resp.data) log.debug "${resp.data}"
                }
                if (resp.success) {
                    energy_data = resp.data
                }
            }
        } catch (Exception e) {
            log.warn "HTTP get failed: ${e.message}"
        }

        Map production_data = energy_data.find{ it.type == 'Production' }
        Map consumption_data = energy_data.find{ it.type == 'Consumption' }
        Integer net_metering = (production_data?.wattsNow - consumption_data?.wattsNow) as Integer

        sendEvent(name: "production_energy_today", value: production_data?.wattHoursToday, displayed: false)
        sendEvent(name: "production_energy_last7days", value: production_data?.wattHoursSevenDays, displayed: false)
        sendEvent(name: "production_energy_life", value: production_data?.wattHoursLifetime, displayed: false)
        sendEvent(name: "production_energy_now", value: production_data?.wattsNow, isStateChange: true)

        sendEvent(name: "consumption_energy_today", value: consumption_data?.wattHoursToday, displayed: false)
        sendEvent(name: "consumption_energy_last7days", value: consumption_data?.wattHoursSevenDays, displayed: false)
        sendEvent(name: "consumption_energy_life", value: consumption_data?.wattHoursLifetime, displayed: false)
        sendEvent(name: "consumption_energy_now", value: consumption_data?.wattsNow, isStateChange: true)

        sendEvent(name: "power", value: net_metering, unit: "w", isStateChange: true)

    } else
        log.warn "Unable to get a valid token. Aborting..."
}

boolean isValidToken(String token) {
    boolean valid_token = false
    String response
    String ip = settings.ip - "https://" - "http://" - "/"
    String token_check_url = "https://" + ip + "/auth/check_jwt"

    if (logEnable) log.debug "Validating the token"
    Map<String> headers = [
            "Authorization": "Bearer " + token
    ]
    Map<String, Object> httpParams = [
            "uri"               : token_check_url,
            "contentType"       : "text/html",
            "requestContentType": "application/json",
            "ignoreSSLIssues"   : true,
            "headers"           : headers
    ]
    try {
        httpGet(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                response = resp.data
                if (response.contains("Valid token.")) {
                    valid_token = true
                }
            }
        }
    } catch (Exception e) {
        log.warn "HTTP get failed: ${e.message}"
    }
    return valid_token
}

String getSession() {
    String session
    String login_url = "https://enlighten.enphaseenergy.com/login/login.json"

    if (logEnable) log.debug "Generating a session"
    Map<String> data = [
            "user[email]"   : settings.email,
            "user[password]": settings.pass
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
                session = resp.data?.session_id
            }
        }
    } catch (Exception e) {
        log.warn "HTTP post failed: ${e.message}"
    }

    if (logEnable) log.debug "Sessin Id: ${session}"
    return session
}

String getToken() {
    String valid_token
    String current_token
    // migrate from attribute to state variable
    if (state.jwt_token != null) {
        current_token = state.jwt_token
    } else if (device.currentValue("jwt_token", true) != null) {
        state.jwt_token = device.currentValue("jwt_token", true)
    }
    if (logEnable) log.debug "Retrieving the token"
    if (current_token != null && isValidToken(current_token)) {
        if (logEnable) log.debug "Current token is still valid. Using it. "
        valid_token = current_token
    } else {
        if (logEnable) log.debug "Current token is expired. Generating a new one."
        String session = getSession()
        if (session != null) {
            String token_generated = generateToken(session)
            if (token_generated != null && isValidToken(token_generated)) {
                sendEvent(name: "jwt_token", value: token_generated, displayed: false)
                state.jwt_token = token_generated
                valid_token = token_generated
            } else {
                log.warn "Generated token is not valid. Investigate with debug logs"
            }
        } else {
            log.warn "Generated token is null. Investigate with debug logs"
        }
    }
    return valid_token
}

String generateToken(String session_id) {
    String token
    String tokenUrl = "https://entrez.enphaseenergy.com/tokens"

    if (logEnable) log.debug "Generating a new token"
    Map<String> data = [
            "session_id": session_id,
            "serial_num": settings.serial,
            "username"  : settings.email
    ]
    Map<String, Object> httpParams = [
            "uri"               : tokenUrl,
            "contentType"       : "text/html",
            "headers"           : ["Accept" : "application/json"],
            "requestContentType": "application/json",
            "body"              : data
    ]
    if (logEnable) log.debug "HTTP params: ${httpParams}"
    try {
        httpPost(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "HTTP response: ${resp.data}"
            }
            if (resp.success) {
                token = resp.data
            }
        }
    } catch (Exception e) {
        log.warn "HTTP post failed: ${e.message}"
    }
    if (logEnable) log.debug "Generated token : ${token}"
    return token
}

void updateCheck(){
    setVersion()
    String updateMsg = ""
    Map params = [uri: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/resources/version.json", contentType: "application/json; charset=utf-8"]
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
    unschedule()
    def sec = Math.round(Math.floor(Math.random() * 60))
    def min = Math.round(Math.floor(Math.random() * settings.polling.toInteger()))
    String cron = "${sec} ${min}/${settings.polling.toInteger()} * * * ?" // every N min
    schedule(cron, pullData)
}
