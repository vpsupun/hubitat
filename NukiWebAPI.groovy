/*
 * Nuki Smart Lock web API driver
 *
 * Hubitat connecting to the web API of the Nuki to lock, unlock, unlatch and get other data
 *
 * Works with Nuki WiFi devices without the bridge
 *
 * Tested : Nuki 3.0 Pro
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

void setVersion(){
    state.version = "0.0.5"
    state.appName = "NukiWebAPI"
}

metadata {
    definition(name: "Nuki Web API", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/NukiWebAPI.groovy") {
        capability "Battery"
        capability "Lock"
        capability "Refresh"
        capability "Polling"

        attribute "lastLockStatus", "string"

        command "lock"
        command "unlock"
        command "unlatch"
        command "lockNGo"
    }
}

preferences {
    section("URI Data") {
        input "api_token", "text", title: "Nuki API Token", required: true
        input "lock_name", "text", title: "Nuki Smart Lock Name (case sensitive)", required: true
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
    setLockId()
    updateSchedule()
    setPolling()
}

void lock() {
    doorAction("lock")
}

void unlock() {
    doorAction("unlock")
}

void unlatch() {
    doorAction("unlatch")
}

void lockNGo() {
    doorAction("lock'n'go")
}

void refresh() {
    setStatus()
}

void poll() {
    setStatus()
}

void setStatus() {
    Map lock_data = getCurrentStatus()
    sendLockEvents(lock_data)
}

void doorAction(String action) {
    log.debug "Initiating : ${action}"
    String lock_id = state.lastLockId
    Map<String, Objects> data = [
            "path": "/smartlock/" + lock_id + "/action",
            "body": ["action": _lockActions[action].id]
    ]
    Map params = prepareNukiApi(data)
    try {
        httpPost(params) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                actionHandler(action)
            }
        }
    } catch (Exception e) {
        log.warn "HTTP GET failed: ${e.message}"
    }
}

void cleanup() {
    state.remove("lastLockStatus")
}

void actionHandler(String action) {
    boolean loop_stop = false
    String end_state = _lockActions[action].end_state
    for (i in 1..5) {
        Map lock_data = getCurrentStatus()
        String lock_state = lock_data.lock_state
        switch (lock_state) {
            case "uncalibrated":
                log.warn "Lock is ${lock_state}. Calibrate it using the smartphone."
                loop_stop = true
                break;
            case "motor blocked":
                log.warn "Motor blocked. Check the lock manually."
                loop_stop = true
                break;
            case "undefined":
                log.warn "Undefined state. Try again later."
                loop_stop = true
                break;
            case "${end_state}":
                log.debug "Action is completed : ${action}"
                loop_stop = true
                break;
            default:
                break;
        }
        if (loop_stop) {
            sendLockEvents(lock_data)
            break
        }
        pauseExecution(2000)
    }
}

void setLockId() {
    List locks = []
    if (logEnable) log.debug "Setting up the lock ID for faster operations"
    String lock_name = settings.lock_name
    Map<String> data = [
            "path"  : "/smartlock",
            "method": "GET"
    ]
    Map params = prepareNukiApi(data)
    try {
        httpGet(params) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                locks = resp.data
            }
        }
    } catch (Exception e) {
        log.warn "HTTP GET failed: ${e.message}"
    }

    def lock = locks.find { map -> map.name == lock_name }
    if (lock != []) {
        state.lastLockId = lock?.smartlockId
    } else
        log.warn "Invalid lock name or error on retrieving the lock ID"
}

Map getCurrentStatus() {
    Map lock_data = [:]
    Map lock_data_return = [:]
    if (logEnable) log.debug "Refreshing the lock data"
    String lock_id = state.lastLockId
    if (lock_id != null) {
        Map<String> data = [
                "path": "/smartlock/" + lock_id
        ]
        Map params = prepareNukiApi(data)
        try {
            httpGet(params) { resp ->
                if (logEnable) {
                    if (resp.data) log.debug "${resp.data}"
                }
                if (resp.success) {
                    lock_data = resp.data
                }
            }
        } catch (Exception e) {
            log.warn "HTTP GET failed: ${e.message}"
        }
        if (lock_data != [:]) {
            Integer state_num = lock_data?.state?.state
            String state_name = _lockStatus.get(state_num)
            Integer battery_state = lock_data?.state?.batteryCharge as Integer
            if (device.currentValue("lastLockStatus", true) != state_name) {
                sendEvent(name: "lastLockStatus", value: state_name, isStateChange: true)
            }
            lock_data_return.battery_state = battery_state
            lock_data_return.lock_state = state_name
        }
    }
    return lock_data_return
}

void sendLockEvents(Map lock_data) {
    String lock_state = lock_data.lock_state
    Integer battery_state = lock_data.battery_state
    if (lock_state == "locked" || lock_state == "unlocked") {
        sendEvent(name: "lock", value: lock_state, isStateChange: true)
    }
    if (battery_state >= 0 && battery_state <= 100) {
        sendEvent(name: "battery", value: battery_state, unit: "%", isStateChange: true)
    }
}

Map prepareNukiApi(Map data) {
    uri = "https://api.nuki.io"
    String token = settings.api_token

    Map<String> headers = [
            "Authorization": "Bearer " + token
    ]

    Map<String, Object> params = [
            "uri"               : uri,
            "contentType"       : "application/json",
            "requestContentType": "application/json",
            "headers"           : headers,
            "path"              : data.path,
            "body"              : data?.body
    ]
    return params
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
    unschedule(setStatus)
    def sec = Math.round(Math.floor(Math.random() * 60))
    def min = Math.round(Math.floor(Math.random() * settings.polling.toInteger()))
    String cron = "${sec} ${min}/${settings.polling.toInteger()} * * * ?"
    schedule(cron, setStatus)
}

@Field static Map _lockStatus = [
        0  : "uncalibrated",
        1  : "locked",
        2  : "unlocking",
        3  : "unlocked",
        4  : "locking",
        5  : "unlatched",
        6  : "unlocked (lock'n'go)",
        7  : "unlatching",
        253: "boot run",
        254: "motor blocked",
        255: "undefined"
]

@Field static Map _lockActions = [
        "unlock"                : ["id": 1, "end_state": "unlocked"],
        "lock"                  : ["id": 2, "end_state": "locked"],
        "unlatch"               : ["id": 3, "end_state": "unlatched"],
        "lock'n'go"             : ["id": 4, "end_state": "unlocked (lock'n'go)"]
]