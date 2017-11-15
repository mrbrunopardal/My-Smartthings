/**
 *  Copyright 2015 Lazcad / RaveTam
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  18/01/2017 corrected the temperature reading a4refillpad
 */

metadata {
    definition (name: "Xiaomi Outlet", namespace: "RostikBor", author: "RostikBor") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Temperature Measurement"
    }

    // simulator metadata
    simulator {
        // status messages
        status "on": "on/off: 1"
        status "off": "on/off: 0"

        // reply messages
        reply "zcl on-off on": "on/off: 1"
        reply "zcl on-off off": "on/off: 0"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.Appliances.appliances17", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.Appliances.appliances17", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.Appliances.appliances17", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.Appliances.appliances17", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute("device.lastOn", key: "SECONDARY_CONTROL") {
    			attributeState("default", label:'Last On: ${currentValue}')
            }
        }
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°',
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        valueTile("lastcheckin", "device.lastCheckin", decoration: "flat", inactiveLabel: false, width: 4, height: 1) {
			state "default", label:'Last Checkin: ${currentValue}'
		}
        valueTile("lasttemp", "device.lastTemp", decoration: "flat", inactiveLabel: false, width: 4, height: 1) {
			state "default", label:'Last Temp: ${currentValue}'
		}
        valueTile("raw", "device.rawT", width: 2, height: 1) {
            state "default", label: 'raw: ${currentValue}'
        }
        main (["switch", "temperature"])
        details(["switch", "temperature", "refresh", "raw", "lastcheckin","lasttemp"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
   log.debug "Parsing '${description}'"
   def value = zigbee.parse(description)?.text
   Map map = [:]
   
   //  send event for heartbeat    
   def now = new Date().format("MMM-d-yyyy h:mm a", location.timeZone)
   sendEvent(name: "lastCheckin", value: now, descriptionText: "Check-in")
   //refresh.refresh   
   if (description?.startsWith('on/off: 1')){
   		sendEvent(name: "lastOn", value: now, descriptionText: "")
    }
    
   if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
    else if (description?.startsWith('on/off: ')){
    	def resultMap = zigbee.getKnownDescription(description)
   		log.debug "${resultMap}"
        
        map = parseCustomMessage(description) 
    }

	log.debug "Parse returned $map"
	def results = map ? createEvent(map) : null
    
    return results;
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	log.debug cluster
    
    if (cluster.clusterId == 0x0006 && cluster.command == 0x01){
    	def onoff = cluster.data[-1]
        if (onoff == 1)
        	resultMap = createEvent(name: "switch", value: "on")
        else if (onoff == 0)
            resultMap = createEvent(name: "switch", value: "off")
    }
    
	return resultMap
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	//log.debug "Desc Map: $descMap"
 
	Map resultMap = [:]

	if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
	}
    if (descMap.cluster == "0002" && descMap.attrId == "0000") {
        sendEvent(name: "rawT", value: descMap.value)
        //
    	//def temp = ((descMap.value.toDouble() * 1.8) + 33).toInteger()
        //if (temp < 50)
        def temp = Math.round(((Integer.parseInt(descMap.value,16) / 2.0 + 7) * 1.8) + 33)//.toInteger()
		resultMap = createEvent(name: "temperature", value: temp, unit: getTemperatureScale())
		def now = new Date().format("MMM-d-yyyy h:mm a", location.timeZone)
   		sendEvent(name: "lastTemp", value: now, descriptionText: "Last Temp")
    }
    else if (descMap.cluster == "0008" && descMap.attrId == "0000") {
    	resultMap = createEvent(name: "switch", value: "off")
    } 
	return resultMap
}

def off() {
    log.debug "off()"
	sendEvent(name: "switch", value: "off")
	"st cmd 0x${device.deviceNetworkId} 1 6 0 {}"
}

def on() {
   log.debug "on()"
	sendEvent(name: "switch", value: "on")
	"st cmd 0x${device.deviceNetworkId} 1 6 1 {}"
}

def refresh() {
	log.debug "refreshing"
    [
        "st rattr 0x${device.deviceNetworkId} 1 6 0", "delay 500",
        "st rattr 0x${device.deviceNetworkId} 1 6 0", "delay 250",
        "st rattr 0x${device.deviceNetworkId} 1 2 0", "delay 250",
        "st rattr 0x${device.deviceNetworkId} 1 1 0", "delay 250",
        "st rattr 0x${device.deviceNetworkId} 1 0 0"
    ]
}

private Map parseCustomMessage(String description) {
	def result
	if (description?.startsWith('on/off: ')) {
    	if (description == 'on/off: 0')
    		result = createEvent(name: "switch", value: "off")
    	else if (description == 'on/off: 1')
    		result = createEvent(name: "switch", value: "on")
	}
    
    return result
}


private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}