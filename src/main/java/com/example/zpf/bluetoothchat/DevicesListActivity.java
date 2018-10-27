package com.example.zpf.bluetoothchat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class DevicesListActivity extends Activity {
    //debug tag
    private static final String TAG = "DevicesListActivity";

    //request code for enable-Bluetooth request
    private static final int REQUEST_ENABLE_BT = 316;

    //for getting data from intent
    public static final String MAC_ADDRESS = "MAC_ADDRESS";

    private ArrayAdapter<String> mPairedDevicesArrayAdapter, mFoundDevicesArrayAdapter;

    private TextView paired, found;
    private ListView pairedList, foundList;
    private BluetoothAdapter mBluetoothAdapter;
    Button scanBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //don't close when touch outside
        setFinishOnTouchOutside(false);
        setContentView(R.layout.device_list);

        //default return result
        Intent intent = new Intent();
        setResult(RESULT_CANCELED, intent);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "该设备不支持蓝牙", Toast.LENGTH_LONG).show();
            finish();
        }

        mPairedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.list_item);
        mFoundDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.list_item);

        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter1);

        IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter2);

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        init();

        if (pairedDevices.size() == 0) {
            mPairedDevicesArrayAdapter.add("没有已经配对的设备");
        } else {
            for (BluetoothDevice d : pairedDevices) {
                mPairedDevicesArrayAdapter.add(d.getName() + " " + d.getAddress());
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            doDiscovery();
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "无法打开蓝牙", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void init() {
        //find views by id
        paired = findViewById(R.id.textview_paired_devices);
        found = findViewById(R.id.textview_found_devices);
        pairedList = findViewById(R.id.listview_paired_devices);
        foundList = findViewById(R.id.listview_found_devices);
        scanBtn = findViewById(R.id.button_scan);

        //set adapter for ListView
        pairedList.setAdapter(mPairedDevicesArrayAdapter);
        foundList.setAdapter(mFoundDevicesArrayAdapter);
        pairedList.setOnItemClickListener(mOnItemClickListener);
        foundList.setOnItemClickListener(mOnItemClickListener);

        //set listener for Button
        scanBtn.setOnClickListener(v -> {
            v.setVisibility(View.GONE);
            mFoundDevicesArrayAdapter.clear();
            if (!mBluetoothAdapter.isEnabled()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            } else {
                doDiscovery();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }
        unregisterReceiver(mReceiver);
    }

    private void doDiscovery() {
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mFoundDevicesArrayAdapter.add(btDevice.getName() + " " + btDevice.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (mFoundDevicesArrayAdapter.getCount() == 0) {
                    mFoundDevicesArrayAdapter.add("未找到任何设备");
                }
                scanBtn.setVisibility(View.VISIBLE);
            }
        }
    };

    private final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            mBluetoothAdapter.cancelDiscovery();

            //get the MAC address of the item selected
            String info = ((TextView) view).getText().toString();
            String macAddress = info.substring(info.length() - 17);

            Intent intent = new Intent();
            intent.putExtra(MAC_ADDRESS, macAddress);
            setResult(RESULT_OK, intent);
            finish();
        }
    };
}