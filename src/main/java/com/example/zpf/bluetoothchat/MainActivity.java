package com.example.zpf.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    //debug tag
    private static final String TAG = "MainActivity";
    private static final int DELAY_TIME = 300;

    private String fileName = null;


    TextView state, content, audioIcon;
    Button sendBtn, switchBtn, audioBtn;
    EditText input;

    private String mFriendName;

    private final int MAC_ADDRESS_REQUEST = 315;
    private final int REQUEST_ENABLE_BT = 316;

    private BluetoothAdapter mBluetoothAdapter;
    private ChatService mChatService;

    private MediaRecorder mRecorder;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_READ:
                    String appendingStr1 = new String((byte[]) msg.obj, 0, msg.arg1);
                    String displayContent1 = content.getText() + "\n" + mFriendName + ": " + appendingStr1;
                    content.setText(displayContent1);
                    break;
                case Constants.MESSAGE_WRITE:
                    String appendingStr2 = new String((byte[]) msg.obj);
                    String displayContent2 = content.getText() + "\n我: " + appendingStr2;
                    content.setText(displayContent2);
                    break;
                case Constants.ACTION_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatService.STATE_CONNECTING:
                            state.setText("正在连接：" + msg.obj);
                            break;
                        case ChatService.STATE_CONNECTED:
                            state.setText("已连接：" + msg.obj);
                            mFriendName = (String) msg.obj;
                            break;
                        case ChatService.STATE_LISTEN:
                        case ChatService.STATE_NONE:
                            state.setText("未连接");
                            break;
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "该设备不支持蓝牙", Toast.LENGTH_LONG).show();
        }
        if (mBluetoothAdapter.isEnabled()) {
            mChatService = new ChatService(this, mHandler);
            mChatService.startListen();
        } else {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }
        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (mChatService != null) {
            mChatService.stop();
            mChatService = null;
        }
        mRecorder.release();
    }

    private Runnable updateThread = new Runnable() {
        @Override
        public void run() {
            audioIcon.setText("volumn" + (10 * mRecorder.getMaxAmplitude() / 32768));
            mHandler.postDelayed(updateThread, DELAY_TIME);
        }
    };

    private void init() {
        state = findViewById(R.id.textview_state);
        content = findViewById(R.id.textview_content);
        sendBtn = findViewById(R.id.button_send);
        input = findViewById(R.id.editview_input);
        switchBtn = findViewById(R.id.button_switch);
        audioBtn = findViewById(R.id.button_audio);
        audioIcon = findViewById(R.id.textview_audio_icon);

        mRecorder = new MediaRecorder();

        content.setOnClickListener(v -> {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.reset();
                Log.d(TAG, "playing completion");
            });

            try {
                mediaPlayer.setDataSource(fileName);
                mediaPlayer.prepare();
            } catch (IOException e) {

            }
            mediaPlayer.start();
        });

        sendBtn.setOnClickListener(v -> {
            String messageText = input.getText().toString();
            if (messageText.length() > 0) {
                mChatService.write(messageText);
                input.setText("");
            } else {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_LONG).show();
            }
        });

        state.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DevicesListActivity.class);
            startActivityForResult(intent, MAC_ADDRESS_REQUEST);
        });

        audioBtn.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                audioIcon.setVisibility(View.VISIBLE);

                mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                fileName = Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".3gp";
                mRecorder.setOutputFile(fileName);
                try {
                    mRecorder.prepare();
                } catch (IOException e) {
                    Log.d(TAG, "prepare failed");
                }
                mRecorder.start();

                mHandler.postDelayed(updateThread, 0);

            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                audioIcon.setVisibility(View.GONE);
                mHandler.removeCallbacks(updateThread);
                mRecorder.stop();
                mRecorder.reset();

                content.setText(content.getText() + "\n我:" + fileName);
            }
            return false;
        });

        switchBtn.setOnClickListener(v -> {
            if (audioBtn.getVisibility() == View.GONE) {
                InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm.isActive() && this.getCurrentFocus() != null) {
                    if (this.getCurrentFocus().getWindowToken() != null) {
                        imm.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    }
                }
                input.setVisibility(View.GONE);
                audioBtn.setVisibility(View.VISIBLE);
                switchBtn.setText("文本");
            } else {
                audioBtn.setVisibility(View.GONE);
                input.setVisibility(View.VISIBLE);
                switchBtn.setText("语音");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MAC_ADDRESS_REQUEST && resultCode == RESULT_OK) {
            String macAddress = data.getStringExtra(DevicesListActivity.MAC_ADDRESS);
            if (mBluetoothAdapter != null) {
                BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress);
                mChatService.connect(bluetoothDevice);
            }

        } else if (requestCode == MAC_ADDRESS_REQUEST && resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "获取设备信息失败", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            mChatService = new ChatService(this, mHandler);
            mChatService.startListen();
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "打开蓝牙失败", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }
    }
}
