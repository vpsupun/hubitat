/*
 * Basic Auth HTTP/HTTPS switch
 *
 * Hubitat connecting to an HTTP/HTTPS endpoint with basic auth.
 *
 */
metadata {
    definition(name: "Basic Auth HTTP/HTTPS Switch", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/BasicAuthHTTPSwitch.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
    }
}

preferences {
    section("URI Data") {
        input "URI", "text", title: "URI", required: true
        input "path", "text", title: "Path", required: true
        input "username", "text", title: "Username", required: true
        input "pass", "password", title: "Password", required: true
        input "onData", "text", title: "Switch ON body", required: true
        input "offData", "text", title: "Switch OFF body", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
}

def parse(String description) {
    if (logEnable) log.debug(description)
}

def on() {
    if (logEnable) log.debug "Switching on the device"
    Map httpParams = prepareHttpParams(settings.onData)
    try {
        httpPostJson(httpParams) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "on", isStateChange: true)
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Call to off failed: ${e.message}"
    }
}

def off() {
    if (logEnable) log.debug "Switching off the device"
    Map httpParams = prepareHttpParams(settings.offData)
    try {
        httpPostJson(httpParams) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "off", isStateChange: true)
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Call to off failed: ${e.message}"
    }
}

def prepareHttpParams(String data = null) {
    if (logEnable) log.debug "Received body : ${data}"

    Map<String, Object> httpParams = [
            "uri"    : settings.URI
    ]
    Map<String> headers = [
            "Content-Type": "application/json"
    ]

    if (settings.path != null) {
        httpParams.path = settings.path
    }
    if (data != null) {
        httpParams.body = data
    }

    if (settings.username != null && settings.pass != null) {
        def pair = "$username:$pass"
        def basicAuth = pair.bytes.encodeBase64();
        headers.Authorization = "Basic " + basicAuth
    }

    httpParams.headers = headers

    if (logEnable) log.debug "List of HTTP parameters : ${httpParams}"
    return httpParams
}