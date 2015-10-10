package com.github.shareme.gwsbetterrelativelayout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.TextView;

import com.github.shareme.gwsbetterrelativelayout.library.SpringLayout;

public class MainActivity extends Activity {

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RelativeWidthChangeAnim aAnimation = new RelativeWidthChangeAnim((TextView) findViewById(R.id.A), 10, 50);
        aAnimation.setDuration(1000);
        aAnimation.setRepeatCount(Animation.INFINITE);
        aAnimation.setRepeatMode(Animation.REVERSE);
        findViewById(R.id.A).startAnimation(aAnimation);

        RelativeWidthChangeAnim bAnimation = new RelativeWidthChangeAnim((TextView) findViewById(R.id.B), 50, 10);
        bAnimation.setDuration(2000);
        bAnimation.setRepeatCount(Animation.INFINITE);
        bAnimation.setRepeatMode(Animation.REVERSE);
        findViewById(R.id.B).startAnimation(bAnimation);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.test_sandbox:
            startActivity(new Intent(this, TestSandboxActivity.class));
            return true;
        case R.id.test_performance:
            startActivity(new Intent(this, TestPerformanceActivity.class));
            return true;
        case R.id.open_github:
            startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://github.com/sulewicz/springlayout")));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}

class RelativeWidthChangeAnim extends Animation {
    TextView mView;
    SpringLayout.LayoutParams mLayoutParams;
    private int mFrom, mTo;

    public RelativeWidthChangeAnim(TextView view, int from, int to) {
        mView = view;
        mLayoutParams = (SpringLayout.LayoutParams) view.getLayoutParams();
        mFrom = from;
        mTo = to;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        int relativeWidth;
        relativeWidth = (int) (mFrom + interpolatedTime * (mTo - mFrom));
        mLayoutParams.setRelativeWidth(relativeWidth);
        mView.setText(relativeWidth + "%");
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }
}
