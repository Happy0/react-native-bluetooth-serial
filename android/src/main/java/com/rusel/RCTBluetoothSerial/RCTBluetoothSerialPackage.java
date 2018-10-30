package com.rusel.RCTBluetoothSerial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

public class RCTBluetoothSerialPackage implements ReactPackage {
    static final String TAG = "BluetoothSerial";
    private final BluetoothSerialConfiguration configuration;

    public RCTBluetoothSerialPackage(BluetoothSerialConfiguration configuration) {
        super();

        this.configuration = configuration;
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();
        modules.add(new RCTBluetoothSerialModule(reactContext, configuration));
        return modules;
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}