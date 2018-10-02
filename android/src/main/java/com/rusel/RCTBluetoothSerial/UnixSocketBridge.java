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
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UnixSocketBridge {


    private final String socketOutgoingPath;
    private final String socketIncomingPath;
    private final ConnectionStatusNotifier connectionStatusNotifier;
    private final BluetoothAdapter bluetoothAdapter;
    private final UUID serviceUUID;

    private static final String TAG = "bluetooth_bridge";

    BlockingQueue<String> blockingQueue = new LinkedBlockingQueue<>();

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

    public void connectToBluetoothAddress(String bluetoothAddress) {
        Log.d(TAG, "adding to queue of awaiting connections: " + bluetoothAddress);
        blockingQueue.add(bluetoothAddress);
    }


    public void listenForOutgoingConnections() {

        Log.d(TAG, "Outgoing connections thread. Sock path: " + this.socketOutgoingPath);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {
                    try {
                        String address = blockingQueue.take();
                        Log.d(TAG, "Dequeue awaiting connection: " + address);

                        Log.d(TAG, "Opening unix socket connection to proxy the bluetooth connection.");

                        LocalSocket localSocket = new LocalSocket();
                        LocalSocketAddress localSocketAddress = new LocalSocketAddress(
                                socketOutgoingPath, LocalSocketAddress.Namespace.FILESYSTEM
                        );

                        try {
                            localSocket.connect(localSocketAddress);
                        } catch (IOException e) {
                            Log.d(TAG, "Could not connect to unix socket to proxy bluetooth connection");
                            e.printStackTrace();

                            return;
                        }

                        Log.d(TAG, "Attempting bluetooth connection to " + address);

                        BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);

                        try {
                            BluetoothSocket bluetoothSocket =
                                    remoteDevice.createRfcommSocketToServiceRecord(serviceUUID);

                            bluetoothSocket.connect();

                            Log.d(TAG, "Connection successful to " + address);

                            Runnable reader = readFromSocketAndSendToBluetooth(localSocket, bluetoothSocket);
                            Runnable writer = readFromBluetoothAndSendToSocket(localSocket, bluetoothSocket);

                            // Todo: Use executor / some way to stop the other thread when one thread stops.

                            Thread readerThread = new Thread(reader);
                            Thread writerThread = new Thread(writer);

                            readerThread.start();
                            writerThread.start();

                            Log.d(TAG, "Started reader and writer threads");
                        } catch (Exception ex) {
                            Log.d(TAG, "Exception while connecting to " + address + ": " + ex.getMessage());
                            close(localSocket);
                        }



                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
