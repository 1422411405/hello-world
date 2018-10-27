package com.example.zpf.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ChatService {
    //debug tag
    private static final String TAG = "ChatService";

    private int mState;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private Context mContext;

    //for server-side Bluetooth pairing
    private static final String NAME = "BluetoothChat";
    private static final UUID mUUID = UUID.fromString("2af513de-2030-4065-98d4-025ebabed090");

    //Threads
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    //State Constants
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_LISTEN = 3;

    public ChatService(Context context, Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = handler;
        mContext = context;
        mState = STATE_NONE;
    }

    public int getState() {
        return mState;
    }

    public void stop() {
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    public void startListen() {
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }

        mState = STATE_LISTEN;
        mHandler.obtainMessage(Constants.ACTION_STATE_CHANGE, STATE_LISTEN, -1).sendToTarget();
    }

    public void connect(BluetoothDevice device) {
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mState == STATE_CONNECTED) {
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
        }

        mConnectThread = new ConnectThread(device);
        mState = STATE_CONNECTING;
        mConnectThread.start();

        mHandler.obtainMessage(Constants.ACTION_STATE_CHANGE, STATE_CONNECTING, -1, device.getName()).sendToTarget();
    }

    private synchronized void connected(BluetoothSocket socket) {
        if (mState == STATE_CONNECTED && mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mState = STATE_CONNECTED;
        mConnectedThread.start();

        if (mAcceptThread != null) {
            Log.d(TAG, "close AcceptThread");

            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        mHandler.obtainMessage(Constants.ACTION_STATE_CHANGE, STATE_CONNECTED,
                -1, socket.getRemoteDevice().getName()).sendToTarget();
    }


    private void connectionLost() {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        //restart listen mode
        startListen();
    }

    private void connectionFailed() {
        Log.d(TAG, "connection failed" + Thread.currentThread().getName());
        if (mState == STATE_CONNECTING && mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        startListen();
    }

    public void write(String str) {
        if (mConnectedThread != null) {
            mConnectedThread.write(str.getBytes());
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, mUUID);
            } catch (IOException e) {

            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket mmSocket = null;
            while (mState != STATE_CONNECTED) {
                try {
                    Log.d(TAG, "Listening");
                    mmSocket = mmServerSocket.accept();
                    Log.d(TAG, "accetpt" + mmSocket.getRemoteDevice().getName());
                } catch (IOException e) {
                    Log.d(TAG, "AcceptThread Exception");
                    e.printStackTrace();
                }

                if (mmSocket != null) {
                    switch (mState) {
                        case STATE_CONNECTING:
                        case STATE_LISTEN:
                            connected(mmSocket);
                            break;
                        case STATE_CONNECTED:
                        case STATE_NONE:
                            try {
                                //either not ready or already connected, cancel this connect
                                mmSocket.close();
                            } catch (IOException e) {

                            }
                            break;
                    }
                }
            }
        }

        public void cancel() {
            Log.d(TAG, "AcceptThread canceled");

            try {
                mmServerSocket.close();
            } catch (IOException e) {

            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        //private BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            //mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(mUUID);
            } catch (IOException e) {
                Log.d(TAG, "ConnectThread Constructor Exception");
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "inside Thread" + getName());
            if (mmSocket != null) {
                try {
                    Log.d(TAG, "ready to connect");
                    mmSocket.connect();
                    Log.d(TAG, "connected");
                    connected(mmSocket);
                } catch (IOException e) {
                    Log.d(TAG, "ConnectThread Exception");
                    e.printStackTrace();

                    //connection time out
                    connectionFailed();
                }
            }
        }

        public void cancel() {
            Log.d(TAG, "ConnectThread canceled");

            try {
                mmSocket.close();
            } catch (IOException e) {

            }
        }
    }

    private class ConnectedThread extends Thread {
        private InputStream mInputStream = null;
        private OutputStream mOutputStream = null;
        private BluetoothSocket mSocket = null;

        public ConnectedThread(BluetoothSocket socket) {
            try {
                mSocket = socket;
                mInputStream = socket.getInputStream();
                mOutputStream = socket.getOutputStream();
            } catch (IOException e) {

            }
        }

        public void run() {
            int flag;
            byte[] buffer = new byte[1024];
            while (mState == STATE_CONNECTED) {
                try {
                    flag = mInputStream.read(buffer);
                    mHandler.obtainMessage(Constants.MESSAGE_READ, flag, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    //when connection lose, IOException occurs
                    Log.d(TAG, "ConnectedThread Exception");
                    e.printStackTrace();
                    connectionLost();
                }
            }

        }

        public void write(byte[] content) {
            try {
                mOutputStream.write(content);
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, content).sendToTarget();
            } catch (IOException e) {

            }
        }

        public void cancel() {
            try {
                mInputStream.close();
                mOutputStream.close();
                mSocket.close();
            } catch (IOException e) {

            }
        }
    }


}
