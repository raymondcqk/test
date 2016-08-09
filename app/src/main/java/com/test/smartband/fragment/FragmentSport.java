package com.test.smartband.fragment;



import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVObject;
import com.avos.avoscloud.AVQuery;
import com.avos.avoscloud.FindCallback;
import com.avos.avoscloud.SaveCallback;
import com.test.smartband.R;

import java.util.List;

/**
 * Created by chuangfeng on 2016/4/24.
 */
public class FragmentSport extends Fragment implements View.OnClickListener{
    /*code qikang-暂时用这里做蓝牙开关等功能的界面*/
    private View view;
    private ImageButton ib_ble_switch;

    private boolean switch_ble;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_sport,container,false);
        initData();
        initView();
        return view;
    }
    private void initView() {
        ib_ble_switch = (ImageButton) view.findViewById(R.id.id_btn_ble_switch);
        ib_ble_switch.setOnClickListener(this);

    }

    private void initData() {
        switch_ble = false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.id_btn_ble_switch:
                //开关BLE

                //改变UI-开关状态图片

                if (switch_ble == false) {
                    switch_ble = true;
                    ib_ble_switch.setImageResource(R.drawable.switch_on);
                    /**
                     * LeanCloud 存储数据之云端
                     * 测试
                     */
                    AVObject ble = new AVObject("ble");
                    ble.put("device","Sample");
                    ble.put("user","Raymond");
                    ble.saveInBackground(new SaveCallback() {
                        @Override
                        public void done(AVException e) {
                            if (e==null){
                                Toast.makeText(getContext(),"网络同步成功",Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    //end 测试
                } else {
                    switch_ble = false;
                    ib_ble_switch.setImageResource(R.drawable.switch_off);

                    //LeanCloud 从云端查询数据
                    AVQuery<AVObject> query = new AVQuery<AVObject>("ble");
                    query.whereEqualTo("user","Raymond");
                    query.findInBackground(new FindCallback<AVObject>() {
                        @Override
                        public void done(List<AVObject> list, AVException e) {
                            if (e==null){
                                int i = 0;
                                for (AVObject object:list) {
                                    Toast.makeText(getContext(),i+"_user："+object.get("user")+" device："+object.get("device"),Toast.LENGTH_SHORT).show();
                                    i++;
                                }
                            }
                        }
                    });//end query test from LeanCloud

                }
                break;
        }
    }
}
