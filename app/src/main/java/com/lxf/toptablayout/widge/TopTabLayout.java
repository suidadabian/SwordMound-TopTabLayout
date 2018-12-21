package com.lxf.toptablayout.widge;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.lxf.toptablayout.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Author: lxf
 * Create: 2018/12/15
 * Describe:
 */

/**
 * Author: lxf
 * Create: 2018/12/15
 * Describe:
 */
public class TopTabLayout<T, K extends BaseViewHolder> extends RecyclerView {
    private static final String TAG = TopTabLayout.class.getSimpleName();

    private static final int INDICATOR_STATE_INIT = 1;
    private static final int INDICATOR_STATE_IDLE = 2;
    private static final int INDICATOR_STATE_MOVING = 3;

    public static final int BETWEEN_DIVIDER_TYPE = 1;
    public static final int BOTTOM_DIVIDER_TYPE = 2;

    private static final Config DEFAULT_CONFIG = new Config.Builder()
            .setEnableIndicator(true)
            .setIndicatorColor(0xff000000)
            .setIndicatorHeight(1)
            .build();

    private int mPreSelection;
    private int mSelection;
    private int mSelectionCache;
    private ArgbEvaluator mArgbEvaluator;

    private Config mConfig;
    private TopTabLayoutAdapter<T, K> mTopTabLayoutAdapter;
    private List<IndicatorTrimmer> mIndicatorTrimmers;
    private List<OnTabSelectedListener<T>> mOnTabSelectedListeners;

    private TopTabViewPager mTopTabViewPager;
    private ViewPagerOnTabSelectedListener<T> mViewPagerOnTabSelectedListener;
    private TopTabLayoutOnPageChangeListener mTopTabLayoutPageChangeListener;

    private int mIndicatorState;
    private int mIndicatorLeft;
    private int mIndicatorRight;
    private int mIndicatorColor;
    private int mIndicatorOriginLeft;
    private int mIndicatorOriginRight;
    private boolean isIndicatorNeedAnim;
    private boolean isIndicatorNeedLocate;

    private ValueAnimator mMoveValueAnimator;
    private ValueAnimator mWidthValueAnimator;
    private ValueAnimator mColorValueAnimator;

    public TopTabLayout(Context context) {
        this(context, null);
    }

    public TopTabLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TopTabLayout(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initData(context, attrs);
        initView();
    }

    private void initData(Context context, AttributeSet attrs) {
        mPreSelection = -1;
        mSelection = -1;
        mSelectionCache = -1;

        mIndicatorState = INDICATOR_STATE_INIT;

        mIndicatorTrimmers = new ArrayList<>();
        mOnTabSelectedListeners = new ArrayList<>();

        if (attrs == null) {
            mConfig = DEFAULT_CONFIG.clone();
            return;
        } else {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.TopTabLayout);
            mConfig = new Config.Builder()
                    .setEnableIndicator(typedArray.getBoolean(R.styleable.TopTabLayout_indicatorEnable, DEFAULT_CONFIG.isEnableIndicator))
                    .setIndicatorHeight((int) typedArray.getDimension(R.styleable.TopTabLayout_indicatorHeight, DEFAULT_CONFIG.indicatorHeight))
                    .setIndicatorColor(typedArray.getColor(R.styleable.TopTabLayout_indicatorColor, DEFAULT_CONFIG.indicatorColor))
                    .setEnableDivider(typedArray.getBoolean(R.styleable.TopTabLayout_dividerEnable, DEFAULT_CONFIG.isEnableDivider))
                    .setDividerType(typedArray.getInt(R.styleable.TopTabLayout_dividerType, DEFAULT_CONFIG.dividerType))
                    .setDividerHeight((int) typedArray.getDimension(R.styleable.TopTabLayout_dividerHeight, DEFAULT_CONFIG.dividerHeight))
                    .setDividerColor(typedArray.getColor(R.styleable.TopTabLayout_dividerColor, DEFAULT_CONFIG.dividerColor))
                    .setEnableCenter(typedArray.getBoolean(R.styleable.TopTabLayout_centerEnable, DEFAULT_CONFIG.isEnableCenter))
                    .build();
        }
    }

    private void initView() {
        super.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false) {
            @Override
            public void onLayoutChildren(Recycler recycler, State state) {
                super.onLayoutChildren(recycler, state);

                if (!mConfig.isEnableCenter) {
                    return;
                }

                if (getChildCount() == 0) {
                    return;
                }

                if (TopTabLayout.this.canScrollHorizontally(1) || TopTabLayout.this.canScrollHorizontally(-1)) {
                    return;
                }

                int spaceLeft = getChildAt(0).getLeft() - TopTabLayout.this.getPaddingLeft();
                int spaceRight = getWidth() - TopTabLayout.this.getPaddingRight() - getChildAt(getChildCount() - 1).getRight();
                int offset = spaceRight - spaceLeft;
                if (spaceRight == spaceLeft) {
                    return;
                }
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    child.layout(child.getLeft() + offset / 2, child.getTop(), child.getRight() + offset / 2, child.getBottom());
                }
            }
        });
        addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {
                super.getItemOffsets(outRect, view, parent, state);
                outRect.bottom += (mConfig.isEnableIndicator ? mConfig.indicatorHeight : 0) + (mConfig.isEnableDivider ? mConfig.dividerHeight : 0);
            }
        });
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                Log.d(TAG, "mIndicatorLeft pre: " + mIndicatorLeft);
                if (mConfig.isEnableIndicator && mIndicatorState == INDICATOR_STATE_IDLE) {
                    mIndicatorLeft -= dx;
                    mIndicatorRight -= dx;
                    mIndicatorOriginLeft -= dx;
                    mIndicatorOriginRight -= dx;
                }
                Log.d(TAG, "mIndicatorLeft after: " + mIndicatorLeft);
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (mConfig.isEnableIndicator && newState == RecyclerView.SCROLL_STATE_IDLE && isIndicatorNeedLocate) {
                    isIndicatorNeedLocate = false;
                    locateAndMoveIndicator();
                }
            }
        });
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mIndicatorState = INDICATOR_STATE_IDLE;
                setSelection(mSelectionCache);
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    public void setConfig(@NonNull Config config) {
        this.mConfig = config;
    }

    public Config getConfig() {
        return mConfig;
    }

    public void setTopTabLayoutAdapter(@NonNull TopTabLayoutAdapter<T, K> adapter) {
        mTopTabLayoutAdapter = adapter;
        super.setAdapter(adapter);
    }

    @Override
    public void setLayoutManager(LayoutManager layout) {
        return;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        return;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawIndicator(canvas);
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        drawIndicator(c);
        drawDivider(c);
    }

    private void drawIndicator(Canvas canvas) {
        if (!mConfig.isEnableIndicator) {
            return;
        }

        Rect rect = new Rect();
        if (!mConfig.isEnableDivider || mConfig.dividerType == BETWEEN_DIVIDER_TYPE) {
            rect.bottom = getHeight();
        } else {
            rect.bottom = getHeight() - mConfig.dividerHeight;
        }
        rect.top = rect.bottom - mConfig.indicatorHeight;
        rect.left = mIndicatorLeft;
        rect.right = mIndicatorRight;
        for (IndicatorTrimmer indicatorTrimmer : mIndicatorTrimmers) {
            Rect trim = new Rect();
            indicatorTrimmer.trim(mSelection, trim);
            rect.left += trim.left;
            rect.right -= trim.right;
        }
        Paint paint = new Paint();
        paint.setColor(mConfig.isMultipleIndicatorColor ? mIndicatorColor : mConfig.indicatorColor);
        canvas.drawRect(rect, paint);
    }

    private void drawDivider(Canvas canvas) {
        if (!mConfig.isEnableDivider) {
            return;
        }

        Rect rect = new Rect();
        if (!mConfig.isEnableIndicator || mConfig.dividerType == BOTTOM_DIVIDER_TYPE) {
            rect.bottom = getHeight();
        } else {
            rect.bottom = getHeight() - mConfig.indicatorHeight;
        }
        rect.top = rect.bottom - mConfig.dividerHeight;
        rect.left = 0;
        rect.right = getWidth();
        Paint paint = new Paint();
        paint.setColor(mConfig.dividerColor);
        canvas.drawRect(rect, paint);
    }

    @Override
    public void onChildAttachedToWindow(View child) {
        super.onChildAttachedToWindow(child);
        int position = getChildAdapterPosition(child);
        child.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (isChildCompleteVisible(position) && isIndicatorNeedLocate && position == mSelection && mConfig.isEnableIndicator) {
                    locateAndMoveIndicator();
                    isIndicatorNeedLocate = false;
                }
                child.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private boolean isChildCompleteVisible(int position) {
        ViewHolder viewHolder = findViewHolderForAdapterPosition(position);
        if (viewHolder == null) {
            return false;
        }

        int visibleLeft = getScrollX();
        int visibleRight = getScrollX() + getWidth();
        View view = viewHolder.itemView;
        return view.getLeft() >= visibleLeft && view.getRight() <= visibleRight;
    }

    private void setSelection(int selection) {
        if (mIndicatorState == INDICATOR_STATE_INIT) {
            mSelectionCache = selection;
            return;
        }

        mPreSelection = mSelection;
        mSelection = selection;

        if (selection == -1) {
            mIndicatorState = INDICATOR_STATE_IDLE;
            if (mConfig.isEnableIndicator) {
                mIndicatorLeft = 0;
                mIndicatorRight = 0;
            }
            if (mPreSelection != -1) {
                getAdapter().notifyItemChanged(mPreSelection);
            }
            return;
        }

        boolean isChildCompleteVisible = isChildCompleteVisible(selection);
        if (!isChildCompleteVisible) {
            smoothScrollToPosition(selection);
        }

        if (mPreSelection == selection) {
            mIndicatorState = INDICATOR_STATE_IDLE;
            return;
        }

        if (mPreSelection != -1) {
            getAdapter().notifyItemChanged(mPreSelection);
        }
        getAdapter().notifyItemChanged(selection);

        if (!mConfig.isEnableIndicator) {
            mIndicatorState = INDICATOR_STATE_IDLE;
        } else {

            isIndicatorNeedAnim = mPreSelection != -1;
            if (!isChildCompleteVisible) {
                isIndicatorNeedLocate = true;
            } else {
                locateAndMoveIndicator();
            }
        }

        for (OnTabSelectedListener<T> onTabSelectedListener : mOnTabSelectedListeners) {
            if (onTabSelectedListener != null) {
                onTabSelectedListener.onTabSelected(selection, mTopTabLayoutAdapter.getItem(selection));
            }
        }
        if (mViewPagerOnTabSelectedListener != null) {
            mViewPagerOnTabSelectedListener.onTabSelected(selection, mTopTabLayoutAdapter.getItem(selection));
        }
    }

    private void locateAndMoveIndicator() {
        if (mSelection == -1) {
            mIndicatorLeft = 0;
            mIndicatorRight = 0;
            invalidate();
            return;
        }

        View view = getLayoutManager().findViewByPosition(mSelection);
        if (view == null) {
            return;
        }

        if (!isIndicatorNeedAnim) {
            mIndicatorLeft = view.getLeft();
            mIndicatorRight = view.getLeft() + view.getWidth();
            if (mConfig.isMultipleIndicatorColor) {
                mIndicatorColor = mConfig.indicatorColors[mSelection];
            }
            invalidate();
            mIndicatorState = INDICATOR_STATE_IDLE;
            return;
        }

        mIndicatorState = INDICATOR_STATE_MOVING;
        setLayoutFrozen(true);
        mTopTabViewPager.setFrozen(true);

        long duration = mConfig.durationCalculator.duration((int) Math.abs(mIndicatorLeft - view.getX()));
        startMoveAnim((int) view.getX(), duration);
        startWidthAnim(view.getWidth(), duration);
        if (mConfig.isMultipleIndicatorColor) {
            startColorAnim(duration);
        }
    }

    private void startMoveAnim(int targetX, long duration) {
        if (mMoveValueAnimator != null) {
            if (mMoveValueAnimator.isRunning()) {
                mMoveValueAnimator.cancel();
            }
            mMoveValueAnimator = null;
        }

        mMoveValueAnimator = ValueAnimator.ofInt(mIndicatorLeft, targetX);
        mMoveValueAnimator.setDuration(duration);
        mMoveValueAnimator.addUpdateListener(animation -> {
            int x = (int) animation.getAnimatedValue();
            int offset = x - mIndicatorLeft;
            mIndicatorLeft = x;
            mIndicatorRight += offset;
            TopTabLayout.this.invalidate();
        });
        mMoveValueAnimator.setInterpolator(new LinearInterpolator());
        mMoveValueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIndicatorState = INDICATOR_STATE_IDLE;
                setLayoutFrozen(false);
                if (mTopTabViewPager != null) {
                    mTopTabViewPager.setFrozen(false);
                }
                mMoveValueAnimator = null;
            }
        });
        mMoveValueAnimator.start();
    }

    private void startWidthAnim(int targetWidth, long duration) {
        if (mWidthValueAnimator != null) {
            if (mWidthValueAnimator.isRunning()) {
                mWidthValueAnimator.cancel();
            }
            mWidthValueAnimator = null;
        }

        mWidthValueAnimator = ValueAnimator.ofInt(mIndicatorRight - mIndicatorLeft, targetWidth);
        mWidthValueAnimator.setDuration(duration);
        mWidthValueAnimator.addUpdateListener(animation -> {
            int width = (int) animation.getAnimatedValue();
            int currWidth = mIndicatorRight - mIndicatorLeft;
            mIndicatorRight += width - currWidth;
            TopTabLayout.this.invalidate();
        });
        mWidthValueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIndicatorState = INDICATOR_STATE_IDLE;
                setLayoutFrozen(false);
                if (mTopTabViewPager != null) {
                    mTopTabViewPager.setFrozen(false);
                }
                mWidthValueAnimator = null;
            }
        });
        mWidthValueAnimator.start();
    }

    private void startColorAnim(long duration) {
        if (mColorValueAnimator != null) {
            if (mColorValueAnimator.isRunning()) {
                mColorValueAnimator.cancel();
            }
            mColorValueAnimator = null;
        }

        int[] colors = new int[Math.abs(mSelection - mPreSelection) + 1];
        System.arraycopy(mConfig.indicatorColors, Math.min(mSelection, mPreSelection), colors, 0, colors.length);
        if (mSelection < mPreSelection) {
            for (int i = 0, j = colors.length - 1; i < j; i++, j--) {
                int temp = colors[i];
                colors[i] = colors[j];
                colors[j] = temp;
            }
        }
        ValueAnimator colorValueAnimator;
        if (Build.VERSION.SDK_INT >= 21) {
            colorValueAnimator = ValueAnimator.ofArgb(colors);
            colorValueAnimator.setDuration(duration);
            colorValueAnimator.addUpdateListener(animation -> {
                mIndicatorColor = (int) animation.getAnimatedValue();
                TopTabLayout.this.invalidate();
            });
        } else {
            colorValueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
            colorValueAnimator.setDuration(duration);
            colorValueAnimator.addUpdateListener(animation -> {
                if (colors.length == 1) {
                    mIndicatorColor = colors[0];
                    return;
                }
                float degree = (float) animation.getAnimatedValue();
                if (degree < 0.0001f) {
                    mIndicatorColor = colors[0];
                }
                if (degree > 0.9999f) {
                    mIndicatorColor = colors[colors.length - 1];
                }
                float partOfOne = 1.0f / colors.length;
                int index = 0;
                while (index * partOfOne < degree) {
                    index++;
                }
                if (mArgbEvaluator == null) {
                    mArgbEvaluator = new ArgbEvaluator();
                }
                mIndicatorColor = (int) mArgbEvaluator.evaluate(degree - (index * partOfOne), colors[index], colors[index + 1]);
                TopTabLayout.this.invalidate();
            });
        }
        colorValueAnimator.start();
        colorValueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIndicatorState = INDICATOR_STATE_IDLE;
                setLayoutFrozen(false);
                if (mTopTabViewPager != null) {
                    mTopTabViewPager.setFrozen(false);
                }
            }
        });
    }

    public void setupWithTopTabViewPager(TopTabViewPager topTabViewPager) {
        if (mTopTabViewPager != null) {
            if (mTopTabLayoutPageChangeListener != null) {
                mTopTabViewPager.removeOnPageChangeListener(mTopTabLayoutPageChangeListener);
            }
        }

        if (mViewPagerOnTabSelectedListener != null) {
            removeOnTabSelectedListener(mViewPagerOnTabSelectedListener);
            mViewPagerOnTabSelectedListener = null;
        }

        if (topTabViewPager != null) {
            mTopTabViewPager = topTabViewPager;

            if (mTopTabLayoutPageChangeListener == null) {
                mTopTabLayoutPageChangeListener = new TopTabLayoutOnPageChangeListener(this);
            }
            topTabViewPager.addOnPageChangeListener(mTopTabLayoutPageChangeListener);

            mViewPagerOnTabSelectedListener = new ViewPagerOnTabSelectedListener<>(topTabViewPager);

            setSelection(mTopTabViewPager.getCurrentItem());
        } else {
            mTopTabViewPager = null;
        }
    }

    public void addIndicatorTrimmer(IndicatorTrimmer indicatorTrimmer) {
        mIndicatorTrimmers.add(indicatorTrimmer);
    }

    public void addIndicatorTrimmer(int index, IndicatorTrimmer indicatorTrimmer) {
        mIndicatorTrimmers.add(index, indicatorTrimmer);
    }

    public void removeIndicatorTrimmer(IndicatorTrimmer indicatorTrimmer) {
        mIndicatorTrimmers.remove(indicatorTrimmer);
    }

    public void removeIndicatorTrimmer(int index) {
        mIndicatorTrimmers.remove(index);
    }

    public void addOnTabSelectedListener(OnTabSelectedListener<T> onTabSelectedListener) {
        mOnTabSelectedListeners.add(onTabSelectedListener);
    }

    public void addOnTabSelectedListener(int index, OnTabSelectedListener<
            T> onTabSelectedListener) {
        mOnTabSelectedListeners.add(index, onTabSelectedListener);
    }

    public void removeOnTabSelectedListener(OnTabSelectedListener<T> onTabSelectedListener) {
        mOnTabSelectedListeners.remove(onTabSelectedListener);
    }

    public void removeOnTabSeletedListener(int index) {
        mOnTabSelectedListeners.remove(index);
    }

    public static abstract class TopTabLayoutAdapter<T, K extends BaseViewHolder> extends BaseQuickAdapter<T, K> {
        private final TopTabLayout<T, K> mTopTabLayout;

        public TopTabLayoutAdapter(@NonNull TopTabLayout<T, K> topTabLayout, int layoutResId, @Nullable List<T> data) {
            super(layoutResId, data);
            mTopTabLayout = topTabLayout;
            setOnItemClickListener(null);
        }

        public TopTabLayoutAdapter(@NonNull TopTabLayout<T, K> topTabLayout, @Nullable List<T> data) {
            this(topTabLayout, 0, data);
        }

        public TopTabLayoutAdapter(@NonNull TopTabLayout<T, K> topTabLayout, int layoutResId) {
            this(topTabLayout, layoutResId, null);
        }

        @Override
        public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
            OnItemClickListener onItemClickListener = (adapter, view, position) -> {
                mTopTabLayout.setSelection(position);
                if (listener != null) {
                    listener.onItemClick(adapter, view, position);
                }
            };
            super.setOnItemClickListener(onItemClickListener);
        }

        public void setSelection(int selection) {
            if (selection >= getData().size() || selection < -1) {
                return;
            }

            mTopTabLayout.setSelection(selection);
        }

        public int getSelection() {
            return mTopTabLayout.mSelection;
        }
    }

    public interface DurationCalculator {
        long duration(int distance);
    }

    public interface IndicatorTrimmer {
        void trim(int position, Rect trim);
    }

    public static class Config implements Cloneable {
        private static final boolean DEFAULT_IS_ENABLE_INDICATOR = false;
        private static final int DEFAULT_INDICATOR_HEIGHT = 0;
        private static final int DEFAULT_INDICATOR_COLOR = 0x00000000;
        private static final boolean DEFAULT_IS_MULTIPLE_INDICATOR_COLOR = false;
        private static final int[] DEFAULT_INDICATOR_COLORS = null;
        private static final boolean DEFAULT_IS_ENABLE_DIVIDER = false;
        private static final int DEFAULT_DIVIDER_TYPE = 0;
        private static final int DEFAULT_DIVIDER_COLOR = 0x00000000;
        private static final int DEFAULT_DIVIDER_HEIGHT = 0;
        private static final DurationCalculator DEFAULT_DURATION_CALCULATOR = new DurationCalculator() {
            private static final int MAX_DURATION = 300;
            private static final int MIN_DURATION = 200;

            @Override
            public long duration(int distance) {
                return Math.max(Math.min(MAX_DURATION, distance), MIN_DURATION);
            }
        };
        private static final boolean DEFAULT_IS_ENABLE_CENTER = false;

        private boolean isEnableIndicator = DEFAULT_IS_ENABLE_INDICATOR;
        private int indicatorHeight = DEFAULT_INDICATOR_HEIGHT;
        private int indicatorColor = DEFAULT_INDICATOR_COLOR;
        private boolean isMultipleIndicatorColor = DEFAULT_IS_MULTIPLE_INDICATOR_COLOR;
        private int[] indicatorColors = DEFAULT_INDICATOR_COLORS;
        private boolean isEnableDivider = DEFAULT_IS_ENABLE_DIVIDER;
        private int dividerType = DEFAULT_DIVIDER_TYPE;
        private int dividerColor = DEFAULT_DIVIDER_COLOR;
        private int dividerHeight = DEFAULT_DIVIDER_HEIGHT;
        private DurationCalculator durationCalculator = DEFAULT_DURATION_CALCULATOR;
        private boolean isEnableCenter;

        private Config() {
            isEnableIndicator = DEFAULT_IS_ENABLE_INDICATOR;
            indicatorHeight = DEFAULT_INDICATOR_HEIGHT;
            indicatorColor = DEFAULT_INDICATOR_COLOR;
            isMultipleIndicatorColor = DEFAULT_IS_MULTIPLE_INDICATOR_COLOR;
            indicatorColors = DEFAULT_INDICATOR_COLORS;
            isEnableDivider = DEFAULT_IS_ENABLE_DIVIDER;
            dividerType = DEFAULT_DIVIDER_TYPE;
            dividerColor = DEFAULT_DIVIDER_COLOR;
            dividerHeight = DEFAULT_DIVIDER_HEIGHT;
            durationCalculator = DEFAULT_DURATION_CALCULATOR;
            isEnableCenter = DEFAULT_IS_ENABLE_CENTER;
        }

        @Deprecated
        public Config(Config config) {
            if (config == null) {
                return;
            }

            this.isEnableDivider = config.isEnableDivider;
            this.indicatorHeight = config.indicatorHeight;
            this.indicatorColor = config.indicatorColor;
            this.isMultipleIndicatorColor = config.isMultipleIndicatorColor;
            if (this.indicatorColors != null) {
                this.indicatorColors = Arrays.copyOf(this.indicatorColors, this.indicatorColors.length);
            }
            this.isEnableDivider = config.isEnableDivider;
            this.dividerType = config.dividerType;
            this.dividerColor = config.dividerColor;
            this.dividerHeight = config.dividerHeight;
            this.durationCalculator = config.durationCalculator;
        }

        @Deprecated
        public Config setEnableIndicator(boolean enableIndicator) {
            isEnableIndicator = enableIndicator;
            return this;
        }

        @Deprecated
        public Config setIndicatorHeight(int indicatorHeight) {
            this.indicatorHeight = indicatorHeight;
            return this;
        }

        @Deprecated
        public Config setIndicatorColor(int indicatorColor) {
            this.indicatorColor = indicatorColor;
            return this;
        }

        @Deprecated
        public Config setMultipleIndicatorColor(boolean multipleIndicatorColor) {
            isMultipleIndicatorColor = multipleIndicatorColor;
            return this;
        }

        @Deprecated
        public Config setIndicatorColors(int[] indicatorColors) {
            this.indicatorColors = indicatorColors;
            return this;
        }

        @Deprecated
        public Config setEnableDivider(boolean enableDivider) {
            isEnableDivider = enableDivider;
            return this;
        }

        @Deprecated
        public Config setDividerType(int dividerType) {
            this.dividerType = dividerType;
            return this;
        }

        @Deprecated
        public Config setDividerColor(int dividerColor) {
            this.dividerColor = dividerColor;
            return this;
        }

        @Deprecated
        public Config setDividerHeight(int dividerHeight) {
            this.dividerHeight = dividerHeight;
            return this;
        }

        @Deprecated
        public void setDurationCalculator(DurationCalculator durationCalculator) {
            this.durationCalculator = durationCalculator;
        }

        public boolean isEnableIndicator() {
            return isEnableIndicator;
        }

        public int getIndicatorHeight() {
            return indicatorHeight;
        }

        public int getIndicatorColor() {
            return indicatorColor;
        }

        public boolean isMultipleIndicatorColor() {
            return isMultipleIndicatorColor;
        }

        public int[] getIndicatorColors() {
            return indicatorColors;
        }

        public boolean isEnableDivider() {
            return isEnableDivider;
        }

        public int getDividerType() {
            return dividerType;
        }

        public int getDividerColor() {
            return dividerColor;
        }

        public int getDividerHeight() {
            return dividerHeight;
        }

        public DurationCalculator getDurationCalculator() {
            return durationCalculator;
        }

        public boolean isEnableCenter() {
            return isEnableCenter;
        }


        @Override
        protected Config clone() {
            Config config = new Config();
            config.isEnableDivider = this.isEnableDivider;
            config.indicatorHeight = this.indicatorHeight;
            config.indicatorColor = this.indicatorColor;
            config.isMultipleIndicatorColor = this.isMultipleIndicatorColor;
            if (this.indicatorColors != null) {
                config.indicatorColors = Arrays.copyOf(this.indicatorColors, this.indicatorColors.length);
            }
            config.isEnableDivider = this.isEnableDivider;
            config.dividerType = this.dividerType;
            config.dividerColor = this.dividerColor;
            config.dividerHeight = this.dividerHeight;
            config.durationCalculator = this.durationCalculator;
            config.isEnableCenter = this.isEnableCenter;
            return config;
        }

        public static class Builder {
            private Config config;

            public Builder() {
                config = new Config();
            }

            public Config build() {
                return config;
            }

            public Builder setEnableIndicator(boolean enableIndicator) {
                config.isEnableIndicator = enableIndicator;
                return this;
            }

            public Builder setIndicatorHeight(int indicatorHeight) {
                config.indicatorHeight = indicatorHeight;
                return this;
            }

            public Builder setIndicatorColor(int indicatorColor) {
                config.indicatorColor = indicatorColor;
                return this;
            }

            public Builder setMultipleIndicatorColor(boolean multipleIndicatorColor) {
                config.isMultipleIndicatorColor = multipleIndicatorColor;
                return this;
            }

            public Builder setIndicatorColors(int[] indicatorColors) {
                config.indicatorColors = indicatorColors;
                return this;
            }

            public Builder setEnableDivider(boolean enableDivider) {
                config.isEnableDivider = enableDivider;
                return this;
            }

            public Builder setDividerType(int dividerType) {
                config.dividerType = dividerType;
                return this;
            }

            public Builder setDividerColor(int dividerColor) {
                config.dividerColor = dividerColor;
                return this;
            }

            public Builder setDividerHeight(int dividerHeight) {
                config.dividerHeight = dividerHeight;
                return this;
            }

            public Builder setDurationCalculator(DurationCalculator durationCalculator) {
                config.durationCalculator = durationCalculator;
                return this;
            }

            public Builder setEnableCenter(boolean enableCenter) {
                config.isEnableCenter = enableCenter;
                return this;
            }
        }
    }

    public interface OnTabSelectedListener<T> {
        void onTabSelected(int position, T item);
    }

    public static class TopTabLayoutOnPageChangeListener implements ViewPager.OnPageChangeListener {
        private final WeakReference<TopTabLayout> mTopTabLayoutRef;
        private int mCurrPageScrollState;
        private ArgbEvaluator mArgbEvaluator;
        private boolean isSelected;

        public TopTabLayoutOnPageChangeListener(TopTabLayout topTabLayout) {
            mTopTabLayoutRef = new WeakReference<>(topTabLayout);
            mCurrPageScrollState = SCROLL_STATE_IDLE;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (isSelected) {
                return;
            }
            final TopTabLayout topTabLayout = mTopTabLayoutRef.get();
            if (topTabLayout == null) {
                return;
            }
            int currPosition = topTabLayout.mSelection;
            int scrollPosition = position == currPosition ? position + 1 : position;
            View itemView = null;
            for (int i = 0; i < topTabLayout.getChildCount(); i++) {
                View child = topTabLayout.getChildAt(i);
                int childPosition = topTabLayout.getChildAdapterPosition(child);
                if (childPosition == scrollPosition) {
                    itemView = child;
                    break;
                }
            }
            if (itemView == null) {
                topTabLayout.smoothScrollToPosition(scrollPosition);
            } else {
                boolean direction = scrollPosition > currPosition;
                float offset = direction ? positionOffset : 1 - positionOffset;

                int originWidth = topTabLayout.mIndicatorOriginRight - topTabLayout.mIndicatorOriginLeft;
                int targetWidth = itemView.getWidth();
                int locationOffset = (int) ((topTabLayout.mIndicatorOriginLeft - itemView.getX()) * offset);
                int widthOffset = (int) ((originWidth - targetWidth) * offset);
                topTabLayout.mIndicatorLeft = topTabLayout.mIndicatorOriginLeft - locationOffset;
                topTabLayout.mIndicatorRight = topTabLayout.mIndicatorOriginRight - locationOffset - widthOffset;

                if (topTabLayout.mConfig.isMultipleIndicatorColor) {
                    if (mArgbEvaluator == null) {
                        mArgbEvaluator = new ArgbEvaluator();
                    }
                    int startColor = topTabLayout.mConfig.indicatorColors[currPosition];
                    int endColor = topTabLayout.mConfig.indicatorColors[scrollPosition];
                    int currColor = (int) mArgbEvaluator.evaluate(offset, startColor, endColor);
                    topTabLayout.mIndicatorColor = currColor;
                }

                topTabLayout.invalidate();
            }
        }

        @Override
        public void onPageSelected(int position) {
            final TopTabLayout topTabLayout = mTopTabLayoutRef.get();
            if (topTabLayout == null) {
                return;
            }
            topTabLayout.setSelection(position);
            isSelected = true;
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            switch (state) {
                case ViewPager.SCROLL_STATE_DRAGGING:
                    if (mCurrPageScrollState == ViewPager.SCROLL_STATE_IDLE) {
                        TopTabLayout topTabLayout = mTopTabLayoutRef.get();
                        if (topTabLayout != null) {
                            topTabLayout.mIndicatorOriginLeft = topTabLayout.mIndicatorLeft;
                            topTabLayout.mIndicatorOriginRight = topTabLayout.mIndicatorRight;
                        }
                    }
                    break;
                case ViewPager.SCROLL_STATE_IDLE:
                    if (mCurrPageScrollState == ViewPager.SCROLL_STATE_SETTLING) {
                        TopTabLayout topTabLayout = mTopTabLayoutRef.get();
                        if (topTabLayout != null) {
                            isSelected = false;
                        }
                    }
                    break;
            }
            mCurrPageScrollState = state;
        }
    }

    public static class ViewPagerOnTabSelectedListener<T> implements OnTabSelectedListener<T> {
        private TopTabViewPager mTopTabViewPager;

        public ViewPagerOnTabSelectedListener(TopTabViewPager topTabViewPager) {
            this.mTopTabViewPager = topTabViewPager;
        }

        @Override
        public void onTabSelected(int position, T item) {
            mTopTabViewPager.forceSetCurrentItem(position);
        }
    }
}