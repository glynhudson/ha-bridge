package com.bwssystems.HABridge.plugins.hue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bwssystems.HABridge.BridgeSettingsDescriptor;
import com.bwssystems.HABridge.Home;
import com.bwssystems.HABridge.NamedIP;
import com.bwssystems.HABridge.api.CallItem;
import com.bwssystems.HABridge.api.hue.DeviceResponse;
import com.bwssystems.HABridge.api.hue.HueApiResponse;
import com.bwssystems.HABridge.dao.DeviceDescriptor;
import com.bwssystems.HABridge.hue.MultiCommandUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class HueHome implements Home {
    private static final Logger log = LoggerFactory.getLogger(HueHome.class);
	private Map<String, HueInfo> hues;
	private Boolean validHue;
	private Gson aGsonHandler;
	
	public HueHome(BridgeSettingsDescriptor bridgeSettings) {
		super();
		createHome(bridgeSettings);
	}

	@Override
	public Object getItems(String type) {
		log.debug("consolidating devices for hues");
		if(!validHue)
			return null;
		Iterator<String> keys = hues.keySet().iterator();
		ArrayList<HueDevice> deviceList = new ArrayList<HueDevice>();
		while(keys.hasNext()) {
			String key = keys.next();
			HueApiResponse theResponse = hues.get(key).getHueApiResponse();
			if(theResponse != null) {
				Map<String, DeviceResponse> theDevices = theResponse.getLights();
				if(theDevices != null) {
					Iterator<String> deviceKeys = theDevices.keySet().iterator();
					while(deviceKeys.hasNext()) {
						String theDeviceKey = deviceKeys.next();
						HueDevice aNewHueDevice = new HueDevice();
						aNewHueDevice.setDevice(theDevices.get(theDeviceKey));
						aNewHueDevice.setHuedeviceid(theDeviceKey);
						aNewHueDevice.setHueaddress(hues.get(key).getHueAddress().getIp());
						aNewHueDevice.setHuename(key);
						deviceList.add(aNewHueDevice);
					}
				}
				else {
					deviceList = null;
					break;
				}
			}
			else
				log.warn("Cannot get lights for Hue with name: " + key);
		}
		return deviceList;
	}

	public DeviceResponse getHueDeviceInfo(HueDeviceIdentifier deviceId, DeviceDescriptor device) {
		if(!validHue)
			return null;
		DeviceResponse deviceResponse = null;
		HueInfo aHueInfo = hues.get(device.getTargetDevice());
		deviceResponse = aHueInfo.getHueDeviceInfo(deviceId.getDeviceId(), device);
		return deviceResponse;
	}
	
	@Override
	public String deviceHandler(CallItem anItem, MultiCommandUtil aMultiUtil, String lightId, int intensity,
			Integer targetBri,Integer targetBriInc, DeviceDescriptor device, String body) {
		if(!validHue)
			return null;
		String responseString = null;
		HueDeviceIdentifier deviceId = aGsonHandler.fromJson(anItem.getItem(), HueDeviceIdentifier.class);
		if(deviceId.getHueName() == null || deviceId.getHueName().isEmpty())
			deviceId.setHueName(device.getTargetDevice());
		
		HueInfo theHue = hues.get(deviceId.getHueName());

		// make call
		responseString = theHue.changeState(deviceId, lightId, body);

		return responseString;
	}

	@Override
	public Home createHome(BridgeSettingsDescriptor bridgeSettings) {
		validHue = bridgeSettings.isValidHue();
		log.info("Hue passthru Home created." + (validHue ? "" : " No Hue passtrhu systems configured."));
		if(validHue) {
			hues = new HashMap<String, HueInfo>();
			Iterator<NamedIP> theList = bridgeSettings.getHueaddress().getDevices().iterator();
			while(theList.hasNext()) {
				NamedIP aHue = theList.next();
	      		hues.put(aHue.getName(), new HueInfo(aHue));
			}
			aGsonHandler = new GsonBuilder().create();
		}
		return this;
	}

	@Override
	public void closeHome() {
		if(!validHue)
			return;
		Iterator<String> keys = hues.keySet().iterator();
		while(keys.hasNext()) {
			String key = keys.next();
			hues.get(key).closeHue();;
		}
	}
}