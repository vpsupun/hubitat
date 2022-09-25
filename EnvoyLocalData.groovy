/*
 * Enphase Envoy-S (metered) get production data with token
 *
 * Hubitat connecting to the Enphase Envoy-S (metered) with new firmware that requires a token to access local data
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

void poll() {
    pullData()
}

void refresh() {
    pullData()
}

def pullData() {
    if (logEnable) log.debug "Pulling data..."
    String production_url = "https://" + settings.ip + "/api/v1/production"
    String token = getToken()
    if (token != null) {
        Map<String> headers = [
                "Content-Type" : "application/json",
                "Authorization": "Basic " + token
        ]
        Map<String, Object> httpParams = [
                "uri"    : production_url,
                "headers": headers
        ]

        def response = myHttpGet(httpParams)

        if (response.wattHoursToday) sendEvent(name: "energy_today", value: response.wattHoursToday, displayed: false)
        if (response.wattHoursSevenDays) sendEvent(name: "energy_last7days", value: response.wattHoursSevenDays, displayed: false)
        if (response.wattHoursLifetime) sendEvent(name: "energy_life", value: response.wattHoursLifetime, displayed: false)
        if (response.wattsNow) sendEvent(name: "energy_now", value: response.wattsNow, displayed: false)
    } else
        log.warn "Unable to get a valid token. Aborting..."
}

def validateToken(String token) {
    if (logEnable) log.debug "Validating the token"
    String token_check_url = "https://" + settings.ip + "/auth/check_jwt"
    Map<String> headers = [
            "Content-Type" : "application/json",
            "Authorization": "Basic " + token
    ]
    Map<String, Object> httpParams = [
            "uri"    : token_check_url,
            "headers": headers
    ]

    def response = myHttpGet(httpParams)
    if (response.contains("Valid token.")) {
        return true
    } else
        return false
}

def getSession() {
    if (logEnable) log.debug "Generating a session"
    String login_url = "https://enlighten.enphaseenergy.com/login/login.json"
    Map<String> data = [
            "user[email]"   : settings.email,
            "user[password]": settings.pass
    ]
    Map<String, Object> httpParams = [
            "uri"    : login_url,
            "body"   : data
    ]

    def response = myHttpPost(httpParams)
    if (logEnable) log.debug "Session 1: ${response}"
    return response
}

def getToken() {
    if (logEnable) log.debug "Retrieving the token"
    if (device.currentValue(jwt_token) != null && validateToken(currentValue(jwt_token))) {
        return currentValue(jwt_token)
    } else {
        String session = getSession()
        String token = generateToken(session)
        if (token != null && validateToken(token)) {
            sendEvent(name: "jwt_token", value: token, displayed: false)
            return token
        } else {
            log.warn "Token generation has been failed or generated token is not valid."
            return null
        }
    }
}

def generateToken(String session_data) {
    if (logEnable) log.debug "Generating a new token"
    if (logEnable) log.debug "Session : ${session_data}"
    String tokenUrl = "https://entrez.enphaseenergy.com/tokens"
    if (session_data != null) {
        String session_id = session_data.session_id
        Map<String> data = [
                "session_id": session_id,
                "serial_num": settings.serial,
                "username"  : settings.email
        ]
        Map<String> headers = [
                "Content-Type": "application/json"
        ]
        Map<String, Object> httpParams = [
                "uri"    : tokenUrl,
                "headers": headers,
                "body"   : data
        ]
        def response = myHttpPost(httpParams)
        return response
    } else {
        log.warn "Session ID was null. Enable debug logs to investigate."
        return null
    }
}

def myHttpPost(Map httpParams) {
    if (logEnable) log.debug "HTTP params: ${httpParams}"
    try {
        httpPost(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                if (logEnable) log.debug "Returning: ${resp.data}"
                return resp.data
            } else {
                return "XX"
            }
        }
    } catch(Exception e) {
        log.warn "HTTP post failed: ${e.message}"
        return null
    }
}

def myHttpGet(Map httpParams) {
    if (logEnable) log.debug "HTTP params: ${httpParams}"
    try {
        httpGetJson(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                return resp.data
            } else {
                return null
            }
        }
    } catch(Exception e) {
        log.warn "HTTP get failed: ${e.message}"
        return null
    }
}