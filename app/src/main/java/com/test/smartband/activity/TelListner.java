package com.test.smartband.activity;

import android.content.Context;
import android.media.AudioManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * Created by ASUS on 2016/5/12.
 */
public class TelListner extends PhoneStateListener {
    boolean comingPhone=false;
    AudioManager audioManager;

    public TelListner(Context context,AudioManager manager){
        audioManager = manager;
    }


    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:/* 无任何状态 */
                if(this.comingPhone){
                    this.comingPhone=false;
                    setSpeekModle(false);
                }
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:/* 接起电话 */
                this.comingPhone=true;
                setSpeekModle(true);
                break;
            case TelephonyManager.CALL_STATE_RINGING:/* 电话进来 */
                this.comingPhone=true;
                setSpeekModle(true);
                break;
        }
    }
    void setSpeekModle(boolean open){
        //audioManager.setMode(AudioManager.ROUTE_SPEAKER);
       int currVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        if(!audioManager.isSpeakerphoneOn()&&true==open) {
            audioManager.setSpeakerphoneOn(true);
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                    AudioManager.STREAM_VOICE_CALL);
        }else if(audioManager.isSpeakerphoneOn()&&false==open){
            audioManager.setSpeakerphoneOn(false);
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,currVolume,
                    AudioManager.STREAM_VOICE_CALL);
        }
    }
}
