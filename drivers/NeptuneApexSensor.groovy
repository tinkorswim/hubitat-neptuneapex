/**
 *  Neptune Apex Child Sensor Driver
 *  Author: tinkorswim
 *  Date: 2022-11-26
 *
 *  Copyright 2022 tinkorswim
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
 *  2022-11-26: Initial Development
 */

metadata {
  definition(name: 'Neptune Apex Sensor', namespace: 'tinkorswim', author: 'tinkorswim',
    importUrl: 'https://raw.githubusercontent.com/tinkorswim/hubitat-neptuneapex/main/drivers/NeptuneApexSensor.groovy') {
    capability 'Refresh'
    capability 'Sensor'
    }
    preferences {
      section('driver') {
        input name: 'debugEnable', type: 'bool', title: 'Enable debug logging', defaultValue: false
      }
    }
  attribute 'type', 'string'
  attribute 'unit', 'string'
  attribute 'value', 'number'
}

void updated() {
  if (debugEnable) log.debug "Neptune Apex Sensor ${device.name} updated....."
  refresh()
}

void installed() {
  if (debugEnable) log.debug "Neptune Apex Sensor ${device.name} installed....."
}

void updateAttribute( String name, Object value ) {
  if (device.currentValue(name) != value) {
    if ( debugEnable) log.debug("updating ${name} with ${value} was ${device.currentValue(name)}")
    sendEvent( name: name, value: value, isStateChanged: true )
  }
}

String did() {
  return device.getDeviceNetworkId().split( '\\|\\|' )[ 1 ]
}

void refresh() {
  log.debug "refreshing requested for device ${device.name}"
  parent?.refreshStatus()
}
