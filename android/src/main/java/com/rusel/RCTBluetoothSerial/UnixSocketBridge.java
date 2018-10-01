package com.rusel.RCTBluetoothSerial;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class UnixSocketBridge {


    private final String socketOutgoingPath;
    private final String socketIncomingPath;
    private final ConnectionStatusNotifier connectionStatusNotifier;
    private final BluetoothAdapter bluetoothAdapter;
    private final UUID serviceUUID;

    private static final String TAG = "bluetooth_bridge";

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
        LocalSocketAddress localSocketAddress = new LocalSocketAddress(
                this.socketIncomingPath,
                LocalSocketAddress.Namespace.FILESYSTEM
        );

        try {
            localSocket.connect(localSocketAddress);

            Runnable reader = readFromBluetoothAndSendToSocket(localSocket, bluetoothSocket);
            Runnable writer = readFromSocketAndSendToBluetooth(localSocket, bluetoothSocket);

            connectionStatusNotifier.onConnectionSuccess(bluetoothSocket.getRemoteDevice().getAddress(), true);

            // TODO: Use thread executor and end other thread when on ends, then send disconnect
            // event
            Thread thread = new Thread(reader);
            Thread thread2 = new Thread(writer);

            thread.start();
            thread2.start();
        } catch (IOException e) {
            Log.d(TAG, "IO err on connection to socket for incoming connection: " + e.getMessage());

            connectionStatusNotifier.onConnectionFailure(
                    bluetoothSocket.getRemoteDevice().getAddress(),
                    e.getMessage()
            );
        }

    }

    public void listenForOutgoingConnections() throws IOException {

        Log.d(TAG, "Listening for outgoing connections. Sock path: " + this.socketOutgoingPath);

        final LocalServerSocket localServerSocket = new LocalServerSocket(this.socketOutgoingPath);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                LocalSocket socket = null;
                BluetoothSocket bluetoothSocket = null;

                try {

                    while (true) {

                        Log.d(TAG, "Awaiting next outgoing bluetooth connection to bridge");

                        socket = localServerSocket.accept();

                        Log.d(TAG, "Accepted connection from local server socket.");

                        InputStream inputStream = socket.getInputStream();

                        byte[] addressBuffer = new byte[17];

                        int totalRead = 0;

                        // The first thing we receive is the remote bluetooth address to connect to,
                        // which is 12 characters long
                        while (totalRead < 17) {
                            int read = inputStream.read(addressBuffer, totalRead, 17);

                            Log.d(TAG, "Read address: " + new String(addressBuffer));

                            totalRead += read;
                        }

                        String address = new String(addressBuffer);

                        Log.d(TAG, "Making outgoing bluetooth connection to " + address);

                        BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
                        bluetoothSocket = remoteDevice.createRfcommSocketToServiceRecord(serviceUUID);

                        bluetoothSocket.connect();

                        connectionStatusNotifier.onConnectionSuccess(address, false);

                        Runnable reader = readFromBluetoothAndSendToSocket(socket, bluetoothSocket);
                        Runnable writer = readFromSocketAndSendToBluetooth(socket, bluetoothSocket);

                        // TODO: Use thread executor and end other thread when on ends, then send disconnect
                        // event
                        new Thread(reader).start();
                        new Thread(writer).start();

                    }

                } catch (IOException e) {

                    Log.d(TAG, "Error making outgoing connection: " + e.getMessage());

                    if (bluetoothSocket != null) {
                        close(bluetoothSocket);
                    }

                    if (socket != null) {
                        close(socket);
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
                copyStream(localSocket, bluetoothSocket, true);
            }
        };

    }

    private Runnable readFromBluetoothAndSendToSocket(final LocalSocket localSocket, final BluetoothSocket bluetoothSocket) {

        return new Runnable() {
            @Override
            public void run() {
                copyStream(localSocket, bluetoothSocket, false);
            }
        };

    }

    private void copyStream(LocalSocket localSocket, BluetoothSocket bluetoothSocket, boolean socketToBluetooth) {

        FileDescriptor socketFd = localSocket.getFileDescriptor();

        Log.d(TAG, "Local socket connection fd: " + socketFd.toString());

        try {
            if (socketToBluetooth) {
                IOUtils.copyLarge(localSocket.getInputStream(), bluetoothSocket.getOutputStream());
            } else {
                IOUtils.copyLarge(bluetoothSocket.getInputStream(), localSocket.getOutputStream());
            }

        } catch (IOException e) {
            Log.d(TAG, "IO err " + e.getMessage());
            Log.d(TAG, "Socket fd: " + socketFd.toString());
        } finally {
            close(bluetoothSocket);
            close(localSocket);

            // TODO: Use a thread pool / executor for the input stream thread and output stream thread, and only
            // send this event once when one of them fails, and then end both threads.
            connectionStatusNotifier.onDisconnect(bluetoothSocket.getRemoteDevice().getAddress(), "");
        }

    }

    private void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
