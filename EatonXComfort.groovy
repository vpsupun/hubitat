/*
 * Eaton XComfort Switch
 *
 * Hubitat connecting to the Eaton XComfort switch using HTTP
 *
 */
metadata {
    definition(name: "Eaton XComfort Switch", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/EatonXComfort.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
    }
}

preferences {
    section("xComfort Device Data") {
        input "URI", "text", title: "URI", required: true
        input "username", "text", title: "Username", required: true
        input "pass", "password", title: "Password", required: true
        input "zone", "text", title: "Zone Id", required: false
        input "devId", "text", title: "Device Id", required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

void logsOff() {
    log.warn "Debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

void updated() {
    log.info "Updated..."
    log.warn "Debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
}

void parse(String description) {
    if (logEnable) log.debug(description)
}

void on() {
    if (settings.zone == null) {
        getZones()
    } else if (settings.devId == null) {
        getDevices()
    } else {
        triggerOn()
    }
}

void off() {
    if (settings.zone == null) {
        getZones()
    } else if (settings.devId == null) {
        getDevices()
    } else {
        triggerOff()
    }
}

def triggerOn() {
    if (logEnable) log.debug "Switching on the device, [${settings.devId}] on the zone, [${settings.zone}]"
    List params = ["${settings.zone}", "${settings.devId}", "on"]
    Map httpParams = prepareHttpParams("StatusControlFunction/controlDevice", params)
    try {
        httpPostJson(httpParams) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "on", isStateChange: true)
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Switch on failed: ${e.message}"
    }
}

def triggerOff() {
    if (logEnable) log.debug "Switching off the device, [${settings.devId}] on the zone, [${settings.zone}]"
    List params = ["${settings.zone}", "${settings.devId}", "off"]
    Map httpParams = prepareHttpParams("StatusControlFunction/controlDevice", params)
    try {
        httpPostJson(httpParams) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "off", isStateChange: true)
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Switch off failed: ${e.message}"
    }
}

def getZones() {
    String method = "HFM/getZones"
    List params = []
    Map httpParams = prepareHttpParams(method, params)
    try {
        httpPostJson(httpParams) { resp ->
            if (resp.success) {
                if (logEnable) log.debug "Zone data : ${resp.data}"
            }
        }
    } catch (Exception e) {
        log.warn "Getting zones failed: ${e.message}"
    }
}

def getDevices(String zone = null) {
    if (settings.zone == null) {
        if (logEnable) log.debug "Zone id is required to retried device ids. Fetching zone details..."
        getZones()
    } else {
        String method = "StatusControlFunction/getDevices"
        List params = ["${settings.zone}", ""]
        Map httpParams = prepareHttpParams(method, params)
        try {
            httpPostJson(httpParams) { resp ->
                if (resp.success) {
                    if (logEnable) log.debug "Device data in the zone ${settings.zone}: ${resp.data}"
                }
            }
        } catch (Exception e) {
            log.warn "Getting devices failed: ${e.message}"
        }
    }
}

def prepareHttpParams(String method, List params = []) {
    String path = "/remote/json-rpc"
    def pair = "$username:$pass"
    def basicAuth = pair.bytes.encodeBase64();

    Map<String, Object> content = [
            "jsonrpc": "2.0",
            "id"     : 1
    ]
    content.method = method
    content.params = params

    Map<String> headers = [
            "Authorization": "Basic " + basicAuth
    ]

    Map<String, Object> httpParams = [
            "uri"    : settings.URI,
            "headers": headers,
            "path"   : path,
            "body"   : content
    ]
    if (logEnable) log.debug "List of HTTP parameters : ${httpParams}"
    return httpParams
}