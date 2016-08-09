package com.test.smartband.fragment;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.test.smartband.R;
import com.test.smartband.activity.LocationData;

import java.util.ArrayList;

/**
 * Created by chuangfeng on 2016/4/24.
 */
public class FragmentHome extends Fragment  {

    private View view;
    private TextView tv_humidity;
    private TextView tv_roomRemperature;
    private TextView tv_bodyTemperature;
    private TextView tv_runAccount;
    private TextView tv_calorie;
    private TextView tv_air_condition;

    private TextWatcher textWatcher;

    //紧急求助测试
    private EditText phone;
    private Button help;
    private Context context;
    private SharedPreferences pre;
    private SharedPreferences.Editor editor;
    public LocationData handleData;//处理定位数据的类

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        try{

            pre = activity.getSharedPreferences("LOCATIONDATA", Context.MODE_PRIVATE);
            editor = pre.edit();

            context = getActivity().getApplicationContext();
            handleData = new LocationData(context, pre);

        }catch(ClassCastException e){
            throw new ClassCastException(activity.toString()+"must implement OnArticleSelectedListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);

        initView();
        updateData();
        return view;
    }
    //更新数据
    private void updateData() {

    }

    private void initView() {
        tv_bodyTemperature = (TextView) view.findViewById(R.id.body_temperature);
        tv_roomRemperature = (TextView) view.findViewById(R.id.room_temperature);
        tv_humidity = (TextView) view.findViewById(R.id.humidity);
        tv_runAccount = (TextView) view.findViewById(R.id.run);
        tv_calorie = (TextView) view.findViewById(R.id.calorie);
        tv_air_condition = (TextView) view.findViewById(R.id.air_condition);


        phone = (EditText)view.findViewById(R.id.phone);
        help = (Button)view.findViewById(R.id.help);

        textWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //存储数据
                editor.putString("PHONE", phone.getText().toString());
                editor.commit();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //存储数据
                editor.putString("PHONE", phone.getText().toString());
                editor.commit();
            }
            @Override
            public void afterTextChanged(Editable s) {
                //存储数据
                editor.putString("PHONE", phone.getText().toString());
                editor.commit();
            }
        };

        //紧急求助测试
        phone.setText(pre.getString("PHONE",null));
        phone.setOnEditorActionListener(
                new TextView.OnEditorActionListener(){
                    @Override
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event){
                        if(!TextUtils.isEmpty(phone.getText().toString())){
                            if (actionId == EditorInfo.IME_ACTION_DONE)
                            {
                                //存储数据
                                editor.putString("PHONE", phone.getText().toString());
                                editor.commit();
                                //取消焦点
                                InputMethodManager imm =(InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(phone.getWindowToken(), 0);
                                phone.clearFocus();
                                Toast.makeText(getActivity(),"已设置", Toast.LENGTH_SHORT).show();
                            }
                        }
                        else{
                            editor.putString("PHONE", null);
                            editor.commit();
                            Toast.makeText(getActivity(),"联系人不能为空", Toast.LENGTH_SHORT).show();//联系人不为空
                        }
                        return false;
                    }
                });

        help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("test","短信测试");
                //发送短信
                if(pre.getString("PHONE",null) != null){

                    //短信内容
                    String content = "纬度："+ handleData.getLat(handleData.getIndex()) +"经度：" +
                            handleData.getLon(handleData.getIndex()) + "时间：" + handleData.getTime(handleData.getIndex()) + "地点：" + handleData.getAddr(handleData.getIndex());
                    //发短信
                    SmsManager smsManager = SmsManager.getDefault();
                    ArrayList<String> texts = smsManager.divideMessage(content);//拆分短信,短信字数太多了的时候要分几次发
                    for(String text : texts){
                        smsManager.sendTextMessage(pre.getString("PHONE",null), null, text, null, null);//发送短信,mobile是对方手机号
                    }
                    Toast.makeText(getActivity(),"发送短信", Toast.LENGTH_SHORT).show();//联系人不为空
                }
                //打电话
                if(pre.getString("PHONE",null) != null)
                {
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.CALL");
                    intent.setData(Uri.parse("tel:"+ pre.getString("PHONE",null)));//mobile为你要拨打的电话号码，模拟器中为模拟器编号也可
                    startActivity(intent);
                    Toast.makeText(getActivity(),"打电话", Toast.LENGTH_SHORT).show();//联系人不为空
                }
            }
        });
    }

}
