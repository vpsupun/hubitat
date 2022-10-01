/*
 * Enphase Envoy-S (metered) get production data with token
 *
 * Hubitat connecting to the Enphase Envoy-S (metered) with new firmware that requires a token to access local data
 *
 * Production output from Envoy : [wattHoursToday:xx, wattHoursSevenDays:xx, wattHoursLifetime:xx, wattsNow:xx]
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

metadata {
    definition(name: "Enphase Envoy-S Production Data", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/EnvoyLocalData.groovy") {
        capability "Sensor"
        capability "Power Meter"
        capability "Energy Meter"
        capability "Refresh"
        capability "Polling"

        attribute "energy_today", "number"
        attribute "energy_last7days", "number"
        attribute "energy_life", "number"
        attribute "energy_now", "number"
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
    setPolling()
    if (logEnable) runIn(1800, logsOff)
}

void poll() {
    pullData()
}

void refresh() {
    pullData()
}

void pullData() {
    String production_url = "https://" + settings.ip + "/api/v1/production"
    Map production_data = [:]

    if (logEnable) log.debug "Pulling data..."
    String token = getToken()
    if (token != null) {
        Map<String> headers = [
                "Authorization": "Basic " + token
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
                    production_data = resp.data
                }
            }
        } catch (Exception e) {
            log.warn "HTTP get failed: ${e.message}"
        }

        sendEvent(name: "energy_today", value: production_data?.wattHoursToday, displayed: false)
        sendEvent(name: "energy_last7days", value: production_data?.wattHoursSevenDays, displayed: false)
        sendEvent(name: "energy_life", value: production_data?.wattHoursLifetime, displayed: false)
        sendEvent(name: "power", value: production_data?.wattsNow, unit: "w", isStateChange: true)

    } else
        log.warn "Unable to get a valid token. Aborting..."
}

boolean isValidToken(String token) {
    boolean valid_token = false
    String response
    String token_check_url = "https://" + settings.ip + "/auth/check_jwt"

    if (logEnable) log.debug "Validating the token"
    Map<String> headers = [
            "Authorization": "Basic " + token
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
            }
        }
    } catch (Exception e) {
        log.warn "HTTP get failed: ${e.message}"
    }
    if (response.contains("Valid token.")) {
        valid_token = true
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
    String current_token = device.currentValue("jwt_token", true)

    if (logEnable) log.debug "Retrieving the token"
    if (current_token != null && isValidToken(current_token)) {
        if (logEnable) log.debug "Current token is still valid. Using it. "
        valid_token = current_token
    } else {
        if (logEnable) log.debug "Current token is still expired. Generating a new one."
        String session = getSession()
        if (session != null) {
            String token_generated = generateToken(session)
            if (token_generated != null && isValidToken(token_generated)) {
                sendEvent(name: "jwt_token", value: token_generated, displayed: false)
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

void setPolling() {
    unschedule()
    def sec = Math.round(Math.floor(Math.random() * 60))
    def min = Math.round(Math.floor(Math.random() * settings.polling.toInteger()))
    String cron = "${sec} ${min}/${settings.polling.toInteger()} * * * ?" // every N min
    log.warn "startPolling: schedule('$cron', pullData)".toString()
    schedule(cron, pullData)
}