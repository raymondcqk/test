package com.test.smartband.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.SDKInitializer;
import com.test.smartband.R;
import com.test.smartband.controlsubfrags.FragmentSubAirCon;
import com.test.smartband.fragment.FragmentControl;
import com.test.smartband.fragment.FragmentHome;
import com.test.smartband.fragment.FragmentSport;

import java.lang.ref.WeakReference;

/**
 * 0-从OnCreate中开始看程序执行流程
 * 1- 实现 FragmentSubAirCon.airControl 接口，当空调Fragment点击按钮时，在该活动中完成代码
 * 2- service在后台负责与蓝牙通信,并将蓝牙状态，通信数据传给Activity,
 * 然后由Activity分发给不同Fragment进行数据处理。Fragment也可通过MainActivity向Service传递消息
 * 3-跨进程通信<-->基于消息的进程间通信的方式：参考：http://blog.csdn.net/lmj623565791/article/details/47017485
 * Messenger,引用了一个Handler对象，以便其他Messenger对象能够向它发送消息（mMessenger.send(msg)）
 * 该类允许跨进程间基于Message的通信（两个进程间可以通过Message通信）
 * 服务端-Handler-创建Messenger；客户端-持有该Messenger-即可与服务端通信
 * ！记录客户端对象的Messenger，然后可以实现一对多的通信！
 */
public class MainActivity extends FragmentActivity implements FragmentSubAirCon.airControl {

    //Log Tag
    private static final String TAG = "*==MainActivity==*";

    /*参考：http://blog.csdn.net/lmj623565791/article/details/47017485
    class: Messenger
    This allows for the implementation of message-based communication across processes
    允许实现基于消息的进程间通信的方式。
    */

    //用来启动service
    private Intent mServiceIntent;
    /**
     * Message 是在线程之间传递的消息，它可以在内部携带少量的信息，用于在不同线程之间交换数据
     * Message对象可以被发送给android.os.Handler处理
     * 属性字段：arg1、arg2、what、obj、replyTo等；其中arg1和arg2是用来存放整型数据的；what是用来保存消息标示的；
     * obj是Object类型的任意对象；replyTo是消息管理器，会关联到一个handler，handler就是处理其中的消息。
     * 通常对Message对象不是直接new出来的，只要调用handler中的obtainMessage方法来直接获得Message对象
     */
    //用来与Service 通信--->信使
    private final Messenger mMessenger;
    //原名：mService作为messenger传过来Service对象的引用，以便进行相关处理
    private Messenger mServiceMessenger = null;
    //状态机标志，控制UI界面的更新以及相关蓝牙操作（自定义服务BleService，处理蓝牙扫描工作及结果）
    private BleService.State mState = BleService.State.UNKNOWN;
    //代表一台蓝牙设备（手环或终端）
    private static BluetoothDevice Device;

    //简单数据存储操作
    private SharedPreferences preference;
    private Editor editor;
    //新数据标志
    private static boolean newData = true;
    //Provides access to information about the telephony services on the device
    //Applications can use the methods in this class to determine telephony services and states
    public TelephonyManager telManager;
    //通话状态监听器，自定义继承自extends PhoneStateListener
    public TelListner listener;
    //音频管理器
    public AudioManager audioManager;

    //主页显示控件
    public static TextView body_t, room_t, humi, cal, step, connect, air_condition;
    private ProgressBar pb;


    public MainActivity() {
        super();
        //构造函数中创建“信使”，用于与Service通信
        //关联到一个Handler-->自定义的IncomingHandler，指定使用该handler子类来处理消息
        mMessenger = new Messenger(new IncomingHandler(this));
    }

    /**
     * 20160528 -- 继续理解这个机制
     * mConnection是Activity与Service绑定时参数之一
     * 绑定后会回调onServiceConnected函数
     * 其中IBiner即为Service传过来的信使，信使引用了Service中的handler
     * 所以可以利用它向Service发送消息。
     * handler同时弱引用Service对象，所以在handler中也能调用Service相关函数
     */

    /**
     * 第二步
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        /**
         * 绑定服务时，将mConnection作为第二个参数传进去，当绑定成功，立即回调下面的onServiceConnected（），
         * 获得服务信使，实现活动与服务通信，活动要服务干嘛就干嘛，这里用于获取蓝牙信息
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //获得服务信使，实现活动与服务通信，活动要服务干嘛就干嘛，
            mServiceMessenger = new Messenger(service);//获得BleService的Messenger引用
            /**
             * 第三步
             * 确认获得Service的Messenger后，发送注册消息给Service的Messenger，使Service获得Activity的Messenger，达成双向通信基础
             */
            if (mServiceMessenger != null) {
                Log.i(TAG, "--->onServiceConnected() 服务信使Messenger获取成功，可通过此向Service发送消息数据");
                sendMessage(BleService.MSG_REGISTER);//发送注册消息后即可以与Service进行通信，消息处理在service handleMessage中
                sendMessage(BleService.MSG_START_SCAN);//绑定并注册后开始搜索
                //sendMessage(BleService.MSG_LOCATE);
            }

        }

        @Override//此处具体作用不知
        public void onServiceDisconnected(ComponentName name) {
            mServiceMessenger = null;//取消绑定，销毁指针,关闭服务，断开与服务通信的Messenger
            //Toast.makeText(MainActivity.this, "断开后台服务", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "--->onServiceDisconnected()");
        }
    };

    //发送消息-->通知服务处理相关操作: 发命令消息的
    void sendMessage(final int Msg) {
        try {

            /**
             * 尽管Message的构造器是公开的，但是获取Message对象的最好方法是
             * 调用Message.obtain()或者Handler.obtainMessage()
             * 这样是从一个可回收对象池中获取Message对象。
             */

            Message msg = Message.obtain(null, Msg);//注册消息-->service收到该消息会进行注册处理
            if (msg != null) {
                //将Activity中handler的信使对象传递过去，这样Service即可用它向Activity发送消息
                msg.replyTo = mMessenger;//指明此message发送到何处的可选Messenger对象。具体的使用方法由发送者和接受者决定。
                //发送消息，进入Service,开始处理
                mServiceMessenger.send(msg);//往服务端发送消息
                Log.i(TAG, "--->sendMessage(final int Msg) 发送命令消息 代号：" + Msg);
            } else {
                //mServiceMessenger = null;//发送失败，销毁引用指针，但原来的Service不一定销毁
                Toast.makeText(this, "Message生成失败 代号" + Msg, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Message生成失败 代号" + Msg);
            }
        } catch (Exception e) {
            // Log.w(TAG, "Error connecting to BleService", e);
            // mServiceMessenger = null;
        }
    }

    /**
     * @param Msg    不同消息常量
     * @param data   要传递的数据
     * @param server 接收消息的服务端
     * @param client 发送消息的客户端
     */
    private void sendMessage(final int Msg, Bundle data, Messenger server, Messenger client) {

        if (data != null) {
            Message msg = Message.obtain(null, Msg);
            if (msg != null) {
                msg.setData(data);
                try {
                    msg.replyTo = client;
                    server.send(msg);//发送消息，进入Service,开始处理
                } catch (Exception e) {
                    // Log.w(TAG, "Error connecting to BleService", e);
                    //server = null;
                }
            } else {
                Toast.makeText(this, "sendMessage（）数据消息发送失败 代号：" + Msg + " 原因：Message 为空", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "sendMessage（）数据消息发送失败 代号：" + Msg + " 原因：Message 为空");
            }
        } else {
            //server = null;//发送失败，销毁引用指针，但原来的Service不一定销毁
            Toast.makeText(this, "sendMessage（）数据消息发送失败 代号：" + Msg + " 原因：Bundle 数据为空", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "sendMessage（）数据消息发送失败 代号：" + Msg + " 原因：Bundle 数据为空");
        }
    }


    /////////////////////////////////////////////////////////////////////////////////////
    private FragmentHome homeFragment;
    private FragmentSport sportFragment;
    private FragmentControl controlFragment;
    private Fragment[] fragments;
    private ImageView[] imagebuttons;
    private TextView[] textviews;
    private int index;
    private int currentTabIndex;


    /**
     * 初始化变量
     *
     * @其康
     */
    //@其康
    private Context context;
    private FragmentManager fragmentManager;

    private void initArgs() {
        fragmentManager = getSupportFragmentManager();

    }

    private void initView() {
        homeFragment = new FragmentHome();
        sportFragment = new FragmentSport();
        controlFragment = new FragmentControl();

        fragments = new Fragment[]{
                homeFragment, sportFragment, controlFragment
        };

        imagebuttons = new ImageView[3];
        imagebuttons[0] = (ImageView) findViewById(R.id.iv_home);
        imagebuttons[1] = (ImageView) findViewById(R.id.iv_sport);
        imagebuttons[2] = (ImageView) findViewById(R.id.iv_control);
        imagebuttons[0].setSelected(true);

        textviews = new TextView[3];
        textviews[0] = (TextView) findViewById(R.id.tv_home);
        textviews[1] = (TextView) findViewById(R.id.tv_sport);
        textviews[2] = (TextView) findViewById(R.id.tv_control);
        textviews[0].setTextColor(0xffea8010);
        // 开启Fragment事务，添加三个主的Fragment，并显示第一个fragment
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, homeFragment, "home")
                .add(R.id.fragment_container, sportFragment, "sport")
                .add(R.id.fragment_container, controlFragment, "control")
                .hide(sportFragment).hide(controlFragment)
                .show(homeFragment)
                .commit();
        findView();
        //Log.i("debug", "findView()");
    }

    public void onTabClicked(View view) {
        switch (view.getId()) {
            case R.id.re_home:
                index = 0;
                break;
            case R.id.re_sport:
                index = 1;
                break;
            case R.id.re_control:
                index = 2;
                break;
            default:
                break;
        }

        if (currentTabIndex != index) {
            FragmentTransaction fts = getSupportFragmentManager().beginTransaction();
            fts.hide(fragments[currentTabIndex]);
            if (!fragments[index].isAdded()) {
                fts.add(R.id.fragment_container, fragments[index]);
            }
            fts.show(fragments[index]).commit();
        }
        //变更Tab显示图标
        imagebuttons[currentTabIndex].setSelected(false);
        imagebuttons[index].setSelected(true);
        textviews[currentTabIndex].setTextColor(0xFF999999);
        textviews[index].setTextColor(0xFFea8010);
        currentTabIndex = index;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //全局初始化百度SDK
        SDKInitializer.initialize(getApplicationContext());

        // 测试 LeanCloud SDK 是否正常工作的代码
        //        AVObject testObject = new AVObject("TestObject");
        //        testObject.put("ray", "Raymond is a great boy");
        //        testObject.saveInBackground(new SaveCallback() {
        //            @Override
        //            public void done(AVException e) {
        //                if (e == null) {
        //                    Log.d("saved", "success!");
        //                }
        //            }
        //        });

        setContentView(R.layout.activity_main);
        initView();//初始化ui控件
        bleVerify();//检查手机是否用于蓝牙模块


        //打电话发短信相关操作
        context = getApplicationContext();
        telManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        listener = new TelListner(context, audioManager);
        telManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);

        /**
         * 绑定后台服务并注册,绑定服务后会调用service中的onBind，服务绑定后会执行mConnection中的两个重写的方法
         * 在重写的方法中向service注册之后开启搜索
         */

        /*第一步*/
        bindService();


    }

    @Override
    protected void onResume() {
        super.onResume();
        //绑定Fragment控件以便显示数据
        findView();
        Log.d("debug", "onResume()");
    }

    @Override
    protected void onDestroy() {
        stopService(mServiceIntent);
        unbindService(mConnection);
        //sendMessage(BleService.MSG_UNREGISTER);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("退出提示");
        dialog.setMessage("是否要退出手环程序？");
        dialog.setCancelable(false);
        dialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                System.exit(0);//调用这个函数才能释放蓝牙资源
                /*finish是Activity的类，仅仅针对Activity，当调用finish()时，
                只是将活动推向后台，并没有立即释放内存，活动的资源并没有被清理；
                当调用System.exit(0)时，杀死了整个进程，这时候活动所占的资源也会被释放。*/
            }
        });
        dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }
        );
        dialog.show();
    }

    //获取主页控件id
    private void findView() {
        pb = (ProgressBar) findViewById(R.id.connectStateProgress);
        body_t = (TextView) findViewById(R.id.body_temperature);
        room_t = (TextView) findViewById(R.id.room_temperature);
        humi = (TextView) findViewById(R.id.humidity);
        step = (TextView) findViewById(R.id.run);
        cal = (TextView) findViewById(R.id.calorie);
        connect = (TextView) findViewById(R.id.connectState);
        air_condition = (TextView) findViewById(R.id.air_condition);
    }

    /**
     * service发过来的消息处理函数。service在后台负责与蓝牙通信,并将蓝牙状态，通信数据传给Activity,
     * 然后由Activity分发给不同Fragment进行数据处理。Fragment也可通过MainActivity向Service传递消息
     */
    public static class IncomingHandler extends Handler {
        /**
         * Handler处理者的意思，它主要是用于发送和处理消息的
         * sendMessage(): 发送消息一般是使用 Handler 的 sendMessage()方法，
         * 而发出的消息经过一系列地辗转处理后，
         * handleMessage(): 最终会传递到 Handler 的 handleMessage()方法中
         */

        //WeakReference弱引用 参考：http://www.tuicool.com/articles/imyueq
        /**
         * 相对于strong reference: Object c = new Car(); //只要c还指向car object, car object就不会被回收
         * 当一个对象仅仅被weak reference指向, 而没有任何其他strong reference指向的时候, 如果GC运行, 那么这个对象就会被回收.
         * weak reference的语法是:
         * WeakReference<Car> weakCar = new WeakReference(Car)(car);
         * 当要获得weak reference引用的object时, 首先需要判断它是否已经被回收:
         * weakCar.get(); 若返回空 则被GC回收
         * 但此处将主活动通过弱引用来创建对象，暂时不知用以何在 20160527
         * 20160528 弱引用防止内存泄露，有没有必要呢？以后深究
         */
        private final WeakReference<MainActivity> mActivity;

        //IncomingHandler构造器
        public IncomingHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);//弱引用防止内存泄露
        }

        //处理来自service的消息
        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case BleService.MSG_STATE_CHANGED://处理连接状态改变消息
                        //从0到msg.arg1遍历这样就找到对应状态
                        activity.stateChanged(BleService.State.values()[msg.arg1]);
                        break;
                    case BleService.MSG_READ_CHARACTERISTIC://读数据通知，开始更新
                        Bundle data = msg.getData();//数据载体
                        activity.showData(data);
                        break;
                    case BleService.MSG_CONNECT_KIND://处理连接状态改变消息
                        break;
                }
                super.handleMessage(msg);
            }
        }
    }

    //显示连接状态的变化
    private void stateChanged(BleService.State newState) {
        mState = newState;
        switch (mState) {
            //全部显示未连接
            case SCANNING:
                connect.setText("正在搜索");
                pb.setVisibility(View.VISIBLE);
                break;
            case IDLE:
                connect.setText("未连接");
                break;
            case CONNECTING:
                connect.setText("正在连接");
                pb.setVisibility(View.VISIBLE);
                break;
            //显示已连接
            case CONNECTED:
                connect.setText("已连接");
                pb.setVisibility(View.GONE);
                break;
            default:
                break;
        }

    }

    //更新数据函数,如果接收数据格式处理不当且没添加try-catch会导致app闪退，必须保证格式处理正确
    public void showData(Bundle data) {
        if (data!= null){
             byte[] value;
            if (data.containsKey("POWEWR")) {//显示电量------->失效
            //以Bundle键值判断数据类型，在service接收到对应数据时写入相应键值中，数据以字节数据存储如蓝牙发送字符's''t'，则接收顺序为't''s',以倒序接收
                value = data.getByteArray("POWER");
                //待添加 value为16进制，需要转换
                Toast.makeText(getApplicationContext(), "" + value, Toast.LENGTH_SHORT).show();
            } else if (data.containsKey("TEMP")) {//显示体温------>三个字节，0,1为体温，2为电量
                value = data.getByteArray("TEMP");//读到的数据
                byte temp[] = {value[1], value[0]};
                body_t.setText("" + Integer.parseInt(MainActivity.toString(temp), 16) / 100.0);
                Toast.makeText(getApplicationContext(), "电量" + value[2], Toast.LENGTH_SHORT).show();
            } else if (data.containsKey("CALORIE")) {//显示计步信息
                value = data.getByteArray("CALORIE");
                //处理方法
            } else if (data.containsKey("STEP")) {//显示计步信息
                value = data.getByteArray("STEP");
                int step_count = (value[0] - 48) * 10000 + (value[1] - 48) * 1000 + (value[2] - 48) * 100 + (value[3] - 48) * 10 + (value[4] - 48);
                //处理方法
                step.setText(String.valueOf(step_count));
            } else if (data.containsKey("TERMINAL")) {//显示温湿度,体温
                value = data.getByteArray("TERMINAL");
                byte b_temp[] = {value[1], value[0]};
                byte humidity[] = {value[3], value[2]};
                byte temp[] = {value[5], value[4]};
                byte air_temp[] = {value[7], value[6]};
                int shidu = Integer.parseInt(MainActivity.toString(humidity), 16);
                int temprature = Integer.parseInt(MainActivity.toString(temp), 16);
                int body_temp = Integer.parseInt(MainActivity.toString(b_temp), 16);
                int air_value = Integer.parseInt(MainActivity.toString(air_temp), 16);
                //处理方法
                room_t.setText("" + temprature / 10.0);
                humi.setText("" + shidu / 10.0);
                body_t.setText("" + body_temp / 100.0);
                air_condition.setText("" + air_value);
            }

        }
        
    }

    //字节变16进制字符，一个字节8位，一个16进制字符2个字节
    public static String toString(byte[] bytes) {
        final String HEX = "0123456789abcdef";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // 取出这个字节的高4位，然后与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt((b >> 4) & 0x0f));
            // 取出这个字节的低位，与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt(b & 0x0f));
        }

        return sb.toString();
    }

    //蓝牙兼容判断
    public void bleVerify() {
        //一开始判断手机是否支持蓝牙BLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "手机不支持蓝牙Ble", Toast.LENGTH_LONG).show();
            Log.i(TAG, "手机蓝牙设备检测：支持蓝牙BLE");
            //finish();//不支持即结束
        } else {
            Toast.makeText(this, "手机支持蓝牙Ble", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "手机蓝牙设备检测：支持蓝牙BLE");
        }
    }


    //启动服务并注册到服务
    public void bindService() {
        //绑定后台服务
        mServiceIntent = new Intent(this, BleService.class);//启动BleService的Intent
        startService(mServiceIntent);//启动服务，注意在一定时候销毁服务，节省功耗
        /**
         * 绑定服务，用于Activity与Service通信
         * 绑定service，触发mConnection中的回调函数，并触发Service中的OnBind函数，
         * 将Service中的信使引用传给回调函数。
         */
        bindService(mServiceIntent, mConnection, BIND_AUTO_CREATE);
    }

    //空调控制函数
    @Override
    public void airCommand(View view, Bundle data) {

        Bundle command = new Bundle();
        int intTemper = data.getInt("intTemper");

        //开关标志
        Boolean switchFlag = data.getBoolean("switchFlag");
        //摆风开关
        boolean directionFlag = data.getBoolean("directionFlag");

        //空调模式标志
        int mode_flag = data.getInt("mode_flag");
        //风速模式标志
        int speed_flag = data.getInt("speed_flag");
        //舒适度等级
        int level_flag = data.getInt("level_flag");
        switch (view.getId()) {
            //温度 减
            case R.id.id_air_minus:
                command.putInt("command", BleService.MINUS);
                //tv_temper.setText(intTemper + "℃");
                break;
            //温度 加
            case R.id.id_air_plus:
                command.putInt("command", BleService.PLUS);
                break;
            //空调开关
            case R.id.btn_air_switch:
                command.putInt("command", BleService.SWITCH);
                command.putBoolean("switch", switchFlag);
                break;
            //空调模式
            case R.id.id_btn_mode:
                command.putInt("command", BleService.MODE);
                command.putInt("mode", mode_flag);
                break;
            //风速
            case R.id.id_btn_speed:
                command.putInt("command", BleService.SPEED);
                command.putInt("speed", speed_flag);
                break;
            //扫风
            case R.id.id_btn_direction:
                command.putInt("command", BleService.DIRECTION);
                command.putBoolean("direction", directionFlag);
                break;
            case R.id.locate:
                sendMessage(BleService.MSG_LOCATE);
                Toast.makeText(this, "定位", Toast.LENGTH_LONG).show();
                break;
            case R.id.send:
                command.putInt("command", BleService.COMFORT);
                command.putInt("level_flag", level_flag);
                //    Toast.makeText(this,"设置舒适等级", Toast.LENGTH_SHORT).show();
                break;
        }
        sendMessage(BleService.MSG_AIR_CONTROL, command, mServiceMessenger, mMessenger);//发送控制指令 到Service

    }

}
