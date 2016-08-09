package com.test.smartband.controlsubfrags;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.test.smartband.R;

/**
 * Created by Administrator on 2016/5/3 0003.
 */
public class FragmentSubAirCon extends Fragment implements View.OnClickListener {

    //
    private static final String KEY_CONTENT = "空调遥控Fragment";
    //用于储存“什么”数据
    private Bundle bundle;

    //
    private View view;

    private Button btn_temper_minus;
    private Button btn_temper_plus;
    private TextView tv_temper;

    //模式button
    private Button btn_mode;
    //风速button
    private Button btn_speed;
    //风向button
    private Button btn_direction;

    //模式的控件
    private ImageView img_mode;
    private TextView tv_mode;
    //风速的控件
    private ImageView img_speed;
    private TextView tv_speed;

    private Button btn_air_switch;


    private int intTemper;

    //开关标志
    Boolean switchFlag;
    //摆风开关
    boolean directionFlag;

    //空调模式标志
    int mode_flag;
    //风速模式标志
    int speed_flag;

    //舒适度等级
    int comfort_level = 0;

    //定位控件
    public EditText sizeIndex,locateIndex;
    public Button locate,readIndex,setSize,locateData;
    public TextView dataText;
    public SharedPreferences pre;
    public SharedPreferences.Editor editor;

    private airControl command;

    //舒适度参数
    public Button send;
    public EditText level;

    public interface airControl{//空调控制接口
        public void airCommand(View view,Bundle data);
    }

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        try{
            command =(airControl)activity;
            pre = activity.getSharedPreferences("LOCATIONDATA", Context.MODE_PRIVATE);
            editor = pre.edit();
        }catch(ClassCastException e){
            throw new ClassCastException(activity.toString()+"must implement OnArticleSelectedListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ((savedInstanceState != null) && savedInstanceState.containsKey(KEY_CONTENT)) {
            bundle = savedInstanceState.getBundle(KEY_CONTENT);
        }
    }

    /*
    * UI程序在这里写*/
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_aircon, container, false);
        viewInit();
        dataInit();
        return view;
    }


    private void viewInit() {

        btn_temper_minus = (Button) view.findViewById(R.id.id_air_minus);
        btn_temper_plus = (Button) view.findViewById(R.id.id_air_plus);
        btn_air_switch = (Button) view.findViewById(R.id.btn_air_switch);
        btn_mode = (Button) view.findViewById(R.id.id_btn_mode);
        btn_speed = (Button) view.findViewById(R.id.id_btn_speed);
        btn_direction = (Button) view.findViewById(R.id.id_btn_direction);

        img_mode = (ImageView) view.findViewById(R.id.id_img_air_mode);
        tv_mode = (TextView) view.findViewById(R.id.id_tv_air_mode);
        img_speed = (ImageView) view.findViewById(R.id.id_img_air_speed);
        tv_speed = (TextView) view.findViewById(R.id.id_tv_air_speed);

        tv_temper = (TextView) view.findViewById(R.id.id_tv_air_temperature);
        tv_temper.setText(intTemper + "℃");

        //定位控件
        locate = (Button)view.findViewById(R.id.locate);

        //舒适度
        level = (EditText)view.findViewById(R.id.level);
        send = (Button)view.findViewById(R.id.send);

        send.setOnClickListener(this);

        btn_temper_minus.setOnClickListener(this);
        btn_temper_plus.setOnClickListener(this);
        btn_air_switch.setOnClickListener(this);
        btn_mode.setOnClickListener(this);
        btn_speed.setOnClickListener(this);
        btn_direction.setOnClickListener(this);

        locate.setOnClickListener(this);

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(KEY_CONTENT, bundle);
    }

    void dataInit() {
        intTemper = 25;
        switchFlag = false;
        acInit();
    }

    public void acInit() {
        //遥控器值初始化 -->空调初始化 25摄氏度、制冷、中速
        mode_flag = 1;      //制冷模式
        speed_flag = 1;     //中速
        intTemper = 25;     //25摄氏度
        //更新UI
        img_mode.setImageResource(R.drawable.ac_cool);
        tv_mode.setText("制冷");
        img_speed.setImageResource(R.drawable.speed_m);
        tv_speed.setText("中");
        tv_temper.setText(intTemper + "℃");

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            //温度 减
            case R.id.id_air_minus:
                intTemper--;
                if (intTemper < 16) {
                    Toast.makeText(getActivity(), "温度范围16~30℃", Toast.LENGTH_SHORT).show();
                    intTemper++;
                    return;
                }
                tv_temper.setText(intTemper + "℃");
                break;
            //温度 加
            case R.id.id_air_plus:
                intTemper++;
                if (intTemper > 30) {
                    Toast.makeText(getActivity(), "温度范围16~30℃", Toast.LENGTH_SHORT).show();
                    intTemper--;
                    return;
                }
                tv_temper.setText(intTemper + "℃");
                break;
            //空调开关
            case R.id.btn_air_switch:
                if (switchFlag == false) {
                    switchFlag = true;
                    btn_air_switch.setBackgroundResource(R.drawable.power_on);
                    //空调初始化 25摄氏度、制冷、中速
                    acInit();
                } else {
                    switchFlag = false;
                    btn_air_switch.setBackgroundResource(R.drawable.power_off);
                }
                break;
            //空调模式
            case R.id.id_btn_mode:
                switch (mode_flag) {
                    case 0:
                        img_mode.setImageResource(R.drawable.ac_cool);
                        tv_mode.setText("制冷");
                        mode_flag++;
                        break;
                    case 1:
                        img_mode.setImageResource(R.drawable.ac_chushi);
                        tv_mode.setText("除湿");
                        mode_flag++;
                        break;
                    case 2:
                        img_mode.setImageResource(R.drawable.ac_hotter);
                        tv_mode.setText("制热");
                        mode_flag++;
                        break;
                    case 3:
                        img_mode.setImageResource(R.drawable.ac_auto);
                        tv_mode.setText("自动");
                        mode_flag = 0;
                        break;
                }

                break;
            //风速
            case R.id.id_btn_speed:
                switch (speed_flag){
                    case 0:
                        img_speed.setImageResource(R.drawable.speed_m);
                        tv_speed.setText("中");
                        speed_flag++;
                        break;
                    case 1:
                        img_speed.setImageResource(R.drawable.speed_l);
                        tv_speed.setText("高");
                        speed_flag++;
                        break;
                    case 2:
                        img_speed.setImageResource(R.drawable.speed_s);
                        tv_speed.setText("低");
                        speed_flag = 0;
                        break;
                }
                break;
            //扫风
            case R.id.id_btn_direction:
                if (directionFlag == false){
                    btn_direction.setBackgroundResource(R.drawable.button_press_show);
                    directionFlag = true;
                }else {
                    btn_direction.setBackgroundResource(R.drawable.btn_ac_swing_selector);
                    directionFlag = false;
                }
                break;
            //
            case R.id.send:
                comfort_level = Integer.parseInt(level.getText().toString());
               // Toast.makeText(getActivity(), "数字"+comfort_level, Toast.LENGTH_SHORT).show();
                break;
            default:
                //用Bundle将数据指令及界面传递到MainActivity处理
                Bundle data = new Bundle();
                data.putInt("intTemper",intTemper);
                data.putBoolean("switchFlag",switchFlag);
                data.putInt("mode_flag",mode_flag);
                data.putInt("speed_flag",speed_flag);
                data.putBoolean("directionFlag",directionFlag);
                data.putInt("level_flag",comfort_level);
                command.airCommand(v, data);//发送控件id
                break;
        }
    }

    private void checkTemper() {
        if (intTemper <= 16 || intTemper >= 30) {
            Toast.makeText(getActivity(), "温度范围16~30℃", Toast.LENGTH_SHORT).show();
            return;
        }
    }


}
