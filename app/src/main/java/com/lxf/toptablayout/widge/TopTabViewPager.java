package com.lxf.toptablayout.widge;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Author: lxf
 * Create: 2018/12/19
 * Describe:
 */
public class TopTabViewPager extends ViewPager {
    private boolean isFrozen;

    public TopTabViewPager(Context context) {
        super(context);
    }

    public TopTabViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return isFrozen ? false : super.dispatchTouchEvent(ev);
    }

    @Override
    public void setCurrentItem(int item) {
        if (isFrozen) {
            return;
        }
        super.setCurrentItem(item);
    }

    @Override
    public void setCurrentItem(int item, boolean smoothScroll) {
        if (isFrozen) {
            return;
        }
        super.setCurrentItem(item, smoothScroll);
    }

    public void forceSetCurrentItem(int item) {
        super.setCurrentItem(item);
    }

    public void forceSetCurrentItem(int item, boolean smoothScroll) {
        super.setCurrentItem(item, smoothScroll);
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    public void setFrozen(boolean isFrozen) {
        this.isFrozen = isFrozen;
    }
}
