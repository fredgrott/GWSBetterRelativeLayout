package com.github.shareme.gwsbetterrelativelayout.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;

import com.github.shareme.gwsbetterrelativelayout.library.LayoutMath.Value;
import com.github.shareme.gwsbetterrelativelayout.library.LayoutMath.ValueWrapper;

import java.util.Stack;

@SuppressWarnings("unused")
public class SpringLayout extends ViewGroup {
    private static int RELATIVE_SIZE_DENOMINATOR = 100;

    public static final int PARENT = -2;
    public static final int TRUE = -1;

    /**
     * Rule that aligns a child's right edge with another child's left edge.
     */
    public static final int LEFT_OF = 0;
    /**
     * Rule that aligns a child's left edge with another child's right edge.
     */
    public static final int RIGHT_OF = 1;
    /**
     * Rule that aligns a child's bottom edge with another child's top edge.
     */
    public static final int ABOVE = 2;
    /**
     * Rule that aligns a child's top edge with another child's bottom edge.
     */
    public static final int BELOW = 3;
    /**
     * Rule that aligns a child's left edge with another child's left edge.
     */
    public static final int ALIGN_LEFT = 4;
    /**
     * Rule that aligns a child's top edge with another child's top edge.
     */
    public static final int ALIGN_TOP = 5;
    /**
     * Rule that aligns a child's right edge with another child's right edge.
     */
    public static final int ALIGN_RIGHT = 6;
    /**
     * Rule that aligns a child's bottom edge with another child's bottom edge.
     */
    public static final int ALIGN_BOTTOM = 7;
    /**
     * Center will be aligned both horizontally and vertically.
     */
    public static final int ALIGN_CENTER = 8;
    /**
     * Center will be aligned horizontally.
     */
    public static final int ALIGN_CENTER_HORIZONTALLY = 9;
    /**
     * Center will be aligned vertically.
     */
    public static final int ALIGN_CENTER_VERTICALLY = 10;

    /**
     * Rule that aligns the child's left edge with its SpringLayout parent's
     * left edge.
     */
    private static final int ALIGN_PARENT_LEFT = 11;
    /**
     * Rule that aligns the child's top edge with its SpringLayout parent's top
     * edge.
     */
    private static final int ALIGN_PARENT_TOP = 12;
    /**
     * Rule that aligns the child's right edge with its SpringLayout parent's
     * right edge.
     */
    private static final int ALIGN_PARENT_RIGHT = 13;
    /**
     * Rule that aligns the child's bottom edge with its SpringLayout parent's
     * bottom edge.
     */
    private static final int ALIGN_PARENT_BOTTOM = 14;

    /**
     * Rule that centers the child with respect to the bounds of its
     * SpringLayout parent.
     */
    public static final int CENTER_IN_PARENT = 15;
    /**
     * Rule that centers the child horizontally with respect to the bounds of
     * its SpringLayout parent.
     */
    public static final int CENTER_HORIZONTAL = 16;
    /**
     * Rule that centers the child vertically with respect to the bounds of its
     * SpringLayout parent.
     */
    public static final int CENTER_VERTICAL = 17;

    private static final int VERB_COUNT = 18;

    private static int[] VALID_RELATIONS = new int[] { LEFT_OF, RIGHT_OF, ALIGN_LEFT, ALIGN_RIGHT, ABOVE, BELOW, ALIGN_TOP, ALIGN_BOTTOM,
            ALIGN_CENTER_HORIZONTALLY, ALIGN_CENTER_VERTICALLY };
    
    // Constants for error reporting purpose
    private static final int TOP = 0;
    private static final int BOTTOM = 1;
    private static final int LEFT = 2;
    private static final int RIGHT = 3;
    private static final String[] ANCHOR_NAMES = new String[] { "top", "bottom", "left", "right" };

    private ViewConstraints mRootConstraints;
    private final SparseIntArray mIdToViewConstraints = new SparseIntArray();
    private ViewConstraints[] mViewConstraints;
    private final Stack<ViewConstraints> mSpringMetrics = new Stack<>();
    private final SimpleIdentitySet<ViewConstraints> mHorizontalChains = new SimpleIdentitySet<>();
    private final SimpleIdentitySet<ViewConstraints> mVerticalChains = new SimpleIdentitySet<>();

    private LayoutMath mLayoutMath = new LayoutMath();

    private boolean mDirtyHierarchy = true;
    private boolean mDirtySize = true;

    private int mMinWidth = 0, mMinHeight = 0;

    public SpringLayout(Context context) {
        super(context);
    }

    public SpringLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initFromAttributes(context, attrs);
    }

    public SpringLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initFromAttributes(context, attrs);
    }

    private void initFromAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SpringLayout);
        setMinimumWidth(a.getDimensionPixelSize(R.styleable.SpringLayout_minWidth, 0));
        setMinimumHeight(a.getDimensionPixelSize(R.styleable.SpringLayout_minHeight, 0));
        a.recycle();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        mDirtyHierarchy = true;
        super.addView(child, index, params);
    }

    @Override
    public void removeView(View view) {
        mDirtyHierarchy = true;
        super.removeView(view);
    }

    @Override
    public void removeViewAt(int index) {
        mDirtyHierarchy = true;
        super.removeViewAt(index);
    }

    @Override
    public void removeViews(int start, int count) {
        mDirtyHierarchy = true;
        super.removeViews(start, count);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        mDirtySize = true;

        if (!mDirtyHierarchy) {
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final LayoutParams params = ((LayoutParams) getChildAt(i).getLayoutParams());
                if (params.dirty) {
                    mDirtyHierarchy = true;
                    params.dirty = false;
                }
            }
        }
    }

    private void resizeViewConstraintsArray(int newLen) {
        if (mViewConstraints.length < newLen) {
            ViewConstraints[] oldConstraints = mViewConstraints;
            mViewConstraints = new ViewConstraints[newLen];
            System.arraycopy(oldConstraints, 0, mViewConstraints, 0, oldConstraints.length);
        }
    }

    private void createViewMetrics(Stack<ViewConstraints> springMetrics) {
        springMetrics.clear();
        mIdToViewConstraints.clear();

        if (mRootConstraints != null) {
            mRootConstraints.release();
            for (ViewConstraints mViewConstraint : mViewConstraints) {
                mViewConstraint.release();
            }

            mRootConstraints.reset(this);
            resizeViewConstraintsArray(getChildCount());
        } else {
            mRootConstraints = new ViewConstraints(this, mLayoutMath);
            mViewConstraints = new ViewConstraints[getChildCount()];
        }

        mRootConstraints.left.setValueObject(mLayoutMath.variable(0));
        mRootConstraints.top.setValueObject(mLayoutMath.variable(0));

        final int count = getChildCount();

        for (int i = 0; i < count; i++) {
            final View v = getChildAt(i);
            mIdToViewConstraints.append(v.getId(), i);
            if (mViewConstraints[i] == null) {
                mViewConstraints[i] = new ViewConstraints(v, mLayoutMath);
            } else {
                mViewConstraints[i].reset(v);
            }
        }

        for (int i = 0; i < count; i++) {
            final ViewConstraints viewConstraints = mViewConstraints[i];
            final LayoutParams layoutParams = (LayoutParams) viewConstraints.getView().getLayoutParams();

            if (layoutParams.getWidthWeight() > 0) {
                viewConstraints.markAsHorizontalSpring();
            }

            if (layoutParams.getHeightWeight() > 0) {
                viewConstraints.markAsVerticalSpring();
            }

            int[] childRules = layoutParams.getRelations();
            for (int relation : VALID_RELATIONS) {
                final ViewConstraints metrics = getViewMetrics(childRules[relation]);
                if (metrics != null) {
                    metrics.updateRelation(viewConstraints, relation);
                }
            }
            if (viewConstraints.isHorizontalSpring() || viewConstraints.isVerticalSpring()) {
                springMetrics.add(viewConstraints);
            }
        }
    }

    private ViewConstraints getViewMetrics(int id) {
        if (id == PARENT) {
            return mRootConstraints;
        } else if (id > 0 && mIdToViewConstraints.indexOfKey(id) >= 0) {
            return mViewConstraints[mIdToViewConstraints.get(id)];
        }
        return null;
    }

    private void adaptLayoutParameters() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final LayoutParams childParams = (LayoutParams) child.getLayoutParams();
            int[] relations = childParams.getRelations();

            if (childParams.getWidthWeight() > 0 && childParams.width != LayoutParams.WRAP_CONTENT) {
                throw new IllegalArgumentException("widthWeight > 0 not supported for layout_width != WRAP_CONTENT in View: " + child);
            }

            if (childParams.getHeightWeight() > 0 && childParams.height != LayoutParams.WRAP_CONTENT) {
                throw new IllegalArgumentException("heightWeight > 0 not supported for layout_height != WRAP_CONTENT in View: " + child);
            }

            // If view is aligned both to parent's top and bottom (left and
            // right) then its height (width) is MATCH_PARENT and the other way
            // around
            if (relations[ALIGN_PARENT_TOP] != 0 && relations[ALIGN_PARENT_BOTTOM] != 0) {
                childParams.height = LayoutParams.MATCH_PARENT;
            } else if (childParams.height == LayoutParams.MATCH_PARENT) {
                relations[ALIGN_PARENT_TOP] = relations[ALIGN_PARENT_BOTTOM] = TRUE;
            }

            if (relations[ALIGN_PARENT_LEFT] != 0 && relations[ALIGN_PARENT_RIGHT] != 0) {
                childParams.width = LayoutParams.MATCH_PARENT;
            } else if (childParams.width == LayoutParams.MATCH_PARENT) {
                relations[ALIGN_PARENT_LEFT] = relations[ALIGN_PARENT_RIGHT] = TRUE;
            }

            if (relations[ALIGN_PARENT_TOP] == TRUE) {
                relations[ALIGN_TOP] = PARENT;
            }

            if (relations[ALIGN_PARENT_BOTTOM] == TRUE) {
                relations[ALIGN_BOTTOM] = PARENT;
            }

            if (relations[ALIGN_PARENT_LEFT] == TRUE) {
                relations[ALIGN_LEFT] = PARENT;
            }

            if (relations[ALIGN_PARENT_RIGHT] == TRUE) {
                relations[ALIGN_RIGHT] = PARENT;
            }

            if (relations[ALIGN_CENTER] != 0) {
                relations[ALIGN_CENTER_HORIZONTALLY] = relations[ALIGN_CENTER];
                relations[ALIGN_CENTER_VERTICALLY] = relations[ALIGN_CENTER];
            }

            if (relations[CENTER_IN_PARENT] == TRUE) {
                relations[CENTER_HORIZONTAL] = relations[CENTER_VERTICAL] = TRUE;
            }

            if (relations[CENTER_HORIZONTAL] == TRUE) {
                relations[ALIGN_CENTER_HORIZONTALLY] = PARENT;
            }

            if (relations[CENTER_VERTICAL] == TRUE) {
                relations[ALIGN_CENTER_VERTICALLY] = PARENT;
            }

            if (!hasHorizontalRelations(relations)) {
                relations[ALIGN_LEFT] = PARENT;
            }

            if (!hasVerticalRelations(relations)) {
                relations[ALIGN_TOP] = PARENT;
            }
        }
    }

    private boolean hasHorizontalRelations(int[] relations) {
        return relations[LEFT_OF] != 0 || relations[RIGHT_OF] != 0 || relations[ALIGN_LEFT] != 0 || relations[ALIGN_RIGHT] != 0
                || relations[ALIGN_CENTER_HORIZONTALLY] != 0;
    }

    private boolean hasVerticalRelations(int[] relations) {
        return relations[BELOW] != 0 || relations[ABOVE] != 0 || relations[ALIGN_TOP] != 0 || relations[ALIGN_BOTTOM] != 0
                || relations[ALIGN_CENTER_VERTICALLY] != 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int myWidth = -1;
        int myHeight = -1;
        int width = 0;
        int height = 0;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        final boolean isWrapContentWidth = widthMode != MeasureSpec.EXACTLY;
        final boolean isWrapContentHeight = heightMode != MeasureSpec.EXACTLY;

        if (mDirtyHierarchy) {
            mDirtyHierarchy = false;
            adaptLayoutParameters();
            createViewMetrics(mSpringMetrics);
            handleSprings(mSpringMetrics, isWrapContentWidth, isWrapContentHeight);
        }

        // Record our dimensions if they are known;
        if (widthMode != MeasureSpec.UNSPECIFIED) {
            myWidth = widthSize;
        }

        if (heightMode != MeasureSpec.UNSPECIFIED) {
            myHeight = heightSize;
        }

        if (widthMode == MeasureSpec.EXACTLY) {
            width = myWidth;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = myHeight;
        }

        if (mDirtySize) {
            mDirtySize = false;
            invalidateMathCache();
            updateChildrenSize(widthMeasureSpec, heightMeasureSpec);
            updateLayoutSize(isWrapContentWidth, width, isWrapContentHeight, height);
            cacheLayoutPositions();
        }

        setMeasuredDimension(mRootConstraints.right.getValue(), mRootConstraints.bottom.getValue());
    }

    private void invalidateMathCache() {
        mRootConstraints.invalidate();
        for (int i = 0; i < getChildCount(); i++) {
            final ViewConstraints viewConstraints = mViewConstraints[i];
            viewConstraints.invalidate();
        }
    }

    private void updateChildrenSize(final int widthMeasureSpec, final int heightMeasureSpec) {
        for (int i = 0; i < getChildCount(); i++) {
            final ViewConstraints viewConstraints = mViewConstraints[i];
            final View v = viewConstraints.getView();
            final LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
            final int mL = layoutParams.leftMargin, mR = layoutParams.rightMargin, mT = layoutParams.topMargin, mB = layoutParams.bottomMargin;
            measureChildWithMargins(v, widthMeasureSpec, 0, heightMeasureSpec, 0);

            if (!viewConstraints.isHorizontalSpring()) {
                Value childWidth;
                if (v.getVisibility() == View.GONE) {
                    childWidth = mLayoutMath.variable(0);
                } else if (layoutParams.relativeWidth > 0) {
                    childWidth = mRootConstraints.innerRight.subtract(mRootConstraints.innerLeft)
                            .multiply(mLayoutMath.variable(layoutParams.relativeWidth))
                            .divide(mLayoutMath.variable(RELATIVE_SIZE_DENOMINATOR));
                } else {
                    childWidth = mLayoutMath.variable(v.getMeasuredWidth());
                }

                viewConstraints.leftMargin.setValue(mL);
                viewConstraints.rightMargin.setValue(mR);

                Value outerWidth = childWidth.add(mLayoutMath.variable(mL + mR)).retain();
                viewConstraints.setWidth(outerWidth);
                outerWidth.release();
            }

            if (!viewConstraints.isVerticalSpring()) {
                Value childHeight;
                if (v.getVisibility() == View.GONE) {
                    childHeight = mLayoutMath.variable(0);
                } else if (layoutParams.relativeHeight > 0) {
                    childHeight = mRootConstraints.innerBottom.subtract(mRootConstraints.innerTop)
                            .multiply(mLayoutMath.variable(layoutParams.relativeHeight))
                            .divide(mLayoutMath.variable(RELATIVE_SIZE_DENOMINATOR));
                } else {
                    childHeight = mLayoutMath.variable(v.getMeasuredHeight());
                }

                viewConstraints.topMargin.setValue(mT);
                viewConstraints.bottomMargin.setValue(mB);

                Value outerHeight = childHeight.add(mLayoutMath.variable(mT + mB)).retain();
                viewConstraints.setHeight(outerHeight);
                outerHeight.release();
            }
        }
    }

    private void handleSprings(final Stack<ViewConstraints> springMetrics, final boolean isWrapContentWidth,
            final boolean isWrapContentHeight) {
        if (!springMetrics.isEmpty()) {
            mHorizontalChains.clear();
            mVerticalChains.clear();
            while (!springMetrics.isEmpty()) {
                final ViewConstraints spring = springMetrics.pop();
                final ViewConstraints chainHeadX = getChainHorizontalHead(spring);
                final ViewConstraints chainHeadY = getChainVerticalHead(spring);
                if (chainHeadX != null) {
                    if (isWrapContentWidth && mMinWidth <= 0) {
                        throw new IllegalStateException("Horizontal springs not supported when layout width is wrap_content");
                    }
                    mHorizontalChains.add(chainHeadX);
                }
                if (chainHeadY != null) {
                    if (isWrapContentHeight && mMinHeight <= 0) {
                        throw new IllegalStateException(
                                "Vertical springs not supported when layout height is wrap_content and minHeight is not defined");
                    }
                    mVerticalChains.add(chainHeadY);
                }
            }

            for (int i = 0; i < mHorizontalChains.size(); i++) {
                final ViewConstraints chainHead = mHorizontalChains.get(i);
                int totalWeight = 0;
                Value contentWidth = mLayoutMath.variable(0);
                final ValueWrapper totalWeightWrapper = mLayoutMath.wrap();
                final ValueWrapper chainWidthWrapper = mLayoutMath.wrap();
                ViewConstraints chainElem = chainHead, prevElem = null;
                Value start = chainElem.left, end;
                while (chainElem != null) {
                    if (chainElem.isHorizontalSpring()) {
                        chainElem.markHorizontalSpringUsed();
                        final int weight = ((LayoutParams) chainElem.getView().getLayoutParams()).widthWeight;
                        totalWeight += weight;
                        final Value width = chainWidthWrapper.multiply(mLayoutMath.variable(weight)).divide(totalWeightWrapper)
                                .max(mLayoutMath.variable(0)).retain();
                        chainElem.setWidth(width);
                        width.release();
                    } else {
                        contentWidth = contentWidth.add(chainElem.getWidth());
                    }
                    prevElem = chainElem;
                    chainElem = chainElem.nextX;
                }
                end = prevElem.right;
                totalWeightWrapper.setValueObject(mLayoutMath.variable(totalWeight));
                chainWidthWrapper.setValueObject(end.subtract(start).subtract(contentWidth));
            }

            for (int i = 0; i < mVerticalChains.size(); i++) {
                final ViewConstraints chainHead = mVerticalChains.get(i);
                int totalWeight = 0;
                Value contentHeight = mLayoutMath.variable(0);
                final ValueWrapper totalWeightWrapper = mLayoutMath.wrap();
                final ValueWrapper chainWidthWrapper = mLayoutMath.wrap();
                ViewConstraints chainElem = chainHead, prevElem = null;
                Value start = chainElem.top, end;
                while (chainElem != null) {
                    if (chainElem.isVerticalSpring()) {
                        chainElem.markVerticalSpringUsed();
                        final int weight = ((LayoutParams) chainElem.getView().getLayoutParams()).heightWeight;
                        totalWeight += weight;
                        final Value height = chainWidthWrapper.multiply(mLayoutMath.variable(weight)).divide(totalWeightWrapper)
                                .max(mLayoutMath.variable(0)).retain();
                        chainElem.setHeight(height);
                        height.release();
                    } else {
                        contentHeight = contentHeight.add(chainElem.getHeight());
                    }
                    prevElem = chainElem;
                    chainElem = chainElem.nextY;
                }
                end = prevElem.bottom;
                totalWeightWrapper.setValueObject(mLayoutMath.variable(totalWeight));
                chainWidthWrapper.setValueObject(end.subtract(start).subtract(contentHeight));
            }
        }
    }

    private void updateLayoutSize(final boolean isWrapContentWidth, int width, final boolean isWrapContentHeight, int height) {
        final int pL = getPaddingLeft(), pR = getPaddingRight(), pT = getPaddingTop(), pB = getPaddingBottom();

        mRootConstraints.leftMargin.setValue(pL);
        mRootConstraints.rightMargin.setValue(pR);
        mRootConstraints.topMargin.setValue(pT);
        mRootConstraints.bottomMargin.setValue(pB);

        Value widthValue, heightValue;

        if (isWrapContentWidth) {
            int maxSize = mMinWidth > 0 ? mMinWidth : -1;
            for (int i = 0; i < getChildCount(); i++) {
                final ViewConstraints viewConstraints = mViewConstraints[i];
                try {
                    maxSize = Math.max(maxSize, viewConstraints.right.getValue() + pR);
                    viewConstraints.right.invalidate();
                } catch (IllegalStateException e) {
                }
            }
            if (maxSize < 0) {
                throw new IllegalStateException(
                        "Parent layout_width == wrap_content is not supported if width of all children depends on parent width.");
            }
            widthValue = mLayoutMath.variable(maxSize);
        } else {
            widthValue = mLayoutMath.variable(width);
        }
        mRootConstraints.right.setValueObject(widthValue);

        if (isWrapContentHeight) {
            int maxSize = mMinHeight > 0 ? mMinHeight : -1;
            for (int i = 0; i < getChildCount(); i++) {
                final ViewConstraints viewConstraints = mViewConstraints[i];
                try {
                    maxSize = Math.max(maxSize, viewConstraints.bottom.getValue() + pB);
                    viewConstraints.bottom.invalidate();
                } catch (IllegalStateException e) {
                }
            }
            if (maxSize < 0) {
                throw new IllegalStateException(
                        "Parent layout_height == wrap_content is not supported if height of all children depends on parent height.");
            }
            heightValue = mLayoutMath.variable(maxSize);
        } else {
            heightValue = mLayoutMath.variable(height);
        }
        mRootConstraints.bottom.setValueObject(heightValue);
    }

    private void cacheLayoutPositions() {
        for (int i = 0; i < getChildCount(); i++) {
            final ViewConstraints viewConstraints = mViewConstraints[i];
            final View v = viewConstraints.getView();
            if (viewConstraints.isHorizontalSpring() && !viewConstraints.isHorizontalSpringUsed()) {
                throw new IllegalStateException(
                        "Horizontal weight defined but never used, please review your layout. Remember that the chain of views cannot divert when using springs: Problematic view (please also check other dependant views): "
                                + v + ", problematic layout: " + this);
            } else if (viewConstraints.isVerticalSpring() && !viewConstraints.isVerticalSpringUsed()) {
                throw new IllegalStateException(
                        "Vertical weight defined but never used, please review your layout. Remember that the chain of views cannot divert when using springs: Problematic view (please also check other dependant views): "
                                + v + ", problematic layout: " + this);
            } else {
                int anchor = 0;
                try {
                    LayoutParams st = (LayoutParams) v.getLayoutParams();
                    anchor = LEFT;
                    st.left = viewConstraints.innerLeft.getValue();
                    anchor = RIGHT;
                    st.right = viewConstraints.innerRight.getValue();
                    anchor = TOP;
                    st.top = viewConstraints.innerTop.getValue();
                    anchor = BOTTOM;
                    st.bottom = viewConstraints.innerBottom.getValue();
                    v.measure(MeasureSpec.makeMeasureSpec(st.right - st.left, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(st.bottom - st.top, MeasureSpec.EXACTLY));
                } catch (IllegalStateException e) {
                    throw new IllegalStateException("View " + ANCHOR_NAMES[anchor] + " position could not be calculated, please review your layout. Remember that A.above = B and B.below = A are not equivalent in terms of calculation order, please refer to documentation. Problematic view (please also check other dependant views): "
                                    + v + ", problematic layout: " + this, e);
                } catch (StackOverflowError e) {
                    throw new IllegalStateException(
                            "Constraints of a view could not be resolved (circular dependency), please review your layout. Problematic view (please also check other dependant views): "
                                    + v + ", problematic layout: " + this);
                }
            }
        }
    }

    private ViewConstraints getChainVerticalHead(ViewConstraints spring) {
        if (spring.nextY == null && spring.prevY == null) {
            return null;
        } else {
            while (spring.prevY != null) {
                spring = spring.prevY;
            }
            return spring;
        }
    }

    private ViewConstraints getChainHorizontalHead(ViewConstraints spring) {
        if (spring.nextX == null && spring.prevX == null) {
            return null;
        } else {
            while (spring.prevX != null) {
                spring = spring.prevX;
            }
            return spring;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                LayoutParams st = (LayoutParams) child.getLayoutParams();
                child.layout(st.left, st.top, st.right, st.bottom);
            }
        }
    }

    @Override
    public void setMinimumHeight(int minHeight) {
        super.setMinimumHeight(minHeight);
        mMinHeight = minHeight;
    }

    @Override
    public void setMinimumWidth(int minWidth) {
        super.setMinimumWidth(minWidth);
        mMinWidth = minWidth;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    /**
     * Returns a set of layout parameters with a width of
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT}, a height of
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT} and no spanning.
     */
    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    /**
     * Per-child layout information associated with SpringLayout.
     * 
     * @attr ref R.styleable#SpringLayout_Layout_layout_toLeftOf
     * @attr ref R.styleable#SpringLayout_Layout_layout_toRightOf
     * @attr ref R.styleable#SpringLayout_Layout_layout_above
     * @attr ref R.styleable#SpringLayout_Layout_layout_below
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignBaseline
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignLeft
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignTop
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignRight
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignBottom
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignCenterHorizontally
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignCenterVertically
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignParentLeft
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignParentTop
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignParentRight
     * @attr ref R.styleable#SpringLayout_Layout_layout_alignParentBottom
     * @attr ref R.styleable#SpringLayout_Layout_layout_centerInParent
     * @attr ref R.styleable#SpringLayout_Layout_layout_centerHorizontal
     * @attr ref R.styleable#SpringLayout_Layout_layout_centerVertical
     */
    public static class LayoutParams extends MarginLayoutParams {
        @ViewDebug.ExportedProperty(resolveId = true, indexMapping = { @ViewDebug.IntToString(from = ABOVE, to = "above"),
                @ViewDebug.IntToString(from = BELOW, to = "below"), @ViewDebug.IntToString(from = LEFT_OF, to = "leftOf"),
                @ViewDebug.IntToString(from = RIGHT_OF, to = "rightOf"),
                @ViewDebug.IntToString(from = ALIGN_PARENT_LEFT, to = "alignParentLeft"),
                @ViewDebug.IntToString(from = ALIGN_PARENT_RIGHT, to = "alignParentRight"),
                @ViewDebug.IntToString(from = ALIGN_PARENT_TOP, to = "alignParentTop"),
                @ViewDebug.IntToString(from = ALIGN_PARENT_BOTTOM, to = "alignParentBottom"),
                @ViewDebug.IntToString(from = ALIGN_LEFT, to = "alignLeft"), @ViewDebug.IntToString(from = ALIGN_RIGHT, to = "alignRight"),
                @ViewDebug.IntToString(from = ALIGN_TOP, to = "alignTop"), @ViewDebug.IntToString(from = ALIGN_BOTTOM, to = "alignBottom"),
                @ViewDebug.IntToString(from = ALIGN_CENTER, to = "alignCenter"),
                @ViewDebug.IntToString(from = ALIGN_CENTER_HORIZONTALLY, to = "alignCenterHorizontally"),
                @ViewDebug.IntToString(from = ALIGN_CENTER_VERTICALLY, to = "alignCenterVertically"),
                @ViewDebug.IntToString(from = CENTER_HORIZONTAL, to = "centerHorizontal"),
                @ViewDebug.IntToString(from = CENTER_IN_PARENT, to = "centerInParent"),
                @ViewDebug.IntToString(from = CENTER_VERTICAL, to = "centerVertical"), }, mapping = {
                @ViewDebug.IntToString(from = TRUE, to = "true"), @ViewDebug.IntToString(from = 0, to = "false/NO_ID"),
                @ViewDebug.IntToString(from = PARENT, to = "parent") })
        int[] relations = new int[VERB_COUNT];
        int left, top, right, bottom;
        int relativeHeight, relativeWidth;
        int heightWeight = 0, widthWeight = 0;
        boolean dirty = true;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.SpringLayout_Layout);

            final int[] relations = this.relations;

            final int N = a.getIndexCount();
            for (int i = 0; i < N; i++) {
                int attr = a.getIndex(i);
                if (attr == R.styleable.SpringLayout_Layout_layout_toLeftOf) {
                    relations[LEFT_OF] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_toRightOf) {
                    relations[RIGHT_OF] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_above) {
                    relations[ABOVE] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_below) {
                    relations[BELOW] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignLeft) {
                    relations[ALIGN_LEFT] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignTop) {
                    relations[ALIGN_TOP] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignRight) {
                    relations[ALIGN_RIGHT] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignBottom) {
                    relations[ALIGN_BOTTOM] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignCenter) {
                    relations[ALIGN_CENTER] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignCenterHorizontally) {
                    relations[ALIGN_CENTER_HORIZONTALLY] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignCenterVertically) {
                    relations[ALIGN_CENTER_VERTICALLY] = a.getResourceId(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignParentLeft) {
                    relations[ALIGN_PARENT_LEFT] = a.getBoolean(attr, false) ? TRUE : 0;

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignParentTop) {
                    relations[ALIGN_PARENT_TOP] = a.getBoolean(attr, false) ? TRUE : 0;

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignParentRight) {
                    relations[ALIGN_PARENT_RIGHT] = a.getBoolean(attr, false) ? TRUE : 0;

                } else if (attr == R.styleable.SpringLayout_Layout_layout_alignParentBottom) {
                    relations[ALIGN_PARENT_BOTTOM] = a.getBoolean(attr, false) ? TRUE : 0;

                } else if (attr == R.styleable.SpringLayout_Layout_layout_centerInParent) {
                    relations[CENTER_IN_PARENT] = a.getBoolean(attr, false) ? TRUE : 0;

                } else if (attr == R.styleable.SpringLayout_Layout_layout_centerHorizontal) {
                    relations[CENTER_HORIZONTAL] = a.getBoolean(attr, false) ? TRUE : 0;

                } else if (attr == R.styleable.SpringLayout_Layout_layout_centerVertical) {
                    relations[CENTER_VERTICAL] = a.getBoolean(attr, false) ? TRUE : 0;

                } else if (attr == R.styleable.SpringLayout_Layout_layout_relativeWidth) {
                    relativeWidth = (int) a.getFraction(attr, RELATIVE_SIZE_DENOMINATOR, 1, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_relativeHeight) {
                    relativeHeight = (int) a.getFraction(attr, RELATIVE_SIZE_DENOMINATOR, 1, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_widthWeight) {
                    widthWeight = a.getInteger(attr, 0);

                } else if (attr == R.styleable.SpringLayout_Layout_layout_heightWeight) {
                    heightWeight = a.getInteger(attr, 0);

                }
            }

            a.recycle();
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public void addRelation(int relation, int anchor) {
            relations[relation] = anchor;
            dirty = true;
        }

        /**
         * Retrieves a complete list of all supported relations, where the index
         * is the relation verb, and the element value is the value specified,
         * or "false" if it was never set.
         * 
         * @return the supported relations
         * @see #addRelation(int, int)
         */
        public int[] getRelations() {
            return relations;
        }

        public int getRelativeHeight() {
            return relativeHeight;
        }

        public void setRelativeHeight(int relativeHeight) {
            this.relativeHeight = relativeHeight;
        }

        public int getRelativeWidth() {
            return relativeWidth;
        }

        public void setRelativeWidth(int relativeWidth) {
            dirty = true;
            this.relativeWidth = relativeWidth;
        }

        public int getWidthWeight() {
            return widthWeight;
        }

        public void setWidthWeight(int widthWeight) {
            dirty = true;
            this.widthWeight = widthWeight;
        }

        public int getHeightWeight() {
            return heightWeight;
        }

        public void setHeightWeight(int heightWeight) {
            dirty = true;
            this.heightWeight = heightWeight;
        }

        public void setWidth(int width) {
            if (this.width != width) {
                if (width == MATCH_PARENT || this.width == MATCH_PARENT) {
                    dirty = true;
                }
                if (width != WRAP_CONTENT || this.width == WRAP_CONTENT) {
                    this.widthWeight = 0;
                    dirty = true;
                }
                this.width = width;
            }
        }

        public void setHeight(int height) {
            if (this.height != height) {
                if (height == MATCH_PARENT || this.height == MATCH_PARENT) {
                    dirty = true;
                }
                if (height != WRAP_CONTENT) {
                    this.heightWeight = 0;
                    dirty = true;
                }
                this.height = height;
            }
        }
    }
}
