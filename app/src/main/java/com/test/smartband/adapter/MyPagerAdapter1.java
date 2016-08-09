package com.test.smartband.adapter;


import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import com.test.smartband.controlsubfrags.FragmentSubAirCon;
import com.test.smartband.controlsubfrags.FragmentSubComfort;

/**
 * Created by qikang on 2016/5/3 0003.
 */
public class MyPagerAdapter1 extends FragmentStatePagerAdapter {

    protected static final String[] SUB_FRAGMENTS = new String[]{"舒适度", "空调"};
    private int mCount = SUB_FRAGMENTS.length;

    public MyPagerAdapter1(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        if (0 == position) {
            return new FragmentSubComfort();
        } else if (1 == position) {
            return new FragmentSubAirCon();
        } else {
            System.out.println("创建控制界面子Fragment_" + position + "失败");
            return null;
        }
    }

    @Override
    public int getCount() {
        return mCount;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return SUB_FRAGMENTS[position % mCount];
    }
}
