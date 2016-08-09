package com.test.smartband.activity;

import android.content.Context;
import android.content.SharedPreferences;


/**
 * Created by ASUS on 2016/5/11.
 */
public class LocationData {
;
    private SharedPreferences preference;//用来存储定位数据和记录地址数。
    SharedPreferences.Editor editor;
    final String ADDR = "ADDR";
    final String TIME = "TIME";
    final String LATITUDE = "LATITUDE";//纬度
    final String LONGITUDE = "LONGITUDE";
    final String INDEX = "INDEX";//数据索引
    final String DATASIZE = "DATASIZE";//默认可存储10个定位数据

    private int dataSize = 10;
    private int index = 0;//默认可存储10个定位数据

    public LocationData(Context context,SharedPreferences share){
        preference = share;
        editor = preference.edit();
        setDatasize(10);
        index  = preference.getInt(INDEX,0);
    }

    //超过设置值大小处理函数
    public void handleOutrange(){
        int j;
        for(int i=1; i<dataSize;i++){
            editor.putString(ADDR+i, preference.getString(ADDR+i+1,null));
        }
        editor.commit();
    }

    //设置存储数据大小
    public void setDatasize(int size){
        dataSize = size;
        editor.putInt(DATASIZE, size);
        editor.commit();
    }

    //获得已存数据个数
    public int getIndex(){
        return index;
    }

    //清除已存数据
    public void clear(){
        dataSize = 10;
        index = 0;
        setDatasize(10);
        editor.clear();
    }

    //存储定位数据
    public void putLocationData(String time,String addr,String lon,String lat){
        index++;//定位数据索引自加
        if(index>dataSize){
            handleOutrange();
            index--;
        }
        //存储数据
        if(addr==null){
            addr = "无地址数据";
        }
        editor.putString(ADDR+index, addr);
        editor.putString(TIME+index,  time);
        editor.putString(LATITUDE+index, lat);
        editor.putString(LONGITUDE+index, lon);
        editor.putInt(INDEX,index);
        editor.commit();
    }

    //取得定位信息
    public String getAddr(int index) {
        String data;
        data = preference.getString(ADDR + index, null);
        return data;
    }

    public String getTime(int index) {
        String data;
        data = preference.getString(TIME + index, null);
        return data;
    }

    public String getLat(int index) {
        String data;
        data = preference.getString(LATITUDE + index, null);
        return data;
    }

    public String getLon(int index) {
        String data;
        data = preference.getString(LONGITUDE + index, null);
        return data;
    }
}
