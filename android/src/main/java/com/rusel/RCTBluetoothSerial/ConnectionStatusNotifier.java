package com.rusel.RCTBluetoothSerial;

public class ConnectionStatusNotifier {

    private final RCTBluetoothSerialModule serialModule;

    public ConnectionStatusNotifier(RCTBluetoothSerialModule serialModule) {
        this.serialModule = serialModule;
    }

    public void onConnectionSuccess(String remoteAddress, boolean incoming) {
        serialModule.onConnectionSuccess(remoteAddress, "", incoming);
    }

    public void onConnectionFailure(String remoteAddress, String reason, boolean incoming) {
        serialModule.onConnectionFailed(remoteAddress, reason, incoming);
    }

    public void onDisconnect(String remoteAddress, String reason) {
        serialModule.onConnectionLost(remoteAddress, reason);
    }

}
