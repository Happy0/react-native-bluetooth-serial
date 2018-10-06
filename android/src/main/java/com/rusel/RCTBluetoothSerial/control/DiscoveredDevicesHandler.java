package com.rusel.RCTBluetoothSerial.control;

import android.bluetooth.BluetoothDevice;

import com.rusel.RCTBluetoothSerial.DiscoveredBluetoothDevicesHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class DiscoveredDevicesHandler implements DiscoveredBluetoothDevicesHandler {

    private final BlockingQueue<BluetoothControlCommand> commandResponseBuffer;

    public DiscoveredDevicesHandler(BlockingQueue<BluetoothControlCommand> commandResponseBuffer) {
        this.commandResponseBuffer = commandResponseBuffer;
    }

    public void onDiscovered(List<BluetoothDevice> devices) {

        Map<String, Object> properties = new HashMap<>();

        List<BluetoothDeviceProperties> deviceProperties = new ArrayList<>();

        for (BluetoothDevice bluetoothDevice: devices) {
            BluetoothDeviceProperties deviceProps = getDeviceProperties(bluetoothDevice);
            deviceProperties.add(deviceProps);
        }

        properties.put("devices", deviceProperties);

        BluetoothControlCommand bluetoothControlCommand =
                new BluetoothControlCommand("discovered", properties);

        commandResponseBuffer.add(bluetoothControlCommand);
    }

    @Override
    public void onErrorDiscovering(String exception) {
        // TODO
    }

    public BluetoothDeviceProperties getDeviceProperties(BluetoothDevice device) {

        // Unlikely, but since we're using pull-json-doubleline on the other end, a bluetooth device
        // name with new lines in it could make things crash

        String deviceName = device.getName() == null ? "" : device.getName();

        String name = deviceName.replace("\n", "");

        return new BluetoothDeviceProperties(device.getAddress(), name);
    }


}
