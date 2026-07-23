package com.yoyo.jingxi.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信式图片堆叠翻页组件。
 * 参考 PhotoStack (https://github.com/Wren036/PhotoStack) 的交互设计规范，
 * 基于对微信公开可见交互的独立分析，用 Android 原生代码实现。
 *
 * 核心模型：三层可见（顶卡+两侧探边）、擦洗模型手势、峰形翻页轨迹、快甩。
 */
public class PhotoStackView extends FrameLayout {

    // ── 设计参数（来自 PhotoStack 逆向工程笔记）──
    private static final float PEEK_DP = 15f;          // 第一层探边露出量
    private static final float PEEK_STEP_DP = 12f;     // 每深一层多探出
    private static final float ROT_STEP_DEG = 2.2f;    // 每层递进旋转角
    private static final float SCALE_STEP = 0.08f;     // 每层递进缩小
    private static final float FLING_VEL_DP_PER_MS = 0.4f; // 快甩判定速度
    private static final int CARD_WIDTH_DP = 142;      // 卡片宽（微信 3:4 比例）
    private static final int CARD_HEIGHT_DP = 190;     // 卡片高
    private static final int SLOP_DP = 8;              // 手势接管阈值

    private final float density;
    private final float peek;
    private final float peekStep;
    private final float flingVel;
    private final float touchSlop;

    // ── 数据 ──
    private final List<String> imageUrls = new ArrayList<>();
    private int curIndex = 0;

    // ── 卡片池（最多 3 张可见）──
    private final ImageView[] cards = new ImageView[3];
    private boolean cardsBuilt = false;

    // ── 手势状态 ──
    private GestureDetector gestureDetector;
    private VelocityTracker velocityTracker;
    private float downX, downY;
    private boolean dragging = false;
    private boolean swiped = false;
    private float lastX;
    private long lastTimeMs;
    private float smoothedVel;

    // ── 动画 ──
    private ValueAnimator finishAnim;
    private int animDir; // -1=左翻(下一页), 1=右翻(上一页), 0=无

    // ── 回调 ──
    private OnChangeListener changeListener;
    private OnTapListener tapListener;

    public interface OnChangeListener {
        void onChange(int index);
    }

    public interface OnTapListener {
        void onTap(int index);
    }

    public PhotoStackView(Context context) {
        this(context, null);
    }

    public PhotoStackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        peek = PEEK_DP * density;
        peekStep = PEEK_STEP_DP * density;
        flingVel = FLING_VEL_DP_PER_MS * density;
        touchSlop = SLOP_DP * density;

        gestureDetector = new GestureDetector(context, new GestureListener());
        setClipChildren(false);
        setClipToPadding(false);
    }

    // ═══════════════════════════════════════════
    // 公开 API
    // ═══════════════════════════════════════════

    public void setImages(List<String> urls) {
        imageUrls.clear();
        if (urls != null) imageUrls.addAll(urls);
        curIndex = 0;
        cancelAnim();
        ensureCards();
        applyLayout();
    }

    public int getCurrentIndex() {
        return curIndex;
    }

    public void setOnChangeListener(OnChangeListener listener) {
        this.changeListener = listener;
    }

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    public void gotoPage(int index) {
        if (imageUrls.isEmpty()) return;
        index = Math.max(0, Math.min(imageUrls.size() - 1, index));
        if (index == curIndex) return;
        cancelAnim();
        curIndex = index;
        applyLayout();
        if (changeListener != null) changeListener.onChange(curIndex);
    }

    public void next() {
        if (curIndex < imageUrls.size() - 1) finish(-1, 0);
    }

    public void prev() {
        if (curIndex > 0) finish(1, 0);
    }

    // ═══════════════════════════════════════════
    // 卡片构建
    // ═══════════════════════════════════════════

    private void ensureCards() {
        if (cardsBuilt) return;
        int cw = (int) (CARD_WIDTH_DP * density);
        int ch = (int) (CARD_HEIGHT_DP * density);

        for (int i = 0; i < 3; i++) {
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LayoutParams lp = new LayoutParams(cw, ch);
            iv.setLayoutParams(lp);
            iv.setClipToOutline(true);
            // 圆角
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(8 * density);
            iv.setBackground(bg);
            iv.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            addView(iv);
            cards[i] = iv;
        }
        cardsBuilt = true;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int cw = (int) (CARD_WIDTH_DP * density);
        int ch = (int) (CARD_HEIGHT_DP * density);
        int paddingH = (int) (peek + peekStep); // 两侧探边空间
        int w = cw + paddingH * 2 + getPaddingLeft() + getPaddingRight();
        int h = ch + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.width = cw;
            lp.height = ch;
            measureChild(child, MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int cw = (int) (CARD_WIDTH_DP * density);
        int ch = (int) (CARD_HEIGHT_DP * density);
        // 舞台中心
        int cx = (right - left) / 2;
        int cy = (bottom - top) / 2;

        for (int i = 0; i < 3; i++) {
            if (cards[i] == null) continue;
            int l = cx - cw / 2;
            int t = cy - ch / 2;
            cards[i].layout(l, t, l + cw, t + ch);
        }
    }

    // ═══════════════════════════════════════════
    // 静态摆位（复位底座）
    // ═══════════════════════════════════════════

    private void applyLayout() {
        if (imageUrls.isEmpty()) return;
        int n = imageUrls.size();
        int cur = curIndex;

        // 计算左右可见探边配额
        int la = cur, ra = n - 1 - cur;
        int L = Math.min(la, 1), R = Math.min(ra, 1);
        if (L + R < 2) {
            L = Math.min(la, 2 - R);
            R = Math.min(ra, 2 - L);
        }

        for (int idx = 0; idx < 3; idx++) {
            int i = cur - 1 + idx; // 当前页附近的 3 张：cur-1, cur, cur+1
            if (i < 0 || i >= n) {
                cards[idx].setVisibility(GONE);
                continue;
            }
            cards[idx].setVisibility(VISIBLE);
            loadImage(cards[idx], imageUrls.get(i));

            float tx, rot, sc, alpha = 1f;
            int z;
            if (i < cur) {
                int d = cur - i;
                tx = -(peek + (d - 1) * peekStep);
                rot = -ROT_STEP_DEG * d;
                sc = 1f - SCALE_STEP * d;
                z = 40 - d;
                if (d > L) alpha = 0f;
            } else if (i == cur) {
                tx = 0;
                rot = 0;
                sc = 1f;
                z = 100;
            } else {
                int d = i - cur;
                tx = peek + (d - 1) * peekStep;
                rot = ROT_STEP_DEG * d;
                sc = 1f - SCALE_STEP * d;
                z = 100 - d;
                if (d > R) alpha = 0f;
            }

            cards[idx].setTranslationX(tx);
            cards[idx].setRotation(rot);
            cards[idx].setScaleX(sc);
            cards[idx].setScaleY(sc);
            cards[idx].setAlpha(alpha);
            // z-order via bringToFront equivalent: higher z means drawn later
            // For FrameLayout child order, we use translationZ as a hint
            cards[idx].setTranslationZ(z);
        }
    }

    private void loadImage(ImageView iv, String url) {
        if (url == null || url.isEmpty()) return;
        Object tag = iv.getTag();
        if (tag != null && tag.equals(url)) return; // already loaded
        iv.setTag(url);
        Glide.with(getContext())
                .load(url)
                .centerCrop()
                .into(iv);
    }

    // ═══════════════════════════════════════════
    // 进度计算：手指到启动边的距离作分母
    // ═══════════════════════════════════════════

    private float progress(float dx, float startX) {
        int w = getWidth();
        float D = startX; // 从手指起点到左侧屏幕边缘
        return Math.min(1f, Math.abs(dx) / Math.max(120 * density, D > 0 ? D : w - startX));
    }

    // ═══════════════════════════════════════════
    // 擦洗帧（核心运动模型）
    // ═══════════════════════════════════════════

    private void scrub(int dir, float p) {
        if (imageUrls.isEmpty()) return;
        int n = imageUrls.size();
        int cur = curIndex;
        float cardW = CARD_WIDTH_DP * density;
        float maxX = cardW * 0.52f;

        // 先复位底座
        applyLayoutBase(dir, p);

        // 边界弹性预览
        if ((dir < 0 && cur >= n - 1) || (dir > 0 && cur <= 0)) {
            ImageView topCard = getCardView(cur);
            if (topCard != null) {
                topCard.setTranslationX(dir * 24 * density * p);
                topCard.setRotation(dir * 2.5f * p);
                topCard.setTranslationZ(110);
            }
            ImageView n1 = getCardView(cur + dir);
            if (n1 != null) {
                n1.setTranslationX(dir * (peek + 8 * density * p));
                n1.setRotation(dir * ROT_STEP_DEG);
                n1.setScaleX(1f - SCALE_STEP);
                n1.setScaleY(1f - SCALE_STEP);
            }
            return;
        }

        // 当前卡：峰形轨迹
        float cx, rot, sc;
        if (p <= 0.5f) {
            float q = p / 0.5f;
            cx = dir * maxX * q;
            rot = dir * 8f * q;
            sc = 1f;
        } else {
            float q = (p - 0.5f) / 0.5f;
            cx = dir * (maxX - (maxX - peek) * q);
            rot = dir * (8f - (8f - ROT_STEP_DEG) * q);
            sc = 1f - SCALE_STEP * q;
        }

        ImageView topCard = getCardView(cur);
        if (topCard != null) {
            topCard.setTranslationX(cx);
            topCard.setRotation(rot);
            topCard.setScaleX(sc);
            topCard.setScaleY(sc);
            topCard.setTranslationZ(p < 0.5f ? 110 : 102);
        }

        // 新顶：从对侧探边位插值升顶
        ImageView newTop = getCardView(cur - dir);
        if (newTop != null) {
            newTop.setTranslationX(-dir * peek * (1f - p));
            newTop.setRotation(-dir * ROT_STEP_DEG * (1f - p));
            newTop.setScaleX(1f - SCALE_STEP + SCALE_STEP * p);
            newTop.setScaleY(1f - SCALE_STEP + SCALE_STEP * p);
            newTop.setAlpha(1f);
            newTop.setTranslationZ(105);
        }

        // 新探边（后半程进场）
        float qq = Math.max(0, (p - 0.5f) / 0.5f);
        ImageView nn = getCardView(cur - dir * 2);
        if (nn != null) {
            int[] lr = calcLR(cur);
            boolean boundaryBorrow = (dir < 0) ? (2 <= lr[1]) : (2 <= lr[0]);
            if (boundaryBorrow) {
                nn.setTranslationX(-dir * (peek + peekStep * (1f - p)));
                nn.setRotation(-dir * (ROT_STEP_DEG * 2f - ROT_STEP_DEG * p));
                float s = 1f - SCALE_STEP * 2f + SCALE_STEP * p;
                nn.setScaleX(s); nn.setScaleY(s);
                nn.setAlpha(1f);
            } else {
                float ntx = -dir * peek * (1f - p);
                nn.setTranslationX(ntx * (1f - qq) + (-dir * peek) * qq);
                nn.setRotation(-dir * (ROT_STEP_DEG * 2f - ROT_STEP_DEG * qq));
                float s = 1f - SCALE_STEP * 2.5f + SCALE_STEP * 1.5f * qq;
                nn.setScaleX(s); nn.setScaleY(s);
                nn.setAlpha(Math.min(1f, qq / 0.18f) * 0.55f + 0.45f * qq);
            }
            nn.setTranslationZ(dir < 0 ? 98 : 38);
        }

        // 旧探边退场
        ImageView old2 = getCardView(cur + dir);
        if (old2 != null) {
            int newCur = dir < 0 ? Math.min(cur + 1, n - 1) : Math.max(cur - 1, 0);
            int[] lr2 = calcLR(newCur);
            int oi = cur + dir;
            int staysIdx = (oi < newCur) ? (newCur - oi) : (oi - newCur);
            boolean stays = (oi < newCur) ? (staysIdx <= lr2[0]) : (staysIdx <= lr2[1]);
            if (!stays) {
                float eq = 1f - (1f - qq) * (1f - qq);
                old2.setTranslationX(dir * peek * (1f - eq) + cx * eq);
                old2.setRotation(dir * ROT_STEP_DEG);
                float s = 1f - SCALE_STEP - SCALE_STEP * 1.5f * qq;
                old2.setScaleX(s); old2.setScaleY(s);
                old2.setAlpha(1f - (Math.min(1f, qq / 0.18f) * 0.55f + 0.45f * qq));
            } else {
                old2.setTranslationX(dir * (peek + peekStep * p));
                old2.setRotation(dir * (ROT_STEP_DEG + ROT_STEP_DEG * p));
                float s = 1f - SCALE_STEP - SCALE_STEP * p;
                old2.setScaleX(s); old2.setScaleY(s);
            }
        }
    }

    /**
     * 仅复位底座（不做擦洗叠加）。scrub() 第一帧调用此方法。
     */
    private void applyLayoutBase(int dir, float p) {
        // 简化版：直接调用 applyLayout 再在 scrub 中叠加
        applyLayout();
    }

    private int[] calcLR(int cur) {
        int n = imageUrls.size();
        int la = cur, ra = n - 1 - cur;
        int L = Math.min(la, 1), R = Math.min(ra, 1);
        if (L + R < 2) {
            L = Math.min(la, 2 - R);
            R = Math.min(ra, 2 - L);
        }
        return new int[]{L, R};
    }

    /** 获取对应 index 的卡片 View（可能为 null，如果不在当前可见窗口） */
    private ImageView getCardView(int dataIndex) {
        if (dataIndex < 0 || dataIndex >= imageUrls.size()) return null;
        // 卡片数组中：idx 0=cur-1, idx 1=cur, idx 2=cur+1
        int offset = dataIndex - curIndex;
        int cardIdx = offset + 1;
        if (cardIdx < 0 || cardIdx >= 3) return null;
        return cards[cardIdx];
    }

    // ═══════════════════════════════════════════
    // 完成动画
    // ═══════════════════════════════════════════

    private void finish(int dir, float fromP) {
        if (finishAnim != null) finishAnim.cancel();

        final int dur = Math.max(140, (int) ((1f - fromP) * 340));
        animDir = dir;

        finishAnim = ValueAnimator.ofFloat(fromP, 1f);
        finishAnim.setDuration(dur);
        finishAnim.setInterpolator(new DecelerateInterpolator(2f));
        finishAnim.addUpdateListener(animation -> {
            float k = (float) animation.getAnimatedValue();
            scrub(dir, fromP + (1f - fromP) * (1f - (float) Math.pow(1 - k, 2)));
        });
        finishAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finishAnim = null;
                animDir = 0;
                int n = imageUrls.size();
                curIndex = dir < 0 ? Math.min(curIndex + 1, n - 1) : Math.max(curIndex - 1, 0);
                applyLayout();
                if (changeListener != null) changeListener.onChange(curIndex);
            }
        });
        finishAnim.start();
    }

    private void cancelAnim() {
        if (finishAnim != null) {
            finishAnim.cancel();
            finishAnim = null;
            animDir = 0;
        }
    }

    // ═══════════════════════════════════════════
    // 手势处理
    // ═══════════════════════════════════════════

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true; // 必须返回 true 才能接收后续事件
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (!swiped && tapListener != null) {
                tapListener.onTap(curIndex);
            }
            swiped = false;
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // 委托给 Android 长按机制，触发 ChatAdapter 中设置的 OnLongClickListener
            performLongClick();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 横向滑动由本组件处理，纵向交还父容器
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            dragging = false;
            swiped = false;
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && !dragging) {
            float dx = Math.abs(ev.getX() - downX);
            float dy = Math.abs(ev.getY() - downY);
            if (dx > touchSlop && dx > dy) {
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (imageUrls.isEmpty()) return false;

        // 同时使用 GestureDetector（处理 tap）和手动跟踪（处理 drag/fling）
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastX = downX;
                lastTimeMs = event.getEventTime();
                smoothedVel = 0;
                dragging = false;
                swiped = false;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(event);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;

                // 过滤纯纵向滑动
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                }
                if (!dragging) return false;

                // 指数平滑速度估计
                long dt = event.getEventTime() - lastTimeMs;
                if (dt > 0) {
                    float frameVel = (event.getX() - lastX) / dt;
                    smoothedVel = 0.7f * frameVel + 0.3f * smoothedVel;
                }
                lastX = event.getX();
                lastTimeMs = event.getEventTime();

                // 如果正在进行完成动画，先打断并结算
                if (finishAnim != null) {
                    cancelAnim();
                    if (animDir != 0) {
                        int n = imageUrls.size();
                        curIndex = animDir < 0 ? Math.min(curIndex + 1, n - 1) : Math.max(curIndex - 1, 0);
                        animDir = 0;
                    }
                }

                int dir = dx < 0 ? -1 : 1; // -1=左滑翻下一页, 1=右滑翻上一页
                scrub(dir, progress(dx, downX));
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                    velocityTracker.recycle();
                    velocityTracker = null;
                }

                if (dragging) {
                    swiped = true;
                    float finalDx = event.getX() - downX;
                    release(finalDx, downX);
                    dragging = false;
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void release(float dx, float startX) {
        int dir = dx < 0 ? -1 : 1;
        float p = progress(dx, startX);
        boolean canFlip = dir < 0 ? curIndex < imageUrls.size() - 1 : curIndex > 0;

        // 快甩判定：速度过阈值 + 位移方向一致 + 位移 > 10px 防误触
        boolean fling = Math.abs(smoothedVel) > flingVel
                && Math.signum(smoothedVel) == Math.signum(dx)
                && p > 0.04f;

        if (canFlip && (p > 0.5f || fling)) {
            finish(dir, p);
        } else {
            // 取消：回弹归位
            applyLayout();
        }
    }
}
