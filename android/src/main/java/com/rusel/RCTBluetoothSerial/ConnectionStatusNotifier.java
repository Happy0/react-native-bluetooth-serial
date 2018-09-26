package com.rusel.RCTBluetoothSerial;

public class ConnectionStatusNotifier {

    private final RCTBluetoothSerialModule serialModule;

    public ConnectionStatusNotifier(RCTBluetoothSerialModule serialModule) {
        this.serialModule = serialModule;
    }

    public void onConnectionSuccess(String remoteAddress, boolean incoming) {
        serialModule.onConnectionSuccess(remoteAddress, "", incoming);
    }

    public void onConnectionFailure(String remoteAddress, String reason) {
        serialModule.onConnectionFailed(remoteAddress, reason);
    }

    public void onDisconnect(String remoteAddress, String reason) {
        serialModule.onConnectionLost(remoteAddress, reason);
    }

}
