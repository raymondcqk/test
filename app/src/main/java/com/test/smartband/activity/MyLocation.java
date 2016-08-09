package com.test.smartband.activity;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.Preference;
import android.provider.Settings;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;

import java.text.DateFormat;
import java.util.Date;

/**
 * 实现实位回调监听
 */
public class MyLocation extends Application implements BDLocationListener,OnGetGeoCoderResultListener {

    private LocationClientOption mOption = null;
    private int dataSize = 10;
    private int index = 0;//默认可存储10个定位数据
    private String latitude;//经纬度
    private String longitude;
    private String time;//时间
    private  String addr;//地址
    private LocationClient mLocClient;//定位的客户端
    private boolean isSuccess = false;//是否是最新一次定位成功的数据
    private LocationData handleData;//处理定位数据的类
    private LatLng ptCenter;//经纬度
    private Intent intent;
    private GeoCoder mSearch = null; //搜索模块用于获取详细地址
    MapStatus status;

    Context mContext;

    public MyLocation(Context context,LocationData locationData){
        mContext = context;
        handleData = locationData;

        mOption = new LocationClientOption();//默认定位方式设置
        mOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);// 设置定位模式,这里可以通过按键选择定位方式
        mOption.setOpenGps(true);// 打开gps
        mOption.setCoorType("bd09ll"); // 设置坐标类型
        mOption.setIsNeedAddress(true);
        mOption.setNeedDeviceDirect(true);//返回的定位结果包含手机机头的方向

//        mSearch.setOnGetGeoCodeResultListener(this);

        mLocClient = new LocationClient(mContext);//创建定位类
        mLocClient.registerLocationListener(this);//注册定位监听函数，接收定位后的结果
    }

    //开始定位，并设置定位回调函数
    public void startLocation(LocationClientOption option){
        if(option == null){//为null则使用默认定位方式
            option = mOption;
            Toast.makeText(mContext,"默认定位方式", Toast.LENGTH_SHORT).show();
        }
        // 定位初始化
        isSuccess = false;
        mLocClient.setLocOption(option);//设置定位参数
        mLocClient.start();//开始定位
    }

    @Override
    public void onReceiveLocation(BDLocation location) {
        addr = location.getAddrStr();//获取地址信息
        //获取时间
        if(location.getBuildingID()!=null)
            addr = addr + location.getBuildingID();
        if(location.getLocationDescribe()!=null)
            addr = addr + location.getLocationDescribe();
        Date now = new Date();
        DateFormat data = DateFormat.getDateTimeInstance(); //显示日期，时间（精确到分）
        time = data.format(now);//与SHORT风格相比，这种方式最好用
        //获取经纬度
        latitude = String.valueOf( location.getLatitude() );
        longitude = String.valueOf( location.getLongitude() );
        isSuccess = true;
        LatLng ptCenter = new LatLng(location.getLatitude(), location.getLongitude());
        mLocClient.stop();//停止定位
        handleData.putLocationData(time,addr,longitude,latitude);
        Toast.makeText(mContext,latitude+location.getAddrStr()+handleData.getAddr(handleData.getIndex()), Toast.LENGTH_SHORT).show();
    }

    //没调用
    //地址编码与反编码接口
    @Override
    public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
        if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(mContext,"未找到结果" , Toast.LENGTH_SHORT).show();
            return;
        }
    }

    @Override
    public void onGetGeoCodeResult(GeoCodeResult arg0) {
        // TODO Auto-generated method stub
    }
    //定位是否成功
    public boolean locationSuccess(){
        return isSuccess;
    }

    //判读是否开启GPS
    private boolean isGPSEnable() {
        /* 用Setting.System来读取也可以，只是这是更旧的用法
        String str = Settings.System.getString(getContentResolver(),
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        */
        String str = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        if (str != null) {
            return str.contains("gps");
        }
        else{
            return false;
        }
    }

    //开关GPS
    private void toggleGPS() {
        Intent gpsIntent = new Intent();
        gpsIntent.setClassName("com.android.settings",
                "com.android.settings.widget.SettingsAppWidgetProvider");
        gpsIntent.addCategory("android.intent.category.ALTERNATIVE");
        gpsIntent.setData(Uri.parse("custom:3"));
        try {
            PendingIntent.getBroadcast(this, 0, gpsIntent, 0).send();
        }
        catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }
}