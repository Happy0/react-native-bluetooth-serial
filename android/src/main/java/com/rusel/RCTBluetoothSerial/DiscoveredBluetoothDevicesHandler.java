package com.rusel.RCTBluetoothSerial;

import android.bluetooth.BluetoothDevice;

import java.util.List;

public interface DiscoveredBluetoothDevicesHandler {

    void onDiscovered(List<BluetoothDevice> devices);

    void onBluetoothDisabled();

    void onBluetoothNotSupported();

}
