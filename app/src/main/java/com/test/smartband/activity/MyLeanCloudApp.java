package com.test.smartband.activity;

import android.app.Application;

import com.avos.avoscloud.AVOSCloud;

/**
 * Created by 陈其康 raymondchan on 2016/6/11 0011.
 */
public class MyLeanCloudApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化参数依次为 this, AppId, AppKey
        AVOSCloud.initialize(this,"Q9qKj8lSDkhagl1sPCWs9So1-gzGzoHsz","tePrM1FTPv8LCfDzUdGKVvWr");

    }
}
