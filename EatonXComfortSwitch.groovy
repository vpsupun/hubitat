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

import groovy.json.*
import groovy.transform.Field

void setVersion(){
    state.version = "0.0.4"
    state.appName = "EatonXComfortSwitch"
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
        input "zone", "text", title: "Zone Name", required: false
        input "dev", "text", title: "Device Name", required: false
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
    setZoneId()
    setDeviceId()
    checkVersion()
}

void parse(String description) {
    if (logEnable) log.debug(description)
}

void on() {
    if (state.zoneId != null && state.deviceId != null) {
        triggerOn()
    }
}

void off() {
    if (state.zoneId != null && state.deviceId != null) {
        triggerOff()
    }
}

void refresh() {
    getStatus()
}

void poll() {
    getStatus()
}

void getStatus() {
    if (state.zoneId != null || state.deviceId != null) {
        List devices = getDevices(state.zoneId)
        if (devices != []) {
            def filtered = devices.flatten().find { it.id.trim().replace(" ","") == state.deviceId }
            if (filtered != null) {
                if (filtered.type == "SwitchActuator") {
                    if (filtered.value == "OFF") {
                        sendEvent(name: "switch", value: "off", isStateChange: true)
                    } else if (filtered.value == "ON") {
                        sendEvent(name: "switch", value: "on", isStateChange: true)
                    }
                }
                if (filtered.type == "DimActuator") {
                    Integer value = filtered.value as Integer
                    sendEvent(name: "switchLevel", value: value, isStateChange: true)
                    if (value == 0) {
                        sendEvent(name: "switch", value: "off", isStateChange: true)
                    } else if (value > 0) {
                        sendEvent(name: "switch", value: "on", isStateChange: true)
                    }
                }
            } else {
                if (logEnable) log.warn "device ID may have changed. Update by clicking 'Save Preferences'"
            }
        } else {
            if (logEnable) log.warn "zone ID may have changed. Update by clicking 'Save Preferences'"
        }
    } else {
        if (logEnable) log.warn "device and zone state variables are not set"
    }
}

void triggerOn() {
    if (logEnable) log.debug "Switching on the device, [${settings.dev}] on the zone, [${settings.zone}]"
    List params = ["${state.zoneId}", "${state.deviceId}", "on"]
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

void triggerOff() {
    if (logEnable) log.debug "Switching off the device, [${settings.dev}] on the zone, [${settings.zone}]"
    List params = ["${state.zoneId}", "${state.deviceId}", "off"]
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

void setZoneId() {
    List zones = getZones()
    String zone = settings.zone.trim()
    if (zones != []) {
        if (logEnable) log.debug "Trimmed zone : ${zone}"
        def filtered = zones.flatten().find { it.zoneName == zone }
        if (logEnable) log.debug "Filetered zone : ${filtered}"
        if (filtered != []) {
            state.zoneId = filtered?.zoneId
        } else {
            if (logEnable) log.warn "Incorrect zone name : ${zone}"
        }
    }
}

void setDeviceId() {
    String device = settings.dev.trim().replace(" ","")
    if (logEnable) log.debug "Device Name trimmed : ${device}"
    if (state.zoneId != null) {
        List devices = getDevices(state.zoneId)
        if (devices != []) {
            def filtered = devices.flatten().find { it.name.trim().replace(" ","") == device }
            if (logEnable) log.debug "Filetered devices : ${filtered}"
            if (filtered != []) {
                if (filtered.type == "SwitchActuator" || filtered.type == "DimActuator") {
                    state.deviceId = filtered?.id
                } else {
                    if (logEnable) log.debug "Device is not a switch or dim actuator (type : ${filtered.type}"
                    state.deviceId = "error"
                }
            } else {
                if (logEnable) log.warn "Incorrect device name : ${device}"
            }
        }
    }
}

List getZones() {
    List zones = []
    String method = "HFM/getZones"
    List params = []
    Map httpParams = prepareHttpParams(method, params)
    try {
        httpPostJson(httpParams) { resp ->
            if (resp.success) {
                zones = resp.data?.result
            }
        }
    } catch (Exception e) {
        log.warn "Getting zones failed: ${e.message}"
    }
    if (logEnable) log.debug "Zone data : ${zones}"
    return zones
}

List getDevices(String zoneId = null) {
    List devices = []
    if (zoneId != null) {
        String method = "StatusControlFunction/getDevices"
        List params = ["${zoneId}", ""]
        Map httpParams = prepareHttpParams(method, params)
        try {
            httpPostJson(httpParams) { resp ->
                if (resp.success) {
                    devices = resp.data?.result
                }
            }
        } catch (Exception e) {
            log.warn "Getting devices failed: ${e.message}"
        }
    }
    if (logEnable) log.debug "Device data in the zone ${settings.zone}: ${devices}"
    return devices
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
    //if (logEnable) log.debug "List of HTTP parameters : ${httpParams}"
    return httpParams
}

void checkVersion(){
    updateCheck()
    schedule("0 0 18 1/1 * ? *", updateCheck)
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