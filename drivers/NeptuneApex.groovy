/**
 *  Neptune Apex Driver
 *  Author: tinkorswim
 *  Date: 2023-10-08
 *
 *  Copyright 2023 tinkorswim
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

import groovy.transform.Field

@Field static Map DRIVER_TEMPERATURE =          [namespace:'hubitat',     name:'Generic Component Temperature Sensor']
@Field static Map DRIVER_SWITCH =               [namespace:'hubitat',     name:'Generic Component Switch']
@Field static Map DRIVER_WATER_SENSOR =         [namespace:'hubitat',     name:'Generic Component Water Sensor']
@Field static Map DRIVER_APEX_GENERIC_SENSOR =  [namespace:'tinkorswim',  name:'Neptune Apex Sensor']

metadata {
  definition(name: 'Neptune Apex',
    namespace: 'tinkorswim',
    author: 'tinkorswim',
    importUrl: 'https://raw.githubusercontent.com/tinkorswim/hubitat-neptuneapex/main/drivers/NeptuneApex.groovy') {
    capability 'Actuator'
    capability 'Sensor'
    capability 'TemperatureMeasurement'
    capability 'Refresh'
    capability 'Alarm'
    }

  attribute 'pH', 'number'
  attribute 'feedCycle', 'number'
  attribute 'salinity', 'number'
  attribute 'ORP', 'number'

  command 'cleanUpChildren'
  command 'feedA'
  command 'feedB'
  command 'feedC'
  command 'feedD'
  command 'cancelFeed'
  command 'clearAttributes'
  command 'syncDevices'
}

preferences {
  section('Apex Info') {
    input name: 'apexHost',
      type: 'text', title: 'Apex Hostname',
      defaultValue: '', required: true,
      displayDuringSetup:true
    input name: 'apexUsername', type: 'text', title: 'Username', defaultValue: '', required: true
    input name: 'apexPassword', type: 'password', title: 'Password', defaultValue: '', required: true
    input name: 'pollFrequencySecs', type: 'number', title: 'Poll Frequency',
      description:'How often in seconds to poll the Apex. min 5, max 3540', defaultValue: 300, range: '5..3540'
    input name: 'devicePrefix', type: 'text', title: 'Device Display Name Prefix', description:'Prefix all child devices with this name', defaultValue: '', required: false
    input name: 'devices',
      type: 'text', title: 'Devices',
      description: 'Only add these child devices (by did, ex.. 2_8, 2_1) leave blank for all devices',
      defaultValue: ''
    input name: 'readOnly', type: 'bool', title: 'Read Only Mode',
      description:"This device and it's children can't control the apex", defaultValue: true
    input name: 'syncApexNames', type: 'bool', title: 'Auto-sync Apex device names.  Input and Output device Names changes on the apex will change the child device', defaultValue: true
    input name: 'debugEnable', type: 'bool', title: 'Enable debug logging', defaultValue: false
  }
}

void logsOff() {
  log.warn 'debug logging disabled...'
  device.updateSetting('debugEnable', [value: 'false', type: 'bool'])
}

void installed() {
  log.info("New Apex device installed...${device.displayName}(${device.name})")
  if (!devicePrefix) {
    device.updateSetting('devicePrefix', "${device.name} ")
  }
  updated()
}

void updated() {
  log.info "${device.displayName}(${device.name}) Preferences Updated"
  setState('whitelist', devices?.trim() ? devices.split(',').toList() : [])
  unschedule(refreshStatus)
  schedule(getCronExpression(), refreshStatus)
  if (logEnable) {
    runIn(1800, logsOff)
  }
  //lets try to populate children as soon as we have a host
  if (apexHost && !state.devices) {
    log.info 'Apex Devices not found, performing initial sync now'
    syncDevices()
  }
}

String getCronExpression() {
  cron = '0 0/5 * * * ? *'
  if (pollFrequencySecs < 60) {
    cron = "0/${pollFrequencySecs} * * * * ? *"
  }
  else {
    minutes = Math.min(java.util.concurrent.TimeUnit.SECONDS.toMinutes(pollFrequencySecs), 59)
    cron = "0 0/${minutes} * ? * *"
  }
  if (debugEnable) log.debug("scheduling with following expression ${cron}")
  return cron
}

void cleanUpChildren() {
  log.info("Cleaning up ${getChildDevices().size()} child devices")
  getChildDevices().each { child ->
    if (debugEnable) log.debug("cleaning up child ${child.name}")
    deleteChildDevice(child.deviceNetworkId)
  }
}

void syncDevices() {
  log.info("Syncing devices with apex at ${apexHost}")
  pollStatusAsync('handleStatusResponse', [create:true])
}

void pollStatusAsync(String handler, Map data=[:]) {
  Map params = [uri: "http://${apexHost}/rest/status",
    contentType: 'application/json',
    headers: [Authorization:authHeader()]]
  asynchttpGet(handler, params, data)
}

void startFeedCycle(int feedCycle) {
  Map params = [
    uri: "http://${apexHost}/rest/status/feed/${feedCycle}",
    contentType: 'application/json',
    headers: [Authorization:authHeader()],
    body: [name: feedCycle, active: 1, errorCode: 0, errorMessage: '']]

  if (readOnly != true) {
    asynchttpPut('handleFeedCycleResponse', params, [ feedCycle:feedCycle])
  }
  else {
    if (debugEnable) log.debug("can't start feed cycle Apex is in read-only mode")
  }
}
void handleFeedCycleResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
  switch ( resp.getStatus() ) {
    case 200:
      json = parseJson( resp.data )
      feedCycle = json.name
      updateAttribute('feedCycle', feedCycle)
      log.info("feed cycle ${feedCycle == 0 ? 'disabled' : 'enabled'}")
      break
    default:
      log.error("error setting feed cycle:${data.feedCycle}")
  }
}
void feedA() {
  startFeedCycle(1)
}

void feedB() {
  startFeedCycle(2)
}

void feedC() {
  startFeedCycle(3)
}

void feedD() {
  startFeedCycle(4)
}

void cancelFeed() {
  startFeedCycle(0)
}

void handleChildDevices(Map deviceInfo, boolean create) {
  outputs = deviceInfo.outputs.findAll { device -> includeDevice(device) }
  outputs.each { output ->
    if (output.type == 'outlet' || output.type == '24v' || output.type == 'virtual') {
      child = loadChildOutputDevice(output, create)
      if (child) {
        child.updateAttribute('switch', output.status[0].toLowerCase().contains('on') ? 'on' : 'off')
        child.updateAttribute('auto', output.status[0].toLowerCase().contains('a') ? 'true' : 'false')
      }
    }
  }

  inputs = deviceInfo.inputs.findAll { device -> includeDevice(device) }
  inputs.each { apexInput ->
    //skipping Amps and volts right now
    if (['Amps', 'volts'].contains(apexInput.type)) {
      return
    }
    
    switch (apexInput.type) {
      case 'pwr':
        //power shows up as an input, we will map it to the outlet and put there
        deviceName = apexInput.name[0..apexInput.name.length() - 2]
        childEnergy = getChildDevice(getChildDeviceNetworkId(state.devices[deviceName]))
        childEnergy?.updateAttribute('power', apexInput.value)
        break
      default:
        childDevice = loadChildInputDevice(apexInput, create)
        if (childDevice) {
          if (childDevice.hasCapability('WaterSensor')) {
            String waterState = apexInput.value == 0 ? 'dry' : 'wet'
            String descriptionText = "${device.displayName} water ${waterState}"
            childDevice?.sendEvent(name: 'water', value: waterState, descriptionText: descriptionText)
          }
          else if ( childDevice.hasCapability('Switch')) {
            value = apexInput.value == 0 ? 'off' : 'on'
            childDevice?.sendEvent(name : 'switch',
              value : value,
              descriptionText : "Changing switch to ${value}")
          }
          else if ( childDevice.hasCapability('TemperatureMeasurement')) {
            childDevice?.sendEvent(name : 'temperature',
              value : apexInput.value,
              descriptionText : "Setting temperature value to ${apexInput.value}")
          }
          else {
            childDevice?.sendEvent(name : 'value',
              value : apexInput.value,
              descriptionText : "Setting sensor value to ${apexInput.value}")
          }
        }
    }
  }
}

Object loadChildOutputDevice(Object output, boolean create) {
  child = getChildDevice(getChildDeviceNetworkId(output.did))
  if (child == null && create == true) {
    log.info("Creating new output device for ${output.name}")
    child = addChildDevice('tinkorswim', 'Neptune Apex Outlet',
          getChildDeviceNetworkId(output.did), [name:output.name, label:calculateLabel(output)])
  }
  return child
}

Object loadChildInputDevice(Object apexInput, boolean create) {
  childDevice = getChildDevice(getChildDeviceNetworkId(apexInput.did))
  if (childDevice == null && create == true) {
    log.info("Creating new input device for ${apexInput.name}")
    childDevice = newInputChild(apexInput)
  }
  return childDevice
}

String calculateLabel(Object device) {
  return "${devicePrefix ?: ''}${device.name}"
}

Object newInputChild(Object apexInput) {
  Map deviceTypeMap = [
    'Temp':DRIVER_TEMPERATURE,
    'pH':DRIVER_APEX_GENERIC_SENSOR,
    'digital':DRIVER_SWITCH
  ]
  deviceModule = getModuleTypeForInput(apexInput.did)
  driver = DRIVER_APEX_GENERIC_SENSOR
  switch (deviceModule) {
    case 'FMM':
      driver = (apexInput.type == 'in' ? DRIVER_APEX_GENERIC_SENSOR : DRIVER_WATER_SENSOR)
      break
    default:
      driver = deviceTypeMap[apexInput.type] ?: DRIVER_APEX_GENERIC_SENSOR
  }
  if (debugEnable) log.debug("creating new device - name[${apexInput.name}] did[(${apexInput.did})] module[${deviceModule}] type[${apexInput.type}] driver[${driver}] ")
  childDevice = addChildDevice(driver.namespace, driver.name,
      getChildDeviceNetworkId(apexInput.did), [name:apexInput.name, label:calculateLabel(apexInput)])
  childDevice?.sendEvent(name : 'type', value : apexInput.type,
          descriptionText : "setting type to ${apexInput.type}")
  return childDevice
}

boolean includeDevice(Map device) {
  if (state.whitelist.size() == 0) {
    return true
  }
  return state.whitelist.contains(device.did)
}

String getChildDeviceNetworkId(String did) {
  return "${device.deviceNetworkId}||${did}"
}

String getModuleTypeForInput(String did) {
  module = did.split('_')[0]
  return state.modules.get(module)
}

void refresh() {
  if (debugEnable) log.debug('Refresh explicit call')
  refreshStatus()
}

void clearAttributes() {
  device.getSupportedAttributes().each { attribute ->
    if (debugEnable) log.debug("clearing attribute ${attribute}")
    updateAttribute(attribute.name, 0)
  }
}

void refreshStatus() {
  if (debugEnable) log.debug('Refreshing Apex device status')
  pollStatusAsync('handleStatusResponse')
  updateLastRefresh()
}

String authHeader() {
  return 'Basic ' + (apexUsername + ':' + apexPassword).bytes.encodeBase64()
}

/* groovylint-disable-next-line ParameterCount */
void updateOutputDevice(String deviceNetworkId,
  String attribute,
  String did,
  String type,
  Object value,
  Object hubitatDeviceValue=null) {
  Map params = [
    uri: "http://${apexHost}/rest/status/outputs/${did}",
    contentType: 'application/json',
    headers: [Authorization:authHeader()],
    body: [status:[value, '', 'OK', ''],
    type:type]]

  if (readOnly != true) {
    asynchttpPut('handleDeviceOutputResponse', params,
        [ deviceNetworkId:deviceNetworkId, did: did,
        attribute:attribute, value: hubitatDeviceValue ?: value])
  }
  else {
    log.warn("device ${did} is in read-only mode")
  }
  }

void handleDeviceOutputResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
  switch ( resp.getStatus() ) {
    case 200:
      json = parseJson( resp.data )
      if (device.deviceNetworkId == data.deviceNetworkId) {
        updateAttribute(data.attribute, data.value)
      }
      else {
        getChildDevice(data.deviceNetworkId).updateAttribute(data.attribute, data.value)
      }
      runInMillis(1000, refreshStatus)
      break
    default:
      log.error("error switching output status for did:${data.did}")
  }
}

void updateAttribute( String name, Object value ) {
  if (device.currentValue(name) != value) {
    if (debugEnable) log.debug("updating ${name} with ${value} was ${device.currentValue(name)}")
    //TODO: what about map or array
    String newValue = value.toString().isNumber() ? value : value.toLowerCase()
    sendEvent( name: name, value: newValue, isStateChanged: true )
  }
}

void setState( String name, Object value ) {
  if ( state."${name}" != value ) {
    if (debugEnable) log.debug( "updating state: ${name} = ${value}")
    state."${name}" = value
  }
}

void handleStatusResponse(hubitat.scheduling.AsyncResponse resp, Map data) {
  switch (resp.getStatus()) {
    case 200:
      deviceStatus = parseJson(resp.data)
      updateHardware(deviceStatus)
      updateBaseAttributes(deviceStatus)
      handleChildDevices(deviceStatus, data['create'] ?: false)
      break
    default:
      if (debugEnable) log.debug("error executing Apex command: ${ resp.status } with data ${data}")
  }
}

void updateBaseAttributes(Map deviceStatus) {
  //mapping apex attribute to device capability
  ['base_Temp':'temperature', 'base_pH':'pH', 'base_Cond':'salinity', 'base_ORP':'ORP'].each { k, v ->
    value = deviceStatus.inputs.find { input -> input.did == k }
    if (value) {
      updateAttribute(v, value.value)
    }
  }
  ['1_2':'alarm'].each { k, v ->
    value = deviceStatus.outputs.find { input -> input.did == k }
    if (value) {
      updateAttribute(v, value.status[0].toLowerCase().contains('on') ? 'on' : 'off')
    }
  }
  feedCycle = deviceStatus.feed.name == 256 ? 0 : deviceStatus.feed.name
  updateAttribute('feedCycle', feedCycle)
}

void  siren() {
  updateOutputDevice(device.deviceNetworkId, 'alarm', '1_2', 'alert', 'ON')
}

void off() {
  updateOutputDevice(device.deviceNetworkId, 'alarm', '1_2', 'alert', 'AUTO')
}

void strobe() {
  log.warn('strobe is not yet implemented')
}

void updateHardware(Map deviceStatus) {
  modules = deviceStatus.modules.collectEntries { module -> [module.abaddr.toString(), module.hwtype] }
  modules['base'] = 'base'
  outputs = deviceStatus.outputs.collectEntries { output -> [output.name, output.did] }
  inputs = deviceStatus.inputs.collectEntries { input -> [input.name, input.did] }
  setState('modules', modules)
  setState('type', deviceStatus.system.type)
  setState('serial', deviceStatus.system.serial)
  setState('hostname', deviceStatus.system.hostname)
  setState('software', deviceStatus.system.software)
  setState('devices', outputs + (inputs))

  if ( syncApexNames ) {
    syncApexNames(deviceStatus, false)
  }
}

void updateLastRefresh() {
  setState('lastRefresh', new Date().format('YYYY-MM-dd \n HH:mm:ss', location.timeZone) )
}

void syncApexNames(Map deviceStatus, boolean create) {
  (deviceStatus.outputs).each { device ->
      child = loadChildOutputDevice(device, create)
      updateChildName(device, child)
  }
  (deviceStatus.inputs).each { device ->
      child = loadChildInputDevice(device, create)
      updateChildName(device, child)
  }
}

void updateChildName(Object device, Object child) {
  if (child != null && (device.name != child.name)) {
    log.info("Apex name sync for did:[${device.did}] new:[${device.name}] was:[${child.name}]")
    //Update the label if its the same as name, otherwise lets leave it alone.
    if (child.label == calculateLabel(child)) {
      child.label = calculateLabel(device)
    }
    child.name = device.name
  }
}

void componentRefresh(Object component) {
  if (debugEnable) log.debug("component refresh called for ${component}" )
}
