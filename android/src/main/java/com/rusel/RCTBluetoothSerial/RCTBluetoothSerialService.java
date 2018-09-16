package com.rusel.RCTBluetoothSerial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.util.Base64;
import android.util.Log;

import static com.rusel.RCTBluetoothSerial.RCTBluetoothSerialPackage.TAG;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 *
 * This code was based on the Android SDK BluetoothChat Sample
 * $ANDROID_SDK/samples/android-17/BluetoothChat
 */
class RCTBluetoothSerialService {
    // Debugging
    private static final boolean D = true;

    // Trace: for verbose output (raw messages being sent and received, etc.)
    private static final boolean T = false;
    private final WebsocketBridge websocketBridge;

    // UUIDs

    // Member fields
    private BluetoothAdapter mAdapter;

    private RCTBluetoothSerialModule mModule;

    private ServerListenThread mServerListenThread = null;

    /**
     * Constructor. Prepares a new RCTBluetoothSerialModule session.
     * @param module Module which handles service events
     */
    RCTBluetoothSerialService(RCTBluetoothSerialModule module) throws UnknownHostException {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mModule = module;

        // Hardcode for now
        UUID uuid = UUID.fromString("b0b2e90d-0cda-4bb0-8e4b-fb165cd17d48");

        ConnectionStatusNotifier connectionStatusNotifier = new ConnectionStatusNotifier(mModule);

        this.websocketBridge = new WebsocketBridge(5666, mAdapter, uuid, connectionStatusNotifier);
        this.websocketBridge.start();
    }

    /**
     * Creates a server connection to listen for incoming connections.
     * return true if a server was not running and a new server was started, false is a server was already running.
     */
    public synchronized boolean startServerSocket(String serviceName, UUID serviceUUID) throws IOException {

        if (mServerListenThread == null) {
    
            BluetoothServerSocket bluetoothServerSocket = BluetoothAdapter
                    .getDefaultAdapter()
                    .listenUsingRfcommWithServiceRecord(serviceName, serviceUUID);
    
            // Listen for incoming connections on a new thread and put new entries into the
            // connected devices map
            mServerListenThread = new ServerListenThread(bluetoothServerSocket);
            mServerListenThread.start();
            return true;

        } else {
           if (D) Log.d(TAG, "Already listening for incoming connections");
           return false;
        }
        
    }

    /**
     * Stop accepting connections on the server socket.
     *
     * Synchronized for exclusive access to the mServerListenThread object
     *
     * @throws IOException
     */
    public synchronized void stopServerSocket() throws IOException {

        // Close the listen socket;
        mServerListenThread.closeListenSocket();

        // Stop the thread
        mServerListenThread.interrupt();

        mServerListenThread = null;
    }

    /**
     * This thread listens for new incoming
     */
    private class ServerListenThread extends Thread {

        private final BluetoothServerSocket serverSocket;
        private boolean stopped = false;

        ServerListenThread(BluetoothServerSocket serverSocket) {
            if (D) Log.d(TAG, "Created server listen thread");

            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            while (true) {
                // Block until there is a new incoming connection, then add it to the connected devices
                // then block again until there is a new connection. This loop exits when the thread is
                // stopped and an interrupted exception is thrown
                try {

                    if (D) Log.d(TAG, "Awaiting a new incoming connection");

                    final BluetoothSocket newConnection = this.serverSocket.accept();

                    if (D) Log.d(TAG, "New connection from: " + newConnection.getRemoteDevice().getAddress());

                    if (newConnection.getRemoteDevice().getBondState() != BluetoothDevice.BOND_BONDED)
                    {
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which){
                                    case DialogInterface.BUTTON_POSITIVE:
                                        if (D) Log.d(TAG, "Accepted incoming connection from: " + newConnection.getRemoteDevice().getAddress() + " bond state " + newConnection.getRemoteDevice().getBondState() );

                                        websocketBridge.createIncomingServerConnection(newConnection);
                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //No button clicked
                                        if (D) Log.d(TAG, "User did not accept the incoming connection. Closing socket.");
                                        try {
                                            newConnection.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                }
                            }
                        };

                        String message = "Accept incoming connection from: " + newConnection.getRemoteDevice().getName() + "(" + newConnection.getRemoteDevice().getAddress() + ")";
                        mModule.showYesNoDialog(message, dialogClickListener);
                    } else {
                        String address = newConnection.getRemoteDevice().getAddress();
                        if (D) Log.d( TAG, "Accepted incoming connection from " + address + " which has pre-existing bond." );

                        websocketBridge.createIncomingServerConnection(newConnection);
                    }


                } catch (IOException e) {

                    if (D) Log.d(TAG, "Error while accepting incoming connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        public void closeListenSocket() throws IOException {
            this.serverSocket.close();
        }
    }

}
