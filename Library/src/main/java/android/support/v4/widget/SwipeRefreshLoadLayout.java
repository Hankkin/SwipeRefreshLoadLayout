package android.support.v4.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by <a href="http://www.jiechic.com" target="_blank">jiechic</a> on 15/8/10.
 */
public class SwipeRefreshLoadLayout extends SwipeRefreshLayout {

    private LinearLayout mLoadView;
    private CircleImageView mLoadCircleView;

    // Default background for the progress spinner
    private static final int CIRCLE_BG_LIGHT = 0xFFFAFAFA;

    private static final int CIRCLE_DIAMETER = 40;
    private static final int CIRCLE_DIAMETER_LARGE = 56;

    private MaterialProgressDrawable mLoadProgress;

    private int mTouchSlop;
    private int mMediumAnimationDuration;
    private final DecelerateInterpolator mDecelerateInterpolator;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final int[] LAYOUT_ATTRS = new int[]{
            android.R.attr.enabled
    };
    private int mCircleWidth;

    private int mCircleHeight;
    private float mSpinnerFinalOffset;
    // Default offset in dips from the top of the view to where the progress spinner should stop
    private static final int DEFAULT_CIRCLE_TARGET = 64;
    private float mTotalDragDistance = -1;

    // Target is returning to its start offset because it was cancelled or a
    // refresh was triggered.
    private boolean mReturningToEnd;
    private View mTarget; // the target of the gesture
    private OnLoadingListener mListener;
    private boolean mLoading = false;
    private boolean mIsEndDragged;
    private int mActivePointerId = INVALID_POINTER;
    private static final int INVALID_POINTER = -1;

    private float mInitialMotionY;
    private float mInitialDownY;

    private static final int MAX_ALPHA = 255;

    private static final int STARTING_PROGRESS_ALPHA = (int) (.3f * MAX_ALPHA);
    private static final float DRAG_RATE = .5f;

    // Whether this item is scaled up rather than clipped
    private boolean mScale;
    private boolean mNotify;

    private Animation mScaleAnimation;

    private Animation mScaleUpAnimation;

    private Animation mAlphaStartAnimation;

    private Animation mAlphaMaxAnimation;

    private Animation mScaleUpToStartAnimation;

    private static final int SCALE_UP_DURATION = 150;
    // Max amount of circle that can be filled by progress during swipe gesture,
    // where 1.0 is a full circle
    private static final float MAX_PROGRESS_ANGLE = .8f;

    private boolean isSuperOntouch;


    public SwipeRefreshLoadLayout(Context context) {
        this(context, null);
    }

    public SwipeRefreshLoadLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mMediumAnimationDuration = getResources().getInteger(
                android.R.integer.config_mediumAnimTime);

        setWillNotDraw(false);
        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

        final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
        setEnabled(a.getBoolean(0, true));
        a.recycle();

        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        mCircleWidth = (int) (CIRCLE_DIAMETER * metrics.density);
        mCircleHeight = (int) (CIRCLE_DIAMETER * metrics.density);
        createLoadView();
        ViewCompat.setChildrenDrawingOrderEnabled(this, true);
        // the absolute offset has to take into account that the circle starts at an offset
        mSpinnerFinalOffset = DEFAULT_CIRCLE_TARGET * metrics.density;
        mTotalDragDistance = mSpinnerFinalOffset;

        setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        //避免复写上级的查找方法
        addView(mLoadView);
    }

    private void createLoadView() {
        mLoadView = new LinearLayout(getContext());
        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, Dp2Px(getContext(), 30));
        mLoadView.setLayoutParams(layoutParams);
        mLoadView.setGravity(Gravity.CENTER);

        mLoadCircleView = new CircleImageView(getContext(), CIRCLE_BG_LIGHT, CIRCLE_DIAMETER / 2);
        mLoadProgress = new MaterialProgressDrawable(getContext(), this);
        mLoadProgress.setBackgroundColor(CIRCLE_BG_LIGHT);
        mLoadCircleView.setImageDrawable(mLoadProgress);

        mLoadView.addView(mLoadCircleView);

        TextView textView = new TextView(getContext());
        textView.setText("Loading");
        mLoadView.addView(textView);

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        final int height = getMeasuredHeight();

        if (getChildCount() == 0) {
            return;
        }

        final int childHeight = height - getPaddingTop() - getPaddingBottom();

        int loadWidth = mLoadView.getMeasuredWidth();
        int loadHeight = mLoadView.getMeasuredHeight();
        mLoadView.layout(0, childHeight,
                loadWidth, childHeight + loadHeight);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mLoadView.measure(mLoadView.getMeasuredWidth(), mLoadView.getMeasuredHeight());
    }

    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mLoadView) && !(child instanceof CircleImageView)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();

        final int action = MotionEventCompat.getActionMasked(ev);

        if (mReturningToEnd && action == MotionEvent.ACTION_DOWN) {
            mReturningToEnd = false;
        }

        if (!isEnabled() || mReturningToEnd || canChildScrollDown() || mLoading) {
            // Fail fast if we're not in a state where a swipe is possible
            mReturningToEnd = false;
        } else {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
//                setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCircleView.getTop(), true);
                    mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                    mIsEndDragged = false;
                    final float initialDownY = getMotionEventY(ev, mActivePointerId);
                    if (initialDownY == -1) {
                        return false;
                    }
                    mInitialDownY = initialDownY;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mActivePointerId == INVALID_POINTER) {
                        mIsEndDragged = false;
                    } else {
                        final float y = getMotionEventY(ev, mActivePointerId);
                        if (y == -1) {
                            mIsEndDragged = false;
                        } else {
                            final float yDiff = y - mInitialDownY;
                            if (yDiff < -mTouchSlop && !mIsEndDragged) {
                                mInitialMotionY = mInitialDownY + mTouchSlop;
                                mIsEndDragged = true;
                                mLoadProgress.setAlpha(STARTING_PROGRESS_ALPHA);
                            } else {
                                mIsEndDragged = false;
                            }
                        }
                    }
                    break;
                case MotionEventCompat.ACTION_POINTER_UP:
//                onSecondaryPointerUp(ev);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mIsEndDragged = false;
                    mActivePointerId = INVALID_POINTER;
                    break;
            }
        }
        //判断是否给父类消费
        if (!mIsEndDragged){
            isSuperOntouch=super.onInterceptTouchEvent(ev);
        }

        return mIsEndDragged ? mIsEndDragged : super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (isSuperOntouch){
            return super.onTouchEvent(ev);
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        if (mReturningToEnd && action == MotionEvent.ACTION_DOWN) {
            mReturningToEnd = false;
        }

        if (!isEnabled() || mReturningToEnd || canChildScrollDown()) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        } else {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                    mIsEndDragged = false;
                    break;

                case MotionEvent.ACTION_MOVE: {
                    final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    if (pointerIndex < 0) {
                        return false;
                    }

                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
                    if (mIsEndDragged) {
                        mLoadProgress.showArrow(true);
                        float originalDragPercent = overscrollTop / mTotalDragDistance;
                        if (originalDragPercent < 0) {
                            return super.onTouchEvent(ev);
                        }
                        float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
                        float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;
                        float extraOS = Math.abs(overscrollTop) - mTotalDragDistance;
                        float slingshotDist = mSpinnerFinalOffset;
                        float tensionSlingshotPercent = Math.max(0,
                                Math.min(extraOS, slingshotDist * 2) / slingshotDist);
                        float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
                                (tensionSlingshotPercent / 4), 2)) * 2f;
                        float extraMove = (slingshotDist) * tensionPercent * 2;

                        int targetY = mOriginalOffsetTop
                                + (int) ((slingshotDist * dragPercent) + extraMove);
                        // where 1.0f is a full circle
                        if (mLoadCircleView.getVisibility() != View.VISIBLE) {
                            mLoadCircleView.setVisibility(View.VISIBLE);
                        }
                        if (!mScale) {
                            ViewCompat.setScaleX(mLoadCircleView, 1f);
                            ViewCompat.setScaleY(mLoadCircleView, 1f);
                        }
                        if (overscrollTop < mTotalDragDistance) {
                            if (mScale) {
//                                setAnimationProgress(overscrollTop / mTotalDragDistance);
                            }
//                            if (mLoadProgress.getAlpha() > STARTING_PROGRESS_ALPHA
//                                    && !isAnimationRunning(mAlphaStartAnimation)) {
//                                // Animate the alpha
//                                startProgressAlphaStartAnimation();
//                            }
                            float strokeStart = adjustedPercent * .8f;
                            mLoadProgress.setStartEndTrim(0f, Math.min(MAX_PROGRESS_ANGLE, strokeStart));
                            mLoadProgress.setArrowScale(Math.min(1f, adjustedPercent));
                        } else {
//                            if (mLoadProgress.getAlpha() < MAX_ALPHA
//                                    && !isAnimationRunning(mAlphaMaxAnimation)) {
//                                // Animate the alpha
//                                startProgressAlphaMaxAnimation();
//                            }
                        }
                        float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
                        mLoadProgress.setProgressRotation(rotation);
//                        setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop,
//                                true /* requires update */);
                    }
                    break;
                }
                case MotionEventCompat.ACTION_POINTER_DOWN: {
                    final int index = MotionEventCompat.getActionIndex(ev);
                    mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                    super.onTouchEvent(ev);
                    break;
                }

                case MotionEventCompat.ACTION_POINTER_UP:
//                    onSecondaryPointerUp(ev);
                    super.onTouchEvent(ev);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    super.onTouchEvent(ev);
                    if (mActivePointerId == INVALID_POINTER) {
                        if (action == MotionEvent.ACTION_UP) {
//                            Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                        }
                        return false;
                    }
                    final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
                    mIsEndDragged = false;
                    if (overscrollTop > mTotalDragDistance) {
//                        setRefreshing(true, true /* notify */);
                    } else {
                        // cancel refresh
                        mLoading = false;
                        mLoadProgress.setStartEndTrim(0f, 0f);
                        Animation.AnimationListener listener = null;
                        if (!mScale) {
                            listener = new Animation.AnimationListener() {

                                @Override
                                public void onAnimationStart(Animation animation) {
                                }

                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    if (!mScale) {
//                                        startScaleDownAnimation(null);
                                    }
                                }

                                @Override
                                public void onAnimationRepeat(Animation animation) {
                                }

                            };
                        }
//                        animateOffsetToStartPosition(mCurrentTargetOffsetTop, listener);
                        mLoadProgress.showArrow(false);
                    }
                    mActivePointerId = INVALID_POINTER;
                    return false;
                }
            }
        }
        return true;
    }


    private float getMotionEventY(MotionEvent ev, int activePointerId) {
        final int index = MotionEventCompat.findPointerIndex(ev, activePointerId);
        if (index < 0) {
            return -1;
        }
        return MotionEventCompat.getY(ev, index);
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    public boolean canChildScrollDown() {
        return ViewCompat.canScrollVertically(mTarget, 1);
//        if (android.os.Build.VERSION.SDK_INT < 14) {
//            if (mTarget instanceof AbsListView) {
//                final AbsListView absListView = (AbsListView) mTarget;
//                return absListView.getChildCount() > 0
//                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(absListView.getCount())
//                        .getBottom() < absListView.getPaddingTop());
//            } else {
//                return ViewCompat.canScrollVertically(mTarget, 1) || mTarget.getScrollY() > 0;
//            }
//        } else {
//            return ViewCompat.canScrollVertically(mTarget, 1);
//        }
    }

    /**
     * One of DEFAULT, or LARGE.
     */
    public void setSize(int size) {
        //parent set
        super.setSize(size);

        if (size != MaterialProgressDrawable.LARGE && size != MaterialProgressDrawable.DEFAULT) {
            return;
        }
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (size == MaterialProgressDrawable.LARGE) {
            mCircleHeight = mCircleWidth = (int) (CIRCLE_DIAMETER_LARGE * metrics.density);
        } else {
            mCircleHeight = mCircleWidth = (int) (CIRCLE_DIAMETER * metrics.density);
        }
        // force the bounds of the progress circle inside the circle view to
        // update by setting it to null before updating its size and then
        // re-setting it
        mLoadCircleView.setImageDrawable(null);
        mLoadProgress.updateSizes(size);
        mLoadCircleView.setImageDrawable(mLoadProgress);
    }

    /**
     * Set the color resources used in the progress animation from color resources.
     * The first color will also be the color of the bar that grows in response
     * to a user swipe gesture.
     *
     * @param colorResIds
     */
    public void setColorSchemeResources(int... colorResIds) {
        final Resources res = getResources();
        int[] colorRes = new int[colorResIds.length];
        for (int i = 0; i < colorResIds.length; i++) {
            colorRes[i] = res.getColor(colorResIds[i]);
        }
        setColorSchemeColors(colorRes);
    }

    /**
     * Set the colors used in the progress animation. The first
     * color will also be the color of the bar that grows in response to a user
     * swipe gesture.
     *
     * @param colors
     */
    public void setColorSchemeColors(int... colors) {
        //parent set
        super.setColorSchemeColors(colors);
        mLoadProgress.setColorSchemeColors(colors);
    }

    public int Dp2Px(Context context, float dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    public interface OnLoadingListener {
        public void onLoading();
    }

}
