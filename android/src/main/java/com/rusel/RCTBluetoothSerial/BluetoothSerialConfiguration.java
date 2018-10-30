package com.rusel.RCTBluetoothSerial;

public class BluetoothSerialConfiguration {

    private final String socketFolderPath;
    private String outgoingSocketPath;
    private String incomingSocketPath;
    private String controlSocketPath;

    public BluetoothSerialConfiguration(String socketFolderPath) {
        this.socketFolderPath = socketFolderPath;
    }

    public String getSocketFolderPath() {
        return socketFolderPath;
    }



    private String getUnixSocketPath(String socketName) {
        return socketFolderPath + "/" + socketName;
    }

    public String getOutgoingSocketPath() {
        return getUnixSocketPath("manyverse_bt_outgoing.sock");
    }

    public String getIncomingSocketPath() {
        return getUnixSocketPath("manyverse_bt_incoming.sock");
    }

    public String getControlSocketPath() {
        return getUnixSocketPath("manyverse_bt_control.sock");
    }
}
