package com.test.smartband.activity;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


public class BleService extends Service  {

    //通知消息
    public static final String TAG = "*==BleService==*";
    static final int MSG_REGISTER = 1;
    static final int MSG_UNREGISTER = 2;
    static final int MSG_START_SCAN = 3;
    static final int MSG_STATE_CHANGED = 4;
    static final int MSG_DEVICE_CONNECT = 5;
    static final int MSG_DEVICE_DISCONNECT = 6;
    static final int MSG_TELE_INFO = 7;
    static final int MSG_READ_CHARACTERISTIC = 8;
    //static final int MSG_WRITE_CONFIG = 9;
    static final int MSG_LOCATE = 10;
    static final int MSG_STOP_SCAN = 11;
    //static final int MSG_GET_STATE = 12;
    static final int MSG_TELE = 13;
    static final int MSG_SET_STATE = 14;
    static final int MSG_AIR_CONTROL = 15;
    static final int MSG_CONNECT_KIND = 16;//用于判断连接类型

    //连接状态
    static final int Connected = 1;
    static final int Idle = 2;

    //读不同数据
    public static final int POWER = 1;
    public static final int CALORIE = 2;
    public static final int STEP = 3;
    public static final int TEMP = 4;
    public static final int TERMINAL = 5;

    //不同空调控制指令
    public static final int MINUS = 1;
    public static final int PLUS = 2;
    public static final int SWITCH = 3;
    public static final int MODE = 4;
    public static final int SPEED = 5;
    public static final int DIRECTION = 6;
    public static final int COMFORT = 7;

    public static final UUID LINK_LOSS_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");
    public static final UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID KEY_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID KEY_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    public static final UUID PASSWORD_UUID = UUID.fromString("00002a08-0000-1000-8000-00805f9b34fb");

    public static final UUID DATA_SERVICE_UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb");
    public static final UUID LEVEL_UUID = UUID.fromString("0000FFF3-0000-1000-8000-00805f9b34fb");
    public static final UUID TEMP_UUID = UUID.fromString("0000FFF4-0000-1000-8000-00805f9b34fb");
    public static final UUID HUMIDITY_TEMP_UUID = UUID.fromString("0000FFF6-0000-1000-8000-00805f9b34fb");
    public static final UUID CALORIE_UUID = UUID.fromString("0000FFF8-0000-1000-8000-00805f9b34fb");
    public static final UUID STEP_UUID = UUID.fromString("0000FFF7-0000-1000-8000-00805f9b34fb");
    public static final UUID AIR_CONTROL_UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805f9b34fb");
    //键值
    public static final String KEY_LINKEDADDR = "KEY_LINKEDADDR";

    public static final int SCAN_PERIOD = 3000;

    //创建该队列是为了在写数据时防止一个数据还没写完，又开始写下一个数据，这样无法向远程端写数据
    private static final Queue<Object> sWriteQueue = new ConcurrentLinkedQueue<Object>();
    //所以将写命令一个个写入队列中一个个执行
    private static boolean sIsWriting = false;

    private int action = 0;

    //蓝牙通信动作控制
    final static int ACT_TIMEOUT = 100;
    final static int ACT_SCAN = 101;
    final static int ACT_DIS = 102;


    private IncomingHandler mHandler;
    private Messenger mMessenger;
    private List<Messenger> mClientsMsger = new LinkedList<Messenger>();
    private Map<String, BluetoothDevice> mDevices = new HashMap<String, BluetoothDevice>();

    //定位相关
    public MyLocation myLocation;//定位类
    public LocationData handleData;//处理定位数据的类
    private SharedPreferences preference;//用来存储定位数据和记录地址数。
    Editor editor;

    int rssiOld = -100;//用于判断
    static int SearchCount = 0;
    static int airCount = 0;
    static boolean isFirst = true;


    public static enum State {
        UNKNOWN,  //状态未知
        IDLE, //空闲
        SCANNING,
        BLUETOOTH_OFF,
        CONNECTING,
        CONNECTED,
    }

    private BluetoothGatt mGatt = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private State mState = State.IDLE;

    public BleService() {
        mHandler = new IncomingHandler();
        mMessenger = new Messenger(mHandler);

    }

    @Override
    public void onCreate() {
        //服务绑定成功后
        //存储定位数据的sharepreference
        preference = getSharedPreferences("LOCATIONDATA", MODE_PRIVATE);//记住这个preference
        editor = preference.edit();
        //定位
        handleData = new LocationData(getApplicationContext(), preference);
        myLocation = new MyLocation(getApplicationContext(), handleData);

        if (!preference.contains("isFirst")) {//如果没有包含该键值，则创建一个
            /**
             * 初始化为真------->代表该应用第一次使用，未连接任何设备，朱叶设计必须先与手环连接一次
             * 才能与空调终端连接，一旦手环连接过，该值设为false
             */
            editor.putBoolean("isFirst", true);//
            editor.commit();
        }
        if (!preference.contains("Mac")) {//如果没有包含该键值，则创建一个
            editor.putString("Mac", null);
            editor.commit();
        }
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {

        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "服务销毁", Toast.LENGTH_SHORT).show();
        close();
        super.onDestroy();
    }

    //static
    private class IncomingHandler extends Handler {
        //        private final WeakReference<BleService> mService;
        //
        //        public IncomingHandler(BleService service) {
        //            mService = new WeakReference<BleService>(service);
        //        }

        //处理来自客户端的消息
        @Override
        public void handleMessage(Message msg) {
            //final BleService service = mget();//service对象的引用
            Bundle data = new Bundle();
            //if (service != null) {}
            switch (msg.what) {
                case MSG_REGISTER:
                    Toast.makeText(getApplicationContext(), "Registered--获得Activity端Messenger", Toast.LENGTH_SHORT).show();
                    //mClientsMsger.add(msg.replyTo);//将一个客户加入客户列表
                    mClientsMsger.add(msg.replyTo);//将一个客户加入客户列表
                    Log.i(TAG, "--->handleMessage(): MSG_REGISTER: --Acitivity发送注册Message到Service，Service获得Activity的Message.replyTo");
                    break;
                case MSG_UNREGISTER://取消注册
                    Toast.makeText(getApplicationContext(), "UnRegistered", Toast.LENGTH_SHORT).show();
                    mClientsMsger.remove(msg.replyTo);
                    Log.i(TAG, "--->handleMessage(): MSG_UNREGISTER");
                    break;
                case MSG_START_SCAN://开始扫描   ！！！！这部分逻辑需要调整 2016年6月13日-陈其康
                    // TODO: 2016/6/14 0014  这部分逻辑需要调整 2016年6月13日-陈其康
                    if (mState == State.CONNECTED && mGatt != null) {
                        mGatt.disconnect();
                        action = ACT_SCAN;
                        //disconnect = true;
                        //setState(State.IDLE);
                        Toast.makeText(getApplicationContext(), "State.CONNECTED--mGatt.disconnect();", Toast.LENGTH_SHORT).show();
                        Log.i(TAG,"--->handleMessage(): MSG_START_SCAN: State.CONNECTED--mGatt.disconnect();");
                    } else if (mState == State.SCANNING) {//停止搜索
                        stopScan();
                        Toast.makeText(getApplicationContext(), "State.SCANNING--stopScan()", Toast.LENGTH_SHORT).show();
                        Log.i(TAG,"--->handleMessage(): MSG_START_SCAN:  State.SCANNING--stopScan()");
                    } else if (mState == State.IDLE || mState == State.BLUETOOTH_OFF || mState == State.CONNECTING) {
                        Log.i(TAG,"--->handleMessage(): MSG_START_SCAN:  mState == State.IDLE || mState == State.BLUETOOTH_OFF || mState == State.CONNECTING--startScan();");
                        startScan();
                        Toast.makeText(getApplicationContext(), "开始搜索", Toast.LENGTH_SHORT).show();

                    }
                    //Log.i(TAG, "Start Scan");
                    break;
                case MSG_DEVICE_DISCONNECT://断开连接
                    if (mState == State.CONNECTED && mGatt != null) {
                        mGatt.disconnect();
                        //setState(State.IDLE);
                        action = ACT_DIS;
                    }
                    break;
                case MSG_READ_CHARACTERISTIC:
                    if (mState == State.CONNECTED && mGatt != null) {
                        data = msg.getData();
                        //读取数据信息
                        readData(data.getInt("Type"), DATA_SERVICE_UUID);
                        Toast.makeText(getApplicationContext(), "读数据", Toast.LENGTH_SHORT).show();
                    }
                    break;
                //					case MSG_GET_STATE:
                //						sendMessage( getStateMessage() );
                //						break;
                case MSG_LOCATE://定位消息
                    myLocation.startLocation(null);//初始化定位设置并开始定位
                    Toast.makeText(getApplicationContext(), "收到命令", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_STOP_SCAN:
                    stopScan();
                    break;
                case MSG_TELE_INFO://判断数据是否写成功
                    data = msg.getData();
                    //Toast.makeText(getApplicationContext(),"发送空调指令", Toast.LENGTH_SHORT).show();
                    if (data.getString("which").equals(AIR_CONTROL_UUID.toString())) {
                        Toast.makeText(getApplicationContext(), "发送空调指令", Toast.LENGTH_SHORT).show();
                    }
                    if (data.getString("which").equals(LEVEL_UUID.toString())) {
                        Toast.makeText(getApplicationContext(), "发送舒适指令", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case MSG_AIR_CONTROL://处理空调控制
                    data = msg.getData();
                    //待添加
                    airControl(data);
                    break;
                case MSG_SET_STATE:
                    data = msg.getData();
                    switch (data.getInt("STATE")) {
                        case Connected:
                            Log.i(TAG,"GATT服务连接成功");
                            setState(State.CONNECTED);
                            //连接后开始读数据
                            if (preference.getString("Mac", null).equals("A0:E6:F8:07:AD:23") || preference.getString("Mac", null).equals("A0:E6:F8:07:AB:B7") || preference.getString("Mac", null).equals("A0:E6:F8:07:AD:06")) {//终端自动更新数据
                                //readData(service, TERMINAL, DATA_SERVICE_UUID);
                                enableNoti(mGatt, DATA_SERVICE_UUID, HUMIDITY_TEMP_UUID);//开启char6通知功能，char6存储的是温湿度数据
                                Toast.makeText(getApplicationContext(), "使能通知", Toast.LENGTH_SHORT).show();
                            } else {//手环读体温、计步数据、电池电量
                                //	readData(service, TEMP, DATA_SERVICE_UUID);
                                //	readData(service,STEP, DATA_SERVICE_UUID);
                                //readData(service, CALORIE, DATA_SERVICE_UUID);
                                //readData(service, POWER, BATTERY_SERVICE_UUID);
                                enableNoti(mGatt, DATA_SERVICE_UUID, TEMP_UUID);
                                enableNoti(mGatt, DATA_SERVICE_UUID, STEP_UUID);
                                periodDisconnect(20000);//如果是手环，延时断开,周期性断开
                            }
                            break;
                        case Idle:
                            mGatt.close();//关闭代理
                            mGatt = null;
                            setState(State.IDLE);
                            switch (action) {
                                case ACT_SCAN://搜索动作造成的状态改变
                                case ACT_DIS://断开连接造成的状态改变
                                case ACT_TIMEOUT://超时造成的状态改变
                                    msg = Message.obtain(null, MSG_START_SCAN);//连接断开后自动搜索
                                    if (msg != null) {
                                        sendMessage(msg);
                                    }
                                    break;
                                default:
                                    action = 0;
                                    break;
                            }
                            break;
                        default:
                            break;
                    }
                    action = 0;
                    break;
                default:
                    super.handleMessage(msg);
            }

        }
    }


    private void stopScan() {
        if (mState == State.SCANNING) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);//调用stopLeScan函数停止搜索
            setState(State.IDLE);
        }
    }

    //扫描工作
    public void startScan() {
        mDevices.clear();//清除设备 （Map类型集合）  为什么扫描就要清空设备集合？
        //判断蓝牙是否开启
        // TODO: 2016/6/14 0014 陈其康 这部分逻辑单独抽离，使用一个对话框提示用户打开蓝牙
        if (mBluetoothAdapter == null) {
            BluetoothManager bluetoothMgr = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);//获得蓝牙管理器
            mBluetoothAdapter = bluetoothMgr.getAdapter();//得到蓝牙适配器
        }
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {//判断是否支持蓝牙或者是否打开
            //setState(State.BLUETOOTH_OFF);//是，则设置蓝牙状态为关闭
            mBluetoothAdapter.enable(); //隐式开启蓝牙，静默开启
        }


        /*startLeScan（）需要传入BluetoothAdapter.LeScanCallback参数，本Service类实现了该接口，故传入this
        * ble搜索结果将通过该callback返回-->onLeScan（）
        * */
        setState(State.SCANNING);//将状态设置为正在搜索
        mBluetoothAdapter.startLeScan(mLeScanCallback);//开始搜索
        Log.i(TAG,"--->startScan() : mBluetoothAdapter.startLeScan(mLeScanCallback);//开始搜索");
       /*注意：由于搜索需要尽量减少功耗，因此在实际使用时需要注意：
        1、当找到对应的设备后，立即停止扫描；
        2、不要循环搜索设备，为每次搜索设置适合的时间限制。避免设备不在可用范围的时候持续不停扫描，消耗电量。
        */
    }

    BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        //BLE搜索回调函数
        @Override
        public void onLeScan( BluetoothDevice device, int rssi, byte[] scanRecord) {
            //Log日志打印扫描到的设备名称和地址
            Log.i(TAG,"--->LeScanCallback:onLeScan(): device_name: "+device.getName()+"  address(MAC): "+device.getAddress());
            //开始扫描后，每扫描到一个设备，即通过广播回调一次
            //if (device != null && device.getName() != null && device.getName().equals(DEVICE_NAME))//设备不为空，名字不为空，且名字要为指定名称
            boolean updata = false;
            if (device != null && device.getName() != null) {
                //先判断蓝牙设备是否为“空调”，再判断是否为“手环”
                //一下一行的mac为空调终端地址
                if (((device.getAddress()).equals("A0:E6:F8:07:AD:06") || (device.getAddress()).equals("A0:E6:F8:07:AD:23") || (device.getAddress()).equals("A0:E6:F8:07:AB:B7")) && !preference.getBoolean("isFirst", true)) {
                    // TODO: 2016/6/14 0014 设计用意忘记了，问朱叶
                    //搜索到空调（地址固定了）且配置了手环----设计用意忘记了？
                    airCount++;//空调终端标志---连续扫描到10次，则进行连接
                    if (airCount > 10) {
                        connect(device);//连接空调
                        airCount = 0;
                    }
                    SearchCount = 0;//这又是什么鬼？
                } else {//不是空调
                    if ((device.getName()).equals("DA14583")) {
                        //以蓝牙名来区分手环
                        SearchCount++;//延时，找出最大信号强度的手环连接
                        if (preference.getBoolean("isFirst", true)) {//第一次连接必须连接手环，以便进行配置
                            // TODO: 2016/6/14 0014 这个判断用意？
                            if (mDevices.isEmpty() || mDevices.get(device.getAddress()) != null) {
                                rssiOld = rssi;
                                updata = true;
                            } else {
                                if (rssi > rssiOld) {//另外一个设备，设备信号强度大，连接此设备
                                    updata = true;
                                    rssiOld = rssi;
                                    mDevices.clear();//又清空设备集合？
                                } else {
                                    updata = false;
                                }
                            }
                            if (updata) {
                                mDevices.put(device.getAddress(), device);//只将部分符合条件的设备加载出来
                                updata = false;
                                if (SearchCount > 50) {
                                    SearchCount = 0;
                                    Log.i(TAG,"--->LeScanCallback:onLeScan(): SearchCount > 50--准备连接设备");
                                    connect(device);
                                    Toast.makeText(getApplicationContext(),
                                            "连接设备" + device.getAddress(), Toast.LENGTH_SHORT)
                                            .show();
                                    editor.putString("bleAddr", device.getAddress());
                                    editor.commit();
                                    Log.i(TAG,"--->LeScanCallback:onLeScan(): 存储手环地址bleAddr editor.putString(\"bleAddr\", device.getAddress()); ");
                                    Toast.makeText(getApplicationContext(),
                                            "存储地址" + preference.getString("bleAddr", "unkonwn"), Toast.LENGTH_SHORT)
                                            .show();
                                    editor.putBoolean("isFirst", false);
                                    editor.commit();
                                    Log.i(TAG,"--->LeScanCallback:onLeScan(): 手环连接：首次，editor.putBoolean(\"isFirst\", false); ");

                                }
                            }
                        } else {//不是第一次连接手环，以第一次连接手环的地址进行连接，search是用于延时连接
                            if ((device.getAddress()).equals(preference.getString("bleAddr", "unkonwn")) && SearchCount > 50) {//延迟一段时间：搜索到50次手环
                                connect(device);
                                SearchCount = 0;
                                Toast.makeText(getApplicationContext(),
                                        "NO2" + preference.getString("bleAddr", "unkonwn") + " " + device.getAddress(), Toast.LENGTH_SHORT)
                                        .show();
                                Log.i(TAG,"--->LeScanCallback:onLeScan(): 手环连接：再次，editor.putBoolean(\"isFirst\", false); ");
                            }
                        }
                    }
                }
            }
        }
    };



    /**
     * 连接设备
     */

    public void connect(BluetoothDevice device) {
        if (mState == State.SCANNING) {//必须在搜索后连接
            stopScan();
            Log.i(TAG,"--->stopScan(),停止扫描");
            editor.putString("Mac", device.getAddress());
            editor.commit();
            if (preference.getString("Mac", null).equals("A0:E6:F8:07:AD:23")||preference.getString("Mac", null).equals("A0:E6:F8:07:AB:B7")||preference.getString("Mac", null).equals("A0:E6:F8:07:AD:06")) {
                Toast.makeText(getApplicationContext(),
                        "开始连接空调:", Toast.LENGTH_SHORT)
                        .show();
                Log.i(TAG,"--->connect(BluetoothDevice),开始连接空调 : "+device.getAddress());
            }else {
                Toast.makeText(getApplicationContext(),
                        "开始连接手环", Toast.LENGTH_SHORT)
                        .show();
                Log.i(TAG,"--->connect(BluetoothDevice),开始连接手环 : "+device.getAddress());
            }
            setState(State.CONNECTING);//设置正在连接
            if (device != null) {
                //正式连接设备！！！！！！！！！！！！！！！！！！！！！！！！其实是连接设备的Gatt
                mGatt = device.connectGatt(this, true, mGattCallback);//创建GATT本地代理，并自动向远程端发起连接，连接远程端服务器后调用mGattCallback中的回调函数

                if (mGatt!=null){
                    //TODO 蓝牙GATT操作逻辑
                }
                //mGatt.connect();
                //参数分析：Context、 autoConnect(boolean)和 BluetoothGattCallback 对象
                //以上代码表示客户端应用程序连接到 GATT服务器
                //后续工作则会在GattCallback的回调接口中实现
            }
        }

    }

    public boolean readCharacteristic(BluetoothGattCharacteristic Char) {//读特性值
        boolean result = false;
        if (mGatt != null) {
            result = mGatt.readCharacteristic(Char);
            Log.i(TAG, "readCharacteristic() - Char=" + Char);
            return result;
        }
        return false;
    }

    public BluetoothGattService getService(UUID ServiceUUID) {
        if (mGatt != null) {
            Log.i(TAG, "getService() - ServiceUUID=" + ServiceUUID);
            return mGatt.getService(ServiceUUID);
        }
        return null;
    }

    public BluetoothGattCharacteristic getCharacteristic(BluetoothGattService iService, UUID CharUUID) {//获得指定服务的特性UUID
        if (iService != null) {
            Log.i(TAG, "getService() - CharUUID=" + CharUUID);
            return iService.getCharacteristic(CharUUID);
        }
        return null;
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.i(TAG, "--->GattCallback.onConnectionStateChange(): " +
                    "Connection State Changed: " +
                    (newState == BluetoothProfile.STATE_CONNECTED ? "Connected" : "Disconnected"));

            if (newState == BluetoothProfile.STATE_CONNECTED) {//判断是否连接成功，连接成功后改变标志位
                /**
                 * 连接成功，立即扫描设备的GattService
                 */
                Log.i(TAG, "--->GattCallback.onConnectionStateChange(): " +
                        "设备GATT连接成功，扫描服务 discoverServices()" );

                gatt.discoverServices();//进行服务发现
            } else {//未连接则设置为未连接状态，判断造成未连接原因
                //setState(State.IDLE);
                if (action != ACT_SCAN && action != ACT_DIS) {
                    action = ACT_TIMEOUT;
                }
                //设置为未连接
                Message msg = Message.obtain(null, MSG_SET_STATE);
                Bundle data = new Bundle();
                data.putInt("STATE", Idle);//设置已连接
                try {
                    msg.setData(data);
                    mMessenger.send(msg);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                //				Message msg = createMsg(MSG_SET_STATE,"STATE",Idle);
                //				try {
                //					mMessenger.send(msg);
                //				} catch (RemoteException e) {
                //					e.printStackTrace();
                //				}

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {//服务发现后回调
            Log.i(TAG, "--->onServicesDiscovered(发现GATT服务): " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {//成功发现服务,到这里表示已经完全连接了

                //设置为已连接
                Message msg = Message.obtain(null, MSG_SET_STATE);
                Bundle data = new Bundle();
                data.putInt("STATE", Connected);
                try {
                    msg.setData(data);
                    mMessenger.send(msg);//设置已连接
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                //gatt.getServices();
                //				Message msg = createMsg(MSG_SET_STATE,"STATE",Connected);
                //				try {
                //					mMessenger.send(msg);
                //				} catch (RemoteException e) {
                //					e.printStackTrace();
                //				}
            }
        }

        //		@Override
        //		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {//读取RSSI后调用，在这里实现报警
        //			Log.v(TAG, "onReadRemoteRssi: " + status);
        //			if (status == BluetoothGatt.GATT_SUCCESS) {//成功读取
        //
        //			}
        //		}

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {//写特性值完成后调用
            Log.v(TAG, "onCharacteristicWrite: " + status);
            sIsWriting = false;
            //写超时报警等级回调监控
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(AIR_CONTROL_UUID) && characteristic.getService().getUuid().equals(DATA_SERVICE_UUID)) {
                    Message msg = Message.obtain(null, MSG_TELE_INFO);
                    if (msg != null) {
                        try {
                            Bundle data = new Bundle();
                            data.putString("which", AIR_CONTROL_UUID.toString());
                            msg.setData(data);
                            mMessenger.send(msg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (characteristic.getUuid().equals(LEVEL_UUID) && characteristic.getService().getUuid().equals(DATA_SERVICE_UUID)) {
                    Message msg = Message.obtain(null, MSG_TELE_INFO);
                    if (msg != null) {
                        try {
                            Bundle data = new Bundle();
                            data.putString("which", LEVEL_UUID.toString());
                            msg.setData(data);
                            mMessenger.send(msg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            nextWrite();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {//写描述符完成后调用
            Log.v(TAG, "onDescriptorWrite: " + status);
            String dataKind = null;
            sIsWriting = false;
            nextWrite();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //按键通知配置回调
                if (descriptor.getCharacteristic().getUuid().equals(KEY_UUID)) {
                    Message msg = Message.obtain(null, MSG_TELE_INFO);
                    if (msg != null) {
                        try {
                            Bundle data = new Bundle();
                            data.putString("which", KEY_UUID.toString());
                            msg.setData(data);
                            mMessenger.send(msg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //配置通知功能回调，判断是否配置成功
                if (descriptor.getCharacteristic().getUuid().equals(BATTERY_LEVEL_UUID)) {
                    Message msg = Message.obtain(null, MSG_TELE_INFO);
                    if (msg != null) {
                        try {
                            Bundle data = new Bundle();
                            data.putString("which", BATTERY_LEVEL_UUID.toString());
                            msg.setData(data);
                            mMessenger.send(msg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {//特性值发生变化后调用
            Log.i(TAG, "onCharacteristicChanged: " + characteristic.getUuid());
            String dataKind = null;
            //			if (characteristic.getUuid().equals(BATTERY_LEVEL_UUID)){ //电池信息改变通知
            //				dataKind = "POWER";
            //			}
            //			if (characteristic.getUuid().equals(HUMIDITY_TEMP_UUID)) {//读终端温湿度
            //				dataKind = "TERMINAL";
            //			}
            //			Message msg = createMsg(MSG_READ_CHARACTERISTIC, dataKind, characteristic.getValue());
            //			sendMessage(msg);//发送读特性消息，进入Activity中进行相关处理
            sendData(characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {


            if (status == BluetoothGatt.GATT_SUCCESS) {//成功读取
                sendData(characteristic);//发送数据到MainActivity
                Toast.makeText(getApplicationContext(),
                        "onCharacteristicRead(),characteristic数据读取成功", Toast.LENGTH_SHORT)
                        .show();
                Log.i(TAG, "--->onCharacteristicRead(),characteristic数据读取成功");
            } else
                Toast.makeText(getApplicationContext(),
                        "onCharacteristicRead(),characteristic数据读取失败", Toast.LENGTH_SHORT)
                        .show();
            Log.i(TAG, "--->onCharacteristicRead(),characteristic数据读取失败");
        }


    };



    //创建消息体，用于向注册了的客户端发送数据。可以创建承载字节数组、字符串、整型的消息体.以键值key标识不同数据
    public Message createMsg(final int MSG, String kind, Object value) {
        Message msg = Message.obtain(null, MSG);
        Bundle bundle = new Bundle();
        if (value instanceof byte[])
            bundle.putByteArray(kind, (byte[]) value);
        else if (value instanceof String)
            bundle.putString(kind, (String) value);
        else //if (value instanceof Integer)
            bundle.putInt(kind, (int) value);
        msg.setData(bundle);
        return msg;
    }


    //返回读的数据
    public void sendData(BluetoothGattCharacteristic characteristic) {
        String dataKind = null;
        if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {//读电池电量
            dataKind = "POWER";
        } else if (TEMP_UUID.equals(characteristic.getUuid())) {//读体温
            dataKind = "TEMP";
        } else if (HUMIDITY_TEMP_UUID.equals(characteristic.getUuid())) {//读终端温湿度
            dataKind = "TERMINAL";
        } else if (CALORIE_UUID.equals(characteristic.getUuid())) {//读终端温湿度
            dataKind = "CALORIE";
        } else if (STEP_UUID.equals(characteristic.getUuid())) {//读终端温湿度
            dataKind = "STEP";
        }
        Message msg = createMsg(MSG_READ_CHARACTERISTIC, dataKind, characteristic.getValue());
        sendMessage(msg);
    }

    //写数据。创建一个队列顺序写数据
    private synchronized boolean write(Object o) {
        if (sWriteQueue.isEmpty() && !sIsWriting) {
            if (doWrite(o))
                return true;
            else
                return false;
        } else {
            sWriteQueue.add(o);
        }

        return false;
    }

    private synchronized boolean nextWrite() {
        if (!sWriteQueue.isEmpty() && !sIsWriting) {
            if (doWrite(sWriteQueue.poll()))
                return true;
            else
                return false;
        }
        return false;
    }

    private synchronized boolean doWrite(Object o) {
        if (o instanceof BluetoothGattCharacteristic) {
            sIsWriting = true;
            if (mGatt.writeCharacteristic((BluetoothGattCharacteristic) o))
                return true;
            else
                return false;
        } else if (o instanceof BluetoothGattDescriptor) {
            sIsWriting = true;
            if (mGatt.writeDescriptor((BluetoothGattDescriptor) o))
                return true;
            else
                return false;
        } else {
            nextWrite();
        }

        return false;
    }

    /**
     * 设置当前蓝牙工作状态标识
     * @param newState 新状态
     */
    private void setState(State newState) {
        //if (mState != newState) {
        mState = newState;
        Message msg = getStateMessage();
        if (msg != null) {
            sendMessage(msg);
        }
        //Toast.makeText(getApplicationContext(),mState.toString(), Toast.LENGTH_SHORT).show();
        //	}
    }

    //获取当前连接状态命令
    private Message getStateMessage() {
        Message msg = Message.obtain(null, MSG_STATE_CHANGED);
        if (msg != null) {
            msg.arg1 = mState.ordinal();//把状态值在枚举中的序号传过去
        }
        return msg;
    }

    //会向所有注册了的客户端发送消息，包括service自身
    private void sendMessage(Message msg) {
        for (int i = 0 ; i<mClientsMsger.size(); i++) {
            Messenger messenger = mClientsMsger.get(i);
            if (!sendMessage(messenger, msg)) {
                mClientsMsger.remove(messenger);//如果有发送失败，说明客户不存在了，删掉它
            }//向所有订阅了的客户端发送消息
        }
    }

    //向指定客户发送消息
    private boolean sendMessage(Messenger messenger, Message msg) {
        boolean success = true;
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            Log.i(TAG, "Lost connection to clientActivity Messenger", e);
            success = false;//出现异常，返回false
            //Toast.makeText(getApplicationContext(),"fail", Toast.LENGTH_SHORT).show();
        }
        return success;
    }


    public boolean enableNotification(boolean enable, BluetoothGattCharacteristic characteristic) {
        Log.i(TAG, "enableNotification status=" + characteristic);
        if (mGatt == null) {
            Toast.makeText(getApplicationContext(),
                    "无mGatt", Toast.LENGTH_SHORT)
                    .show();
            return false;
        }

        //TODO 开启CharacteristicNotification
        if (!mGatt.setCharacteristicNotification(characteristic, enable)) {//先要开启对应特性的通知功能
            Toast.makeText(getApplicationContext(),
                    "开启本地通知失败", Toast.LENGTH_SHORT)
                    .show();
            return false;
        }

        BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(CCC);//获得它的客户端描述符
        if (clientConfig == null) {
            Toast.makeText(getApplicationContext(),
                    "无客户端配置", Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        if (enable) {
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);//设置客户端描述符的值
            //         不能开启此处通知提示   Toast.makeText(getApplicationContext(),
            //    				"通知开启", Toast.LENGTH_SHORT)
            //    				.show();
        } else {
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        return write(clientConfig);//写客户端配置
    }

    //读指定数据服务下的数据
    //public static void readData(BleService service, int dataType, UUID uuidDataService) {
    public void readData(int dataType, UUID uuidDataService) {
        BluetoothGattCharacteristic data = null;
        UUID temp = null;
        BluetoothGattService dataService = mGatt.getService(uuidDataService);
        if (dataService == null) {
            Toast.makeText((getApplicationContext()),
                    "无数据服务", Toast.LENGTH_SHORT)
                    .show();
            Log.i(TAG,"--->BluetoothGattService获取失败，无数据服务");
            return;
        }
        switch (dataType) {
            case POWER:
                //待添加*********************************************************
                //uuidDataService = mGatt.getService(BATTERY_SERVICE_UUID);
                temp = BATTERY_LEVEL_UUID;
                Toast.makeText(getApplicationContext(), "读电量", Toast.LENGTH_SHORT).show();
                break;
            case TEMP:
                temp = TEMP_UUID;
                Toast.makeText((getApplicationContext()),
                        "读温度", Toast.LENGTH_SHORT)
                        .show();
                break;
            case CALORIE:
                temp = CALORIE_UUID;
                Toast.makeText(getApplicationContext(), "读卡路里", Toast.LENGTH_SHORT).show();
                break;
            case STEP:
                temp = STEP_UUID;
                Toast.makeText(getApplicationContext(), "读步数", Toast.LENGTH_SHORT).show();
                break;
            case TERMINAL:
                temp = HUMIDITY_TEMP_UUID;
                Toast.makeText(getApplicationContext(), "读终端", Toast.LENGTH_SHORT).show();
                break;
        }

        if (temp != null) {
            //获得指定UUID的characteristic对象，后续通过该对象读取内容value
            data = dataService.getCharacteristic(temp);
        }

        if (data == null) {
            Toast.makeText((getApplicationContext()),
                    "无数据特性", Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        //根据特定UUID的characteristic来读取其值
        boolean result = mGatt.readCharacteristic(data);
        if (result == false) {
            Toast.makeText((getApplicationContext()),
                    "读数据错误", Toast.LENGTH_SHORT)
                    .show();
            Log.i(TAG,"读数据失败");
        }
    }

    //开启蓝牙端characteristic通知功能，蓝牙作为从机只能通过通知方式向主机（APP）发数据,app必须先开启通知功能才能接收数据
    public boolean enableNoti(BluetoothGatt gatt, final UUID serviceUuid, final UUID charUuid) {
        BluetoothGattService bleGattService = gatt.getService(serviceUuid);
        if (bleGattService == null) {
            Toast.makeText(getApplicationContext(),
                    "无按键服务", Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        BluetoothGattCharacteristic charUUID = bleGattService.getCharacteristic(charUuid);
        if (charUUID == null) {
            Toast.makeText(getApplicationContext(),
                    "无char特性", Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        return enableNotification(true, charUUID);
    }

    public boolean enableKeyNoti() {
        BluetoothGattService keyService = mGatt.getService(DATA_SERVICE_UUID);
        if (keyService == null) {
            Toast.makeText(getApplicationContext(),
                    "无按键服务", Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        BluetoothGattCharacteristic Key = keyService.getCharacteristic(HUMIDITY_TEMP_UUID);
        if (Key == null) {
            Toast.makeText(getApplicationContext(),
                    "无按键特性", Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        return enableNotification(true, Key);
    }

    //向蓝牙写数据函数,写单个字符
    public boolean writeCharacteristic(char data, UUID serviceUuid, UUID charUuid) {//
        BluetoothGattService bleGattService = getService(serviceUuid);//获取服务对象
        if (bleGattService != null) {
            BluetoothGattCharacteristic charValue = bleGattService.getCharacteristic(charUuid);
            //			Toast.makeText(getApplicationContext(),
            //					"获取服务对象", Toast.LENGTH_SHORT)
            //					.show();
            if (charValue != null) {
                charValue.setValue(data, BluetoothGattCharacteristic.FORMAT_UINT8, 0);//第二个参数必须设置对，是数据类型
                charValue.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                //				Toast.makeText(getApplicationContext(),
                //						"获取空调控制char", Toast.LENGTH_SHORT)
                //						.show();
                if (write(charValue))//写到远程服务端，开启通知
                    return true;
                //				Toast.makeText(getApplicationContext(),
                //						"开始写char1", Toast.LENGTH_SHORT)
                //						.show();
            }
        }
        return false;
    }

    public void close() {
        if (mGatt == null) {
            return;
        }
        if (mState == State.CONNECTED) {
            mGatt.disconnect();
            Toast.makeText(getApplicationContext(),
                    "断开连接", Toast.LENGTH_SHORT)
                    .show();
        }
        mGatt.close();
        mGatt = null;
        Toast.makeText(getApplicationContext(),
                "释放资源", Toast.LENGTH_SHORT)
                .show();
    }

    //延时断开连接函数,time(ms)是延迟时间
    public void periodDisconnect(final int time) {
        if (mState == State.CONNECTED && mGatt != null && !preference.getString("Mac", null).equals("A0:E6:F8:07:AD:23")) {
            //连接的是空调就不用断开，超出通信范围会自动断开
            action = ACT_DIS;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(time);
                        mGatt.disconnect();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    //空调控制命令，不同字符对应不同命令，在终端蓝牙已经对应处理
    public void airControl(Bundle data) {
        switch (data.getInt("command")) {
            case MINUS:
                writeCharacteristic('-', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                //				Toast.makeText(getApplicationContext(),"-", Toast.LENGTH_SHORT).show();
                break;
            case PLUS:
                writeCharacteristic('+', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                //				Toast.makeText(getApplicationContext(),"+", Toast.LENGTH_SHORT).show();
                break;
            case SWITCH:
                if (data.getBoolean("switch")) {
                    writeCharacteristic('f', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                    //					Toast.makeText(getApplicationContext(),"TURN OFF", Toast.LENGTH_SHORT).show();
                } else {
                    writeCharacteristic('o', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                    //					Toast.makeText(getApplicationContext(),"TURN ON", Toast.LENGTH_SHORT).show();
                }
                break;
            case MODE:
                switch (data.getInt("mode")) {
                    case 0:
                        writeCharacteristic('0', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                        //						Toast.makeText(getApplicationContext(),"制冷", Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        writeCharacteristic('1', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                        //						Toast.makeText(getApplicationContext(),"除湿", Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        writeCharacteristic('2', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                        //						Toast.makeText(getApplicationContext(),"制热", Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        writeCharacteristic('3', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                        //						Toast.makeText(getApplicationContext(),"自动", Toast.LENGTH_SHORT).show();
                        break;
                }
                break;
            case SPEED:
                switch (data.getInt("speed")) {
                    case 0:
                        writeCharacteristic('m', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                        //						Toast.makeText(getApplicationContext(),"中", Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        writeCharacteristic('h', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                        //						Toast.makeText(getApplicationContext(),"高", Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        writeCharacteristic('l', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                        //						Toast.makeText(getApplicationContext(),"低", Toast.LENGTH_SHORT).show();
                        break;
                }
                break;
            case DIRECTION:
                if (data.getBoolean("direction")) {
                    writeCharacteristic('w', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                    //					Toast.makeText(getApplicationContext(),"扫风关", Toast.LENGTH_SHORT).show();
                } else {
                    writeCharacteristic('W', DATA_SERVICE_UUID, AIR_CONTROL_UUID);
                    //					Toast.makeText(getApplicationContext(),"扫风", Toast.LENGTH_SHORT).show();
                }
                break;
            case COMFORT:
                writeCharacteristic((char) data.getInt("level_flag", 0), DATA_SERVICE_UUID, LEVEL_UUID);
                Toast.makeText(getApplicationContext(), "设置舒适等级：" + data.getInt("level_flag", 0), Toast.LENGTH_SHORT).show();
                break;
        }

    }

}
