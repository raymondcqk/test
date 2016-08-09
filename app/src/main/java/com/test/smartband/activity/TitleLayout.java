package com.test.smartband.activity;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.test.smartband.R;

/**
 * Created by chuangfeng on 2016/4/27.
 */
public class TitleLayout extends LinearLayout {
    public TitleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.title,this);
        Button titlePerson = (Button) findViewById(R.id.title_person);
        Button titleMenu = (Button) findViewById(R.id.title_menu);
        titlePerson.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "我的个人信息", Toast.LENGTH_SHORT).show();
            }
        });
        titleMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(),"菜单",Toast.LENGTH_SHORT).show();
            }
        });
    }
}
