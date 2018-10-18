package com.rusel.RCTBluetoothSerial;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.rusel.RCTBluetoothSerial.control.DiscoveredDevicesHandler;
import com.rusel.RCTBluetoothSerial.control.MakeDeviceDiscoverableHandler;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;
import static com.rusel.RCTBluetoothSerial.RCTBluetoothSerialPackage.TAG;

@SuppressWarnings("unused")
public class RCTBluetoothSerialModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

    // Debugging
    private static final boolean D = true;

    // Trace: for verbose output (raw messages being sent and received, etc.)
    private static final boolean T = false;

    // Event names
    private static final String BT_ENABLED = "bluetoothEnabled";
    private static final String BT_DISABLED = "bluetoothDisabled";
    private static final String CONN_SUCCESS = "connectionSuccess";
    private static final String CONN_FAILED = "connectionFailed";
    private static final String CONN_LOST = "connectionLost";
    private static final String DEVICE_READ = "read";
    private static final String ERROR = "error";

    private static final String DEVICE_DISCOVERABLE = "deviceDiscoverable";
    private static final String DEVICE_NOT_DISCOVERABLE = "deviceNotDiscoverable";
    private static final String DEVICE_NOT_CONNECTABLE = "deviceCannotBeConnectedTo";

    // Codes for results from onActivityResult
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_PAIR_DEVICE = 2;
    private static final int REQUEST_MAKE_DISCOVERABLE = 3;

    // Members
    private BluetoothAdapter mBluetoothAdapter;
    private RCTBluetoothSerialService mBluetoothService;
    private ReactApplicationContext mReactContext;

    // Promises
    private Promise mEnabledPromise;
    private Promise mPairDevicePromise;

    private MakeDeviceDiscoverableHandler makeDeviceDiscoverableHandler = null;

    public RCTBluetoothSerialModule(ReactApplicationContext reactContext) {
        super(reactContext);

        if (D) Log.d(TAG, "Bluetooth module started");

        mReactContext = reactContext;

        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (mBluetoothService == null) {
            mBluetoothService = new RCTBluetoothSerialService(this);
        }

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            sendEvent(BT_ENABLED, null);
        } else {
            sendEvent(BT_DISABLED, null);
        }

        mReactContext.addActivityEventListener(this);
        mReactContext.addLifecycleEventListener(this);
        registerBluetoothStateReceiver();
    }

    @Override
    public String getName() {
        return "RCTBluetoothSerial";
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
        if (D) Log.d(TAG, "On activity result request: " + requestCode + ", result: " + resultCode);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (D) Log.d(TAG, "User enabled Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.resolve(true);
                }
            } else {
                if (D) Log.d(TAG, "User did *NOT* enable Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.reject(new Exception("User did not enable Bluetooth"));
                }
            }
            mEnabledPromise = null;
        }
        else if (requestCode == REQUEST_MAKE_DISCOVERABLE) {

            // For some reason, the result code is the length of time the user permitted discoverability
            // for...
            if (resultCode > 0) {
                if (D) Log.d(TAG, "User allowed the device to be discovered.");

                if (makeDeviceDiscoverableHandler != null) {
                    long currentTime = System.currentTimeMillis();
                    long discoverableForMillis = resultCode * 1000;
                    long estimatedDiscoverableUntil = currentTime + discoverableForMillis;

                    makeDeviceDiscoverableHandler.handleSuccess(estimatedDiscoverableUntil);
                }

            } else {
                if (D) Log.d(TAG, "User did not allow the device to be discovered") ;
                if (D) Log.d(TAG, "Result code was: " + resultCode);

                if (makeDeviceDiscoverableHandler != null)
                    makeDeviceDiscoverableHandler.handleUserDidNotAllow();
            }

            // Will be made non-null again on future discoverability requests
            makeDeviceDiscoverableHandler = null;
        }
        else if (requestCode == REQUEST_PAIR_DEVICE) {
            if (resultCode == Activity.RESULT_OK) {
                if (D) Log.d(TAG, "Pairing ok");
            } else {
                if (D) Log.d(TAG, "Pairing failed");
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (D) Log.d(TAG, "On new intent");
    }


    @Override
    public void onHostResume() {
        if (D) Log.d(TAG, "Host resume");
    }

    @Override
    public void onHostPause() {
        if (D) Log.d(TAG, "Host pause");
    }

    @Override
    public void onHostDestroy() {
        if (D) Log.d(TAG, "Host destroy");
    }

    @Override
    public void onCatalystInstanceDestroy() {
        if (D) Log.d(TAG, "Catalyst instance destroyed");
        super.onCatalystInstanceDestroy();
    }

    /*******************************/
    /** Methods Available from JS **/
    /*******************************/

    /*************************************/
    /** Bluetooth state related methods **/

    @ReactMethod
    /**
     * Request user to enable bluetooth
     */
    public void requestEnable(Promise promise) {
        // If bluetooth is already enabled resolve promise immediately
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            promise.resolve(true);
        // Start new intent if bluetooth is note enabled
        } else {
            Activity activity = getCurrentActivity();
            mEnabledPromise = promise;
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (activity != null) {
                activity.startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
            } else {
                Exception e = new Exception("Cannot start activity");
                Log.e(TAG, "Cannot start activity", e);
                mEnabledPromise.reject(e);
                mEnabledPromise = null;
                onError(e);
            }
        }
    }

    @ReactMethod
    public void connect(String address) {
        mBluetoothService.connect(address);
    }

    /**
     * Make the device discoverable for connection and pairing by other android devices
     * for the given amount of time in seconds. The user will be shown a dialog box to
     * confirm where they want to make the device discoverable.
     *
     * The handler handles the error / success of this request
     */
    public synchronized void makeDeviceDiscoverable(int timeSeconds, MakeDeviceDiscoverableHandler handler) {

        if (D) Log.d(TAG, "Asking to make device discoverable for "  + timeSeconds + " seconds");

        Activity currentActivity = getCurrentActivity();

        if (makeDeviceDiscoverableHandler != null) {
            handler.handleAlreadyInProgress();
        } else if (currentActivity == null) {
            if (D) Log.d(TAG, "Cannot make device discoverable because activity is null");
            handler.handleAppNotAtFront();
        } else {
            // Make the device discoverable for a limited duration
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, timeSeconds);

            this.makeDeviceDiscoverableHandler = handler;
            currentActivity.startActivityForResult(discoverableIntent, REQUEST_MAKE_DISCOVERABLE);
        }

    }

    public void listenForIncomingConnections(String serviceName, String UUID) {
        UUID sspUuid = java.util.UUID.fromString(UUID);
        boolean started = mBluetoothService.startServerSocket(serviceName, sspUuid);
    }

    public void stopListeningForNewConnections(Promise promise) {
        try {
            mBluetoothService.stopServerSocket();
            promise.resolve(true);
        } catch (IOException e) {
            promise.reject(new Exception(e.getMessage()));
        }
    }

    /**
     * Changes the device name. Requires the 'bluetooth admin' permission.
     * @param deviceName the device name
     */
    @ReactMethod
    public void changeDeviceName(String deviceName) {
        mBluetoothAdapter.setName(deviceName);
    }

    /**
     * Get the device name.
     * @return
     */
    @ReactMethod
    public String getDeviceName() {
        return mBluetoothAdapter.getName();
    }

    @ReactMethod
    /**
     * Enable bluetooth
     */
    public void enable(Promise promise) {
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        promise.resolve(true);
    }

    @ReactMethod
    /**
     * Disable bluetooth
     */
    public void disable(Promise promise) {
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
        promise.resolve(true);
    }

    /**
     * Check if bluetooth is enabled
     */
    public boolean isEnabled() {
        if (mBluetoothAdapter != null) {
            return mBluetoothAdapter.isEnabled();
        } else {
            return false;
        }
    }

    /**
     * Discover unpaired bluetooth devices
     */
    public void discoverNearbyDevices(DiscoveredDevicesHandler handler) {
        if (D) Log.d(TAG, "Discover nearby called");

        // todo: handle error?
        registerBluetoothDeviceDiscoveryReceiver(handler);

        if (mBluetoothAdapter == null ) {
            handler.onBluetoothNotSupported();
        } else if (!mBluetoothAdapter.isEnabled()) {
            handler.onBluetoothDisabled();
        }

        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.startDiscovery();
        }
    }

    /**
     * Cancel discovery
     */
    public void cancelDiscovery(final Promise promise) {
        if (D) Log.d(TAG, "Cancel discovery called");

        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
        }
        promise.resolve(true);
    }


    @ReactMethod
    /**
     * Pair device
     */
    public void pairDevice(String id, Promise promise) {
        if (D) Log.d(TAG, "Pair device: " + id);

        if (mBluetoothAdapter != null) {
            mPairDevicePromise = promise;
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(id);
            if (device != null) {
                pairDevice(device);
            } else {
                mPairDevicePromise.reject(new Exception("Could not pair device " + id));
                mPairDevicePromise = null;
            }
        } else {
            promise.resolve(false);
        }
    }

    @ReactMethod
    /**
     * Unpair device
     */
    public void unpairDevice(String id, Promise promise) {
        if (D) Log.d(TAG, "Unpair device: " + id);

        if (mBluetoothAdapter != null) {
            mPairDevicePromise = promise;
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(id);
            if (device != null) {
                unpairDevice(device);
            } else {
                mPairDevicePromise.reject(new Exception("Could not unpair device " + id));
                mPairDevicePromise = null;
            }
        } else {
            promise.resolve(false);
        }
    }

    public void showYesNoDialog(final String message, final DialogInterface.OnClickListener dialogClickListener) {
        if (D) Log.d(TAG, "Showing yes/no dialog for incoming connection");

        getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getCurrentActivity());
                builder.setMessage(message).setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });


    }


    @ReactMethod
    /**
     * Set bluetooth adapter name
     */
    public void setAdapterName(String newName, Promise promise) {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.setName(newName);
        }
        promise.resolve(true);
    }

    /****************************************/
    /** Methods available to whole package **/
    /****************************************/

    /**
     * Handle connection success
     * @param msg Additional message
     */
    void onConnectionSuccess(String address, String msg, boolean isIncoming) {
        this.mBluetoothService.getControlSocket().sendConnectedEvent(address, isIncoming);
    }

    /**
     * handle connection failure
     * @param msg Additional message
     */
    void onConnectionFailed(String address, String msg, boolean isIncoming) {
        this.mBluetoothService.getControlSocket().sendConnectionFailureEvent(address, msg, isIncoming);
    }

    /**
     * Handle lost connection
     * @param msg Message
     */
    void onConnectionLost (String address, String msg) {
        this.mBluetoothService.getControlSocket().sendDisconnectionEvent(address, msg);
    }

    /**
     * Handle error
     * @param e Exception
     */
    void onError (Exception e) {

    }


    /*********************/
    /** Private methods **/
    /*********************/

    /**
     * Check if is api level 19 or above
     * @return is above api level 19
     */
    private boolean isKitKatOrAbove () {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * Send event to javascript
     * @param eventName Name of the event
     * @param params Additional params
     */
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        if (mReactContext.hasActiveCatalystInstance()) {
            if (T) Log.v(TAG, "Sending event: " + eventName);
            mReactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        } else {
            Log.d(TAG, "Cannot send event as there is no active catalyst instance");
        }
    }

    /**
     * Pair device before kitkat
     * @param device Device
     */
    private void pairDevice(BluetoothDevice device) {
        try {
            if (D) Log.d(TAG, "Start Pairing...");
            Method m = device.getClass().getMethod("createBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            registerDevicePairingReceiver(device.getAddress(), BluetoothDevice.BOND_BONDED);
            if (D) Log.d(TAG, "Pairing finished.");
        } catch (Exception e) {
            Log.e(TAG, "Cannot pair device", e);
            if (mPairDevicePromise != null) {
                mPairDevicePromise.reject(e);
                mPairDevicePromise = null;
            }
            onError(e);
        }
    }

    /**
     * Unpair device
     * @param device Device
     */
    private void unpairDevice(BluetoothDevice device) {
        try {
            if (D) Log.d(TAG, "Start Unpairing...");
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            registerDevicePairingReceiver(device.getAddress(), BluetoothDevice.BOND_NONE);
        } catch (Exception e) {
            Log.e(TAG, "Cannot unpair device", e);
            if (mPairDevicePromise != null) {
                mPairDevicePromise.reject(e);
                mPairDevicePromise = null;
            }
            onError(e);
        }
    }

    /**
     * Register receiver for device pairing
     * @param deviceId Id of device
     * @param requiredState State that we require
     */
    private void registerDevicePairingReceiver(final String deviceId, final int requiredState) {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        final BroadcastReceiver devicePairingReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    final int prevState	= intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                    if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                        if (D) Log.d(TAG, "Device paired");
                        if (mPairDevicePromise != null) {
                            mPairDevicePromise.resolve(true);
                            mPairDevicePromise = null;
                        }
                        try {
                            mReactContext.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Cannot unregister receiver", e);
                            onError(e);
                        }
                    } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                        if (D) Log.d(TAG, "Device unpaired");
                        if (mPairDevicePromise != null) {
                            mPairDevicePromise.resolve(true);
                            mPairDevicePromise = null;
                        }
                        try {
                            mReactContext.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Cannot unregister receiver", e);
                            onError(e);
                        }
                    }

                }
            }
        };

        mReactContext.registerReceiver(devicePairingReceiver, intentFilter);
    }

    /**
     * Register receiver for bluetooth device discovery
     * @param handler
     */
    private void registerBluetoothDeviceDiscoveryReceiver(final DiscoveredDevicesHandler handler) {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        final BroadcastReceiver deviceDiscoveryReceiver = new BroadcastReceiver() {
            private List<BluetoothDevice> devices = new ArrayList<>();
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (D) Log.d(TAG, "onReceive called");

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    devices.add(device);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    if (D) Log.d(TAG, "Discovery finished");

                    handler.onDiscovered(devices);

                    try {
                        mReactContext.unregisterReceiver(this);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to unregister receiver", e);
                        onError(e);
                    }
                }
            }
        };

        mReactContext.registerReceiver(deviceDiscoveryReceiver, intentFilter);
    }

    /**
     * Register receiver for bluetooth state change
     */
    private void registerBluetoothStateReceiver() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    switch (state) {
                        case BluetoothAdapter.STATE_OFF:
                            if (D) Log.d(TAG, "Bluetooth was disabled");
                            sendEvent(BT_DISABLED, null);
                            break;
                        case BluetoothAdapter.STATE_ON:
                            if (D) Log.d(TAG, "Bluetooth was enabled");
                            sendEvent(BT_ENABLED, null);
                            break;
                    }
                }
            }
        };

        mReactContext.registerReceiver(bluetoothStateReceiver, intentFilter);
    }
}
