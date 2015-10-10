package com.github.shareme.gwsbetterrelativelayout;

import android.content.Context;
import android.os.Debug;
import android.util.AttributeSet;

import com.github.shareme.gwsbetterrelativelayout.library.SpringLayout;

@SuppressWarnings("unused")
public class ProxySpringLayout extends SpringLayout implements MeasurableLayout {
    private int mMeasuresCount, mLayoutsCount;
    private long mTotalMeasuresTime, mTotalLayoutsTime;

    public ProxySpringLayout(Context context) {
        super(context);
    }

    public ProxySpringLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProxySpringLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final long start = Debug.threadCpuTimeNanos();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mTotalMeasuresTime += (Debug.threadCpuTimeNanos() - start);
        mMeasuresCount++;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final long start = Debug.threadCpuTimeNanos();
        super.onLayout(changed, l, t, r, b);
        mTotalLayoutsTime += (Debug.threadCpuTimeNanos() - start);
        mLayoutsCount++;
    }

    @Override
    public int getMeasuresCount() {
        return mMeasuresCount;
    }

    @Override
    public long getTotalMeasuresTime() {
        return mTotalMeasuresTime / 1000;
    }

    @Override
    public long getAverageMeasureTime() {
        return getTotalMeasuresTime() / getMeasuresCount();
    }

    @Override
    public int getLayoutsCount() {
        return mLayoutsCount;
    }

    @Override
    public long getTotalLayoutsTime() {
        return mTotalLayoutsTime / 1000;
    }

    @Override
    public long getAverageLayoutTime() {
        return getTotalLayoutsTime() / getLayoutsCount();
    }
}
