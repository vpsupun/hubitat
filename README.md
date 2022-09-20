# Tiny Hubitat Drivers and Apps

## EatonXComfort.groovy (driver)
Simple drive to control your Eaton XConfort Controller ( devices )

- Information required :
  - Eaton XComfort controller IP
  - Username - Username of the device
  - Password - Password of the device
  - Zone ID - If zone Ids are not known, leave it empty and enable debugging logs. When it clicked the on/off trigger, all zone Ids will be written to the logs
  - Device ID - If device Ids are not know, leave it empty and enable debugging logs. When it clicked the on/off trigger, all device Ids (relevant to the given zone Id) will be written to the logs
  - Enable debug logging - Enable/Disable debug logging

## BasicAuthHTTPSwitch (driver)
Simple driver to control any HTTP/HTTPS base endpoint with runs with basic auth (username/password) or no authentication

- Information required:
  - URI - Base URL of the endpoint. e.g. http://192.168.1.2
  - Path - Path component of the endpoint. e.g. /remote/json-rpc
  - Username - Username of the device/endpoint
  - Password - Password of the device/endpoint
  - Switch ON body - HTTP body required to switch or the device. e.g. { "jsonrpc": "2.0", "method": "StatusControlFunction/controlDevice", "params": ['hz_1', 'on'] }
  - Switch OFF body - HTTP body required to switch or the device. e.g. { "jsonrpc": "2.0", "method": "StatusControlFunction/controlDevice", "params": ['hz_1', 'off'] }
  - Enable debug logging - Enable/Disable debug logging 