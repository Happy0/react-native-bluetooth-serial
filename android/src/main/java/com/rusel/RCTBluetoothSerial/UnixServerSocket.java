package com.rusel.RCTBluetoothSerial;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

public class UnixServerSocket {

    private final String TAG = "unix_socket_native";

    static {
        System.loadLibrary("unix-socket-server");
    }

    private final String filePath;

    public UnixServerSocket(String filePath) {
        this.filePath = filePath;
    }

    public FileDescriptor getFileDescriptor() throws IOException {

        int unixSocketServerFd = createUnixSocketServer(this.filePath);

        if (unixSocketServerFd < 0 ) {
            Log.d(TAG, "Error while creating native file descriptor: " + unixSocketServerFd);
            throw new IOException("Could not open native server socket. Err code: " + unixSocketServerFd);
        } else {
            Log.d(TAG, "Socket fd: " + unixSocketServerFd);
        }

        ParcelFileDescriptor parcelFileDescriptor = ParcelFileDescriptor.adoptFd(unixSocketServerFd);

        return parcelFileDescriptor.getFileDescriptor();
    }

    private native int createUnixSocketServer(String path);
}
