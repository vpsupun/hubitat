/*
 * Itho NRG Wi-Fi Controller
 *
 * Hubitat connecting to the Itho NRG Wi-Fi controller
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
    state.version = "0.0.1"
    state.appName = "IthoNRG"
}

metadata {
    definition(name: "Itho NRG Wi-Fi Controller", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/IthoNRG.groovy") {
        capability "Fan Control"
        capability "Refresh"
        capability "Polling"
    }
}

preferences {
    section("Itho NRG controller data") {
        input "IP", "text", title: "IP Address", required: true
        input "polling", "text", title: "Polling Interval (mins)", required: true, defaultValue: "15", range: 2..59
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
    updateSchedule()
    setPolling()
}

void parse(String description) {
    if (logEnable) log.debug(description)
}

void setSpeed(String fanSpeed) {
    log.info "Setting the Speed : ${fanSpeed}"
    setFanSpeed(fanSpeed)
}

void refresh() {
    updateFanSpeed()
}

void poll() {
    updateFanSpeed()
}

void updateFanSpeed() {
    String currentSpeed = getFanSpeed()
    sendEvent("name":"speed", "value":currentSpeed)
}

String getFanSpeed() {
    String url = "http://" + settings.IP + "/api.html?get=currentspeed"
    Integer currentSpeedValue = httpCall(url) as Integer
    String currentSpeed = getSpeedFromValue(currentSpeedValue)
    return currentSpeed
}

void setFanSpeed(String fanSpeed) {
    Integer fanSpeedValue = _fanStatus[fanSpeed]
    String url = "http://" + settings.IP + "/api.html?speed=" + fanSpeedValue
    String returnMsg = httpCall(url)
    if (returnMsg == "OK") {
        sendEvent("name":"speed", "value":fanSpeed)
    }
}

String getSpeedFromValue(Integer speedValue) {
    String speed
    if (speedValue <= _fanStatus["off"]) {
        speed = "off"
    } else if (speedValue <= _fanStatus["low"]) {
        speed = "low"
    } else if (speedValue <= _fanStatus["medium-low"]) {
        speed = "medium-low"
    } else if (speedValue <= _fanStatus["medium"]) {
        speed = "medium"
    } else if (speedValue <= _fanStatus["medium-high"]) {
        speed = "medium-high"
    } else if (speedValue <= _fanStatus["high"]) {
        speed = "high"
    } else {
        speed = "auto"
    }
    return speed
}

String httpCall(String url) {
    String value
    Map<String> httpParams = [
            "uri"    : url
    ]

    try {
        httpGet(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "HTTP response received : ${resp.data}"
            }
            if (resp.success) {
                value = resp.data as String
            }
        }
    } catch (Exception e) {
        log.warn "HTTP GET failed: ${e.message}"
    }

    return value
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
    unschedule(updateFanSpeed)
    def sec = Math.round(Math.floor(Math.random() * 60))
    def min = Math.round(Math.floor(Math.random() * settings.polling.toInteger()))
    String cron = "${sec} ${min}/${settings.polling.toInteger()} * * * ?"
    schedule(cron, updateFanSpeed)
}

@Field static Map _fanStatus = [
        "on"  : 127,
        "low"  : 5,
        "medium-low"  : 50,
        "medium"  : 127,
        "medium-high"  : 200,
        "high"  : 254,
        "off"  : 1,
        "auto"  : 127
]