package com.yoyo.jingxi.ui.widget;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

public class BookFlipPageTransformer implements ViewPager2.PageTransformer {
    private static final float CENTER = 0.5f;

    @Override
    public void transformPage(@NonNull View page, float position) {
        int pageWidth = page.getWidth();

        page.setCameraDistance(10000 * page.getContext().getResources().getDisplayMetrics().density);

        if (position < -1) { // [-Infinity,-1)
            // This page is way off-screen to the left.
            page.setAlpha(0f);
        } else if (position <= 0) { // [-1,0]
            // Page is moving left (or is fully centered)
            page.setAlpha(1f);
            page.setTranslationX(0f);
            page.setPivotX(0f);
            page.setPivotY(page.getHeight() * CENTER);
            page.setRotationY(90 * position);
        } else if (position <= 1) { // (0,1]
            // Page is moving right (or is just off-center to the right)
            page.setAlpha(1f);
            page.setTranslationX(-pageWidth * position);
            page.setPivotX(0f);
            page.setPivotY(page.getHeight() * CENTER);
            
            // To prevent backface visibility issues
            if (position > 0.5f) {
                page.setVisibility(View.INVISIBLE);
            } else {
                page.setVisibility(View.VISIBLE);
                page.setRotationY(0f);
            }
        } else { // (1,+Infinity]
            // This page is way off-screen to the right.
            page.setAlpha(0f);
        }
    }
}
