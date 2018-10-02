#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>


#include <jni.h>

JNIEXPORT jint JNICALL
Java_com_rusel_RCTBluetoothSerial_UnixServerSocket_createUnixSocketServer(JNIEnv *env,
                                                                          jobject instance,
                                                                          jstring path_) {

    const char *path = (*env)->GetStringUTFChars(env, path_, 0);
    unlink(path);

    struct sockaddr_un serveraddr;
    int sd = socket(AF_UNIX, SOCK_STREAM, 0);

    if (sd < 0)
    {
        return -1;
    }

    memset(&serveraddr, 0, sizeof(serveraddr));
    serveraddr.sun_family = AF_UNIX;
    strcpy(serveraddr.sun_path, path);

    int rc = bind(sd, (struct sockaddr *)&serveraddr, sizeof(serveraddr));

    if (rc < 0) {
        return rc;
    }

    return sd;
}