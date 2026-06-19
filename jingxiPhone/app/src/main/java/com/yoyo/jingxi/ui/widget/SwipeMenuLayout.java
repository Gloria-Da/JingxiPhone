package com.yoyo.jingxi.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

public class SwipeMenuLayout extends HorizontalScrollView {
    private View mContentView;
    private View mMenuView;
    private int mMenuWidth;
    private float startX;
    private float startY;

    public SwipeMenuLayout(Context context) {
        this(context, null);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setHorizontalScrollBarEnabled(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getChildCount() > 0) {
            View wrapper = getChildAt(0);
            if (wrapper instanceof ViewGroup && ((ViewGroup) wrapper).getChildCount() == 2) {
                mContentView = ((ViewGroup) wrapper).getChildAt(0);
                mMenuView = ((ViewGroup) wrapper).getChildAt(1);

                int contentWidth = getMeasuredWidth();
                ViewGroup.LayoutParams vlp = mContentView.getLayoutParams();
                if (vlp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) vlp;
                    contentWidth -= (mlp.leftMargin + mlp.rightMargin);
                }

                if (vlp.width != contentWidth) {
                    vlp.width = contentWidth;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
                mMenuWidth = mMenuView.getMeasuredWidth();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercepted = super.onInterceptTouchEvent(ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                // If menu is open and user touches content area, intercept the touch
                if (getScrollX() > 0 && ev.getX() < getWidth() - getScrollX()) {
                    intercepted = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - startX);
                float dy = Math.abs(ev.getY() - startY);
                if (dx > dy && dx > 10) {
                    intercepted = true;
                } else if (dy > dx && dy > 10) {
                    intercepted = false;
                }
                break;
        }
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mMenuView == null) return super.onTouchEvent(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (getScrollX() > 0 && ev.getX() < getWidth() - getScrollX()) {
                    smoothScrollTo(0, 0);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                int scrollX = getScrollX();
                if (scrollX >= mMenuWidth / 2) {
                    smoothScrollTo(mMenuWidth, 0);
                } else {
                    smoothScrollTo(0, 0);
                }
                return true;
        }
        return super.onTouchEvent(ev);
    }

    public void closeMenu() {
        smoothScrollTo(0, 0);
    }
}
