/**
 *  Neptune Apex Child Outlet Driver
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
  definition(name: 'Neptune Apex Outlet', namespace: 'tinkorswim', author: 'tinkorswim',
    importUrl: 'https://raw.githubusercontent.com/tinkorswim/hubitat-neptuneapex/main/drivers/NeptuneApexOutlet.groovy') {
    capability 'Switch'
    capability 'Refresh'
    capability 'Actuator'
    capability 'Sensor'
    capability 'PowerMeter'
    capability 'Outlet'
    }
    preferences {
      section('driver') {
        input name: 'debugEnable', type: 'bool', title: 'Enable debug logging', defaultValue: false
      }
    }
  command 'auto'
  attribute 'auto', 'string'
}

void updated() {
  log.info "Apex Outlet Driver ${device.name} updated....."
  refresh()
}

void installed() {
  log.info "Apex Outlet Driver ${device.name} installed....."
}

void updateAttribute( String name, Object value ) {
  if (device.currentValue(name) != value) {
    if ( debugEnable) log.debug("updating ${name} with ${value} was ${device.currentValue(name)}")
    sendEvent( name: name, value: value, isStateChanged: true )
  }
}

void auto() {
  parent?.updateOutputDevice(device.getDeviceNetworkId(), 'auto', did(), 'outlet', 'auto', true)
}

String did() {
  return device.getDeviceNetworkId().split( '\\|\\|' )[ 1 ]
}

void on() {
  parent?.updateOutputDevice(device.getDeviceNetworkId(), 'switch', did(), 'outlet', 'on')
}

void off() {
  parent?.updateOutputDevice(device.getDeviceNetworkId(), 'switch', did(), 'outlet', 'off')
}

void refresh() {
  log.debug "refreshing requested for device ${device.name}"
  parent?.refreshStatus()
}
