package com.rusel.RCTBluetoothSerial;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class UnixSocketBridge {


    private final String socketOutgoingPath;
    private final String socketIncomingPath;
    private final ConnectionStatusNotifier connectionStatusNotifier;
    private final BluetoothAdapter bluetoothAdapter;
    private final UUID serviceUUID;

    public UnixSocketBridge(String socketOutgoingPath,
                            String socketIncomingPath,
                            UUID serviceUUID,
                            ConnectionStatusNotifier notifier,
                            BluetoothAdapter bluetoothAdapter) {
        this.socketOutgoingPath = socketOutgoingPath;
        this.socketIncomingPath = socketIncomingPath;
        this.serviceUUID = serviceUUID;
        this.connectionStatusNotifier = notifier;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void createIncomingServerConnection(final BluetoothSocket bluetoothSocket) {

        LocalSocket localSocket = new LocalSocket();
        LocalSocketAddress localSocketAddress = new LocalSocketAddress(this.socketIncomingPath);

        try {
            localSocket.connect(localSocketAddress);

            Runnable reader = readFromBluetoothAndSendToSocket(localSocket, bluetoothSocket);
            Runnable writer = readFromSocketAndSendToBluetooth(localSocket, bluetoothSocket);

            Thread thread = new Thread(reader);
            Thread thread2 = new Thread(writer);

            thread.start();
            thread2.start();

            connectionStatusNotifier.onConnectionSuccess(bluetoothSocket.getRemoteDevice().getAddress(), true);

        } catch (IOException e) {
            connectionStatusNotifier.onConnectionFailure(
                    bluetoothSocket.getRemoteDevice().getAddress(),
                    e.getMessage()
            );
        }

    }

    public void listenForOutgoingConnections() throws IOException {

        final LocalServerSocket localServerSocket = new LocalServerSocket(this.socketOutgoingPath);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                LocalSocket socket = null;
                BluetoothSocket bluetoothSocket = null;

                try {

                    while (true) {

                        socket = localServerSocket.accept();

                        InputStream inputStream = socket.getInputStream();

                        byte[] addressBuffer = new byte[12];

                        int read = 0;

                        // The first thing we receive is the remote bluetooth address to connect to,
                        // which is 12 characters long
                        while (read < 12) {
                            inputStream.read(addressBuffer, read, 12);
                        }

                        String address = new String(addressBuffer);

                        BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
                        bluetoothSocket = remoteDevice.createRfcommSocketToServiceRecord(serviceUUID);

                        bluetoothSocket.connect();

                        Runnable reader = readFromBluetoothAndSendToSocket(socket, bluetoothSocket);
                        Runnable writer = readFromSocketAndSendToBluetooth(socket, bluetoothSocket);

                        new Thread(reader).start();
                        new Thread(writer).start();

                        connectionStatusNotifier.onConnectionSuccess(address, false);
                    }

                } catch (IOException e) {

                    if (bluetoothSocket != null) {
                        closeBluetoothSocket(bluetoothSocket);
                    }

                    if (socket != null) {
                        closeSocket(socket);
                    }

                }
            }
        });

        thread.start();
    }


    private Runnable readFromSocketAndSendToBluetooth(final LocalSocket localSocket,
                                                      final BluetoothSocket bluetoothSocket)
    {

        return new Runnable() {
            @Override
            public void run() {
                try {
                    IOUtils.copy(localSocket.getInputStream(), bluetoothSocket.getOutputStream());
                } catch (IOException e) {
                    closeBluetoothSocket(bluetoothSocket);
                    closeSocket(localSocket);
                }
            }
        };

    }

    private Runnable readFromBluetoothAndSendToSocket(final LocalSocket localSocket, final BluetoothSocket bluetoothSocket) {

        return new Runnable() {
            @Override
            public void run() {
                try {
                    IOUtils.copy(bluetoothSocket.getInputStream(), localSocket.getOutputStream());
                } catch (IOException e) {
                    closeBluetoothSocket(bluetoothSocket);
                    closeSocket(localSocket);
                }
            }
        };

    }

    private void closeSocket(LocalSocket localSocket) {
        try {
            localSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeBluetoothSocket(BluetoothSocket bluetoothSocket) {
        try {
            bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
