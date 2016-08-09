package com.test.smartband.fragment;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.test.smartband.R;
import com.test.smartband.adapter.MyPagerAdapter1;
import com.viewpagerindicator.TitlePageIndicator;

/**
 * Created by chuangfeng on 2016/4/24.
 */
public class FragmentControl extends Fragment {
    /**
     * @ code by qikang
     * @ 作用：显示子fragment-通过viewpage+indicator实现
     */
    private MyPagerAdapter1 mAdapter;
    private ViewPager mPager;
    private TitlePageIndicator mIndicator;
    private static int mCurrentSubFragSeq = 0;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView
            (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control, container, false);
        System.out.println("Fragment_control:onCreateView");
        mAdapter = new MyPagerAdapter1(getFragmentManager());
        mPager = (ViewPager) view.findViewById(R.id.id_viewPager_control);
        mPager.setAdapter(mAdapter);

        mIndicator = (TitlePageIndicator) view.findViewById(R.id.id_titlePage_control);
        mIndicator.setViewPager(mPager,mCurrentSubFragSeq);
        mIndicator.setFooterIndicatorStyle(TitlePageIndicator.IndicatorStyle.Triangle);
        mIndicator.setFooterColor(R.color.black);
        mIndicator.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                mCurrentSubFragSeq = position;

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }
}
