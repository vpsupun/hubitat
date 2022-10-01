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

metadata {
    definition(name: "Nuki Web API", namespace: "community", author: "Supun Vidana Pathiranage", importUrl: "https://raw.githubusercontent.com/vpsupun/hubitat-eaton-xcomfort/master/NukiWebAPI.groovy") {
        capability "Battery"
        capability "Lock"

        command "lock"
        command "unlock"
        command "unlatch"
        command "lockNGo"
        command "lockNGoUnlatch"
        command "refresh"

        attribute "lastLockStatus", "string"
        attribute "lastLockId", "string"
    }
}

preferences {
    section("URI Data") {
        input "api_token", "text", title: "Nuki API Token", required: true
        input "lock_name", "text", title: "Nuki Smart Lock Name (case sensitive)", required: true
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
    setLockId()
    if (logEnable) runIn(1800, logsOff)
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

void lockNGoUnlatch() {
    doorAction("lock'n'go with unlatch")
}

void refresh() {
    Map lock_data = getCurrentStatus()
    sendLockEvents(lock_data)
}

void doorAction(String action) {
    if (logEnable) log.debug "Initiating : ${action}"
    String lock_id = device.currentValue("lastLockId", true)
    Map<String, Objects> data = [
            "path": "/smartlock/" + lock_id + "/action",
            "body": ["action": _lockActions[action]]
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

void actionHandler(String action) {
    boolean loop_stop = false
    for (i in 1..5) {
        Map lock_data = getCurrentStatus()
        String lock_state = lock_data.lock_state
        int battery_state = lock_data.battery_state
        switch (lock_state) {
            case "uncalibrated":
                log.warn "Lock is ${lock_state}. Calibrate it using the smartphone."
                loop_stop = true
                break
            case "motor blocked":
                log.warn "Motor blocked. Check the lock manually."
                loop_stop = true
                break
            case "undefined":
                log.warn "Undefined state. Try again later."
                loop_stop = true
                break
            case action:
                loop_stop = true
                break
            default:
                break
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

    List lock = locks.findAll { map -> map.name == lock_name }
    if (lock != []) {
        sendEvent(name: "lastLockId", value: lock[0]?.smartlockId, displayed: false)
    } else
        log.warn "Invalid lock name or error on retrieving the lock ID"
}

Map getCurrentStatus() {
    Map lock_data = [:]
    Map lock_data_return = [:]
    if (logEnable) log.debug "Refreshing the lock data"
    String lock_id = device.currentValue("lastLockId", true)
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
            int state = lock_data?.state?.state
            String lock_state = _lockStatus.get(state)
            int battery_state = lock_data?.state?.batteryCharge
            if (device.currentValue("lastLockStatus", true) != lock_state) sendEvent(name: "lastLockStatus", value: lock_state, isStateChange: true)

            lock_data_return.battery_state = battery_state
            lock_data_return.lock_state = lock_state
        }
    }
    return lock_data_return
}

void sendLockEvents(Map lock_data) {
    String lock_state = lock_data.lock_state
    int battery_state = lock_data.battery_state
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
        "unlock"                : 1,
        "lock"                  : 2,
        "unlatch"               : 3,
        "lock'n'go"             : 4,
        "lock'n'go with unlatch": 5
]