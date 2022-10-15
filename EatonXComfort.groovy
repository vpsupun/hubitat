/*
 * Eaton XComfort Switch
 *
 * Hubitat connecting to the Eaton XComfort switch using HTTP
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
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
    state.version = "0.0.3"
    state.appName = "EatonXComfort"
}

metadata {
    definition(name: "Eaton XComfort Switch", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/EatonXComfort.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"
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
    checkVersion()
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

// Check Version   ***** with great thanks and acknowledgment to Cobra (github CobraVmax) for his original code **************
void checkVersion(){
    updateCheck()
    schedule("0 0 18 1/1 * ? *", updateCheck) // Cron schedule
}

void updateCheck(){
    setVersion()
    String updateMsg = ""
    def paramsUD = [uri: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master//resources/version.json", contentType: "application/json; charset=utf-8"]
    try {
        httpGet(paramsUD) { respUD ->
            if (logEnable) log.debug " Version Checking - Response Data: ${respUD.data}"
            String driverInfo = respUD.data.driver.${state.InternalName}
            String newVerRaw = driverInfo.version as String
            String newVer = newVerRaw.replace(".", "")
            String currentVer = state.version.replace(".", "")
            String updateInfo = driverInfo.updateInfo
            switch (newVer) {
                case newVer == "NLS":
                    updateMsg = "<b>** This driver is no longer supported by the auther, ${state.author} **</b>"
                    log.warn "** This driver is no longer supported by the auther, ${state.author} **"
                    break;
                case newVer == "BETA":
                    updateMsg = "<b>** This driver is still in beta **</b>"
                    log.warn "** This driver is still in beta **"
                    break;
                case currentVer < newVer:
                    updateMsg = "<b>** A new version is availabe (version: ${newVerRaw}) **</b>"
                    log.warn "** A new version is availabe (version: ${newVerRaw}) **"
                    state.newVersionInfo = updateInfo
                    break;
                case currentVer > newVer:
                    updateMsg = "<b>** You are using a test version of this driver (version: ${currentVer}) **</b>"
                    log.warn "** You are using a test version of this driver (version: ${currentVer}) **"
                    break;
                default :
                    updateMsg = "up to date"
                    log.info "You are using the current version of this driver"
            }
            state.author = respUD.data.author
            state.versionInfo = updateMsg
        }
    }
    catch (e) {
        log.error "Something went wrong while fetching the version information ${e}"
    }
}