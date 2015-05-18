/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.graphics.drawable;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.Animator.AnimatorListener;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class uses {@link android.animation.ObjectAnimator} and
 * {@link android.animation.AnimatorSet} to animate the properties of a
 * {@link android.graphics.drawable.VectorDrawable} to create an animated drawable.
 * <p>
 * AnimatedVectorDrawable are normally defined as 3 separate XML files.
 * </p>
 * <p>
 * First is the XML file for {@link android.graphics.drawable.VectorDrawable}.
 * Note that we allow the animation to happen on the group's attributes and path's
 * attributes, which requires they are uniquely named in this XML file. Groups
 * and paths without animations do not need names.
 * </p>
 * <li>Here is a simple VectorDrawable in this vectordrawable.xml file.
 * <pre>
 * &lt;vector xmlns:android=&quot;http://schemas.android.com/apk/res/android&quot;
 *     android:height=&quot;64dp&quot;
 *     android:width=&quot;64dp&quot;
 *     android:viewportHeight=&quot;600&quot;
 *     android:viewportWidth=&quot;600&quot; &gt;
 *     &lt;group
 *         android:name=&quot;rotationGroup&quot;
 *         android:pivotX=&quot;300.0&quot;
 *         android:pivotY=&quot;300.0&quot;
 *         android:rotation=&quot;45.0&quot; &gt;
 *         &lt;path
 *             android:name=&quot;v&quot;
 *             android:fillColor=&quot;#000000&quot;
 *             android:pathData=&quot;M300,70 l 0,-70 70,70 0,0 -70,70z&quot; /&gt;
 *     &lt;/group&gt;
 * &lt;/vector&gt;
 * </pre></li>
 * <p>
 * Second is the AnimatedVectorDrawable's XML file, which defines the target
 * VectorDrawable, the target paths and groups to animate, the properties of the
 * path and group to animate and the animations defined as the ObjectAnimators
 * or AnimatorSets.
 * </p>
 * <li>Here is a simple AnimatedVectorDrawable defined in this avd.xml file.
 * Note how we use the names to refer to the groups and paths in the vectordrawable.xml.
 * <pre>
 * &lt;animated-vector xmlns:android=&quot;http://schemas.android.com/apk/res/android&quot;
 *   android:drawable=&quot;@drawable/vectordrawable&quot; &gt;
 *     &lt;target
 *         android:name=&quot;rotationGroup&quot;
 *         android:animation=&quot;@anim/rotation&quot; /&gt;
 *     &lt;target
 *         android:name=&quot;v&quot;
 *         android:animation=&quot;@anim/path_morph&quot; /&gt;
 * &lt;/animated-vector&gt;
 * </pre></li>
 * <p>
 * Last is the Animator XML file, which is the same as a normal ObjectAnimator
 * or AnimatorSet.
 * To complete this example, here are the 2 animator files used in avd.xml:
 * rotation.xml and path_morph.xml.
 * </p>
 * <li>Here is the rotation.xml, which will rotate the target group for 360 degrees.
 * <pre>
 * &lt;objectAnimator
 *     android:duration=&quot;6000&quot;
 *     android:propertyName=&quot;rotation&quot;
 *     android:valueFrom=&quot;0&quot;
 *     android:valueTo=&quot;360&quot; /&gt;
 * </pre></li>
 * <li>Here is the path_morph.xml, which will morph the path from one shape to
 * the other. Note that the paths must be compatible for morphing.
 * In more details, the paths should have exact same length of commands , and
 * exact same length of parameters for each commands.
 * Note that the path strings are better stored in strings.xml for reusing.
 * <pre>
 * &lt;set xmlns:android=&quot;http://schemas.android.com/apk/res/android&quot;&gt;
 *     &lt;objectAnimator
 *         android:duration=&quot;3000&quot;
 *         android:propertyName=&quot;pathData&quot;
 *         android:valueFrom=&quot;M300,70 l 0,-70 70,70 0,0   -70,70z&quot;
 *         android:valueTo=&quot;M300,70 l 0,-70 70,0  0,140 -70,0 z&quot;
 *         android:valueType=&quot;pathType&quot;/&gt;
 * &lt;/set&gt;
 * </pre></li>
 *
 * @attr ref android.R.styleable#AnimatedVectorDrawable_drawable
 * @attr ref android.R.styleable#AnimatedVectorDrawableTarget_name
 * @attr ref android.R.styleable#AnimatedVectorDrawableTarget_animation
 */
public class AnimatedVectorDrawable extends Drawable implements Animatable {
    private static final String LOGTAG = "AnimatedVectorDrawable";

    private static final String ANIMATED_VECTOR = "animated-vector";
    private static final String TARGET = "target";

    private static final boolean DBG_ANIMATION_VECTOR_DRAWABLE = false;

    /**
     * The resources against which this drawable was created. Used to attempt
     * to inflate animators if applyTheme() doesn't get called.
     */
    private Resources mRes;

    private AnimatedVectorDrawableState mAnimatedVectorState;

    private boolean mMutated;

    public AnimatedVectorDrawable() {
        this(null, null);
    }

    private AnimatedVectorDrawable(AnimatedVectorDrawableState state, Resources res) {
        mAnimatedVectorState = new AnimatedVectorDrawableState(state, mCallback, res);
        mRes = res;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mAnimatedVectorState = new AnimatedVectorDrawableState(
                    mAnimatedVectorState, mCallback, null);
            mMutated = true;
        }
        return this;
    }

    /**
     * @hide
     */
    public void clearMutated() {
        super.clearMutated();
        mAnimatedVectorState.mVectorDrawable.clearMutated();
        mMutated = false;
    }

    @Override
    public ConstantState getConstantState() {
        mAnimatedVectorState.mChangingConfigurations = getChangingConfigurations();
        return mAnimatedVectorState;
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mAnimatedVectorState.getChangingConfigurations();
    }

    @Override
    public void draw(Canvas canvas) {
        mAnimatedVectorState.mVectorDrawable.draw(canvas);
        if (isStarted()) {
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mAnimatedVectorState.mVectorDrawable.setBounds(bounds);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return mAnimatedVectorState.mVectorDrawable.setState(state);
    }

    @Override
    protected boolean onLevelChange(int level) {
        return mAnimatedVectorState.mVectorDrawable.setLevel(level);
    }

    @Override
    public boolean onLayoutDirectionChange(int layoutDirection) {
        return mAnimatedVectorState.mVectorDrawable.setLayoutDirection(layoutDirection);
    }

    @Override
    public int getAlpha() {
        return mAnimatedVectorState.mVectorDrawable.getAlpha();
    }

    @Override
    public void setAlpha(int alpha) {
        mAnimatedVectorState.mVectorDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mAnimatedVectorState.mVectorDrawable.setColorFilter(colorFilter);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        mAnimatedVectorState.mVectorDrawable.setTintList(tint);
    }

    @Override
    public void setHotspot(float x, float y) {
        mAnimatedVectorState.mVectorDrawable.setHotspot(x, y);
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        mAnimatedVectorState.mVectorDrawable.setHotspotBounds(left, top, right, bottom);
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        mAnimatedVectorState.mVectorDrawable.setTintMode(tintMode);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mAnimatedVectorState.mVectorDrawable.setVisible(visible, restart);
        return super.setVisible(visible, restart);
    }

    @Override
    public boolean isStateful() {
        return mAnimatedVectorState.mVectorDrawable.isStateful();
    }

    @Override
    public int getOpacity() {
        return mAnimatedVectorState.mVectorDrawable.getOpacity();
    }

    @Override
    public int getIntrinsicWidth() {
        return mAnimatedVectorState.mVectorDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mAnimatedVectorState.mVectorDrawable.getIntrinsicHeight();
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        mAnimatedVectorState.mVectorDrawable.getOutline(outline);
    }

    @Override
    public void inflate(Resources res, XmlPullParser parser, AttributeSet attrs, Theme theme)
            throws XmlPullParserException, IOException {
        final AnimatedVectorDrawableState state = mAnimatedVectorState;

        int eventType = parser.getEventType();
        float pathErrorScale = 1;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                final String tagName = parser.getName();
                if (ANIMATED_VECTOR.equals(tagName)) {
                    final TypedArray a = obtainAttributes(res, theme, attrs,
                            R.styleable.AnimatedVectorDrawable);
                    int drawableRes = a.getResourceId(
                            R.styleable.AnimatedVectorDrawable_drawable, 0);
                    if (drawableRes != 0) {
                        VectorDrawable vectorDrawable = (VectorDrawable) res.getDrawable(
                                drawableRes, theme).mutate();
                        vectorDrawable.setAllowCaching(false);
                        vectorDrawable.setCallback(mCallback);
                        pathErrorScale = vectorDrawable.getPixelSize();
                        if (state.mVectorDrawable != null) {
                            state.mVectorDrawable.setCallback(null);
                        }
                        state.mVectorDrawable = vectorDrawable;
                    }
                    a.recycle();
                } else if (TARGET.equals(tagName)) {
                    final TypedArray a = obtainAttributes(res, theme, attrs,
                            R.styleable.AnimatedVectorDrawableTarget);
                    final String target = a.getString(
                            R.styleable.AnimatedVectorDrawableTarget_name);

                    final int animResId = a.getResourceId(
                            R.styleable.AnimatedVectorDrawableTarget_animation, 0);
                    if (animResId != 0) {
                        if (theme != null) {
                            final Animator objectAnimator = AnimatorInflater.loadAnimator(
                                    res, theme, animResId, pathErrorScale);
                            setupAnimatorsForTarget(target, objectAnimator);
                        } else {
                            // The animation may be theme-dependent. As a
                            // workaround until Animator has full support for
                            // applyTheme(), postpone loading the animator
                            // until we have a theme in applyTheme().
                            if (state.mPendingAnims == null) {
                                state.mPendingAnims = new ArrayList<>(1);
                            }
                            state.mPendingAnims.add(
                                    new PendingAnimator(animResId, pathErrorScale, target));

                        }
                    }
                    a.recycle();
                }
            }

            eventType = parser.next();
        }

        // If we don't have any pending animations, we don't need to hold a
        // reference to the resources.
        if (state.mPendingAnims == null) {
            mRes = null;
        }

        setupAnimatorSet();
    }

    private void setupAnimatorSet() {
        if (mAnimatedVectorState.mTempAnimators != null) {
            mAnimatedVectorState.mAnimatorSet.playTogether(mAnimatedVectorState.mTempAnimators);
            mAnimatedVectorState.mTempAnimators.clear();
            mAnimatedVectorState.mTempAnimators = null;
        }
    }

    @Override
    public boolean canApplyTheme() {
        return (mAnimatedVectorState != null && mAnimatedVectorState.canApplyTheme())
                || super.canApplyTheme();
    }

    @Override
    public void applyTheme(Theme t) {
        super.applyTheme(t);

        final VectorDrawable vectorDrawable = mAnimatedVectorState.mVectorDrawable;
        if (vectorDrawable != null && vectorDrawable.canApplyTheme()) {
            vectorDrawable.applyTheme(t);
        }

        if (t != null) {
            inflatePendingAnimators(t.getResources(), t);
        }
    }

    /**
     * Inflates pending animators, if any, against a theme. Clears the list of
     * pending animators.
     *
     * @param t the theme against which to inflate the animators
     */
    private void inflatePendingAnimators(@NonNull Resources res, @Nullable Theme t) {
        final ArrayList<PendingAnimator> pendingAnims = mAnimatedVectorState.mPendingAnims;
        if (pendingAnims != null) {
            mAnimatedVectorState.mPendingAnims = null;

            for (int i = 0, count = pendingAnims.size(); i < count; i++) {
                final PendingAnimator pendingAnimator = pendingAnims.get(i);
                final Animator objectAnimator = pendingAnimator.newInstance(res, t);
                setupAnimatorsForTarget(pendingAnimator.target, objectAnimator);
            }
        }
    }

    /**
     * Adds a listener to the set of listeners that are sent events through the life of an
     * animation.
     *
     * @param listener the listener to be added to the current set of listeners for this animation.
     */
    public void addListener(AnimatorListener listener) {
        mAnimatedVectorState.mAnimatorSet.addListener(listener);
    }

    /**
     * Removes a listener from the set listening to this animation.
     *
     * @param listener the listener to be removed from the current set of listeners for this
     *                 animation.
     */
    public void removeListener(AnimatorListener listener) {
        mAnimatedVectorState.mAnimatorSet.removeListener(listener);
    }

    /**
     * Gets the set of {@link android.animation.Animator.AnimatorListener} objects that are currently
     * listening for events on this <code>AnimatedVectorDrawable</code> object.
     *
     * @return List<AnimatorListener> The set of listeners.
     */
    public List<AnimatorListener> getListeners() {
        return mAnimatedVectorState.mAnimatorSet.getListeners();
    }

    private static class AnimatedVectorDrawableState extends ConstantState {
        int mChangingConfigurations;
        VectorDrawable mVectorDrawable;
        // Always have a valid animatorSet to handle all the listeners call.
        AnimatorSet mAnimatorSet = new AnimatorSet();
        // When parsing the XML, we build individual animator and store in this array. At the end,
        // we add this array into the mAnimatorSet.
        private ArrayList<Animator> mTempAnimators;
        ArrayMap<Animator, String> mTargetNameMap;
        ArrayList<PendingAnimator> mPendingAnims;

        public AnimatedVectorDrawableState(AnimatedVectorDrawableState copy,
                Callback owner, Resources res) {
            if (copy != null) {
                mChangingConfigurations = copy.mChangingConfigurations;
                if (copy.mVectorDrawable != null) {
                    final ConstantState cs = copy.mVectorDrawable.getConstantState();
                    if (res != null) {
                        mVectorDrawable = (VectorDrawable) cs.newDrawable(res);
                    } else {
                        mVectorDrawable = (VectorDrawable) cs.newDrawable();
                    }
                    mVectorDrawable = (VectorDrawable) mVectorDrawable.mutate();
                    mVectorDrawable.setCallback(owner);
                    mVectorDrawable.setLayoutDirection(copy.mVectorDrawable.getLayoutDirection());
                    mVectorDrawable.setBounds(copy.mVectorDrawable.getBounds());
                    mVectorDrawable.setAllowCaching(false);
                }
                if (copy.mAnimatorSet != null) {
                    final int numAnimators = copy.mTargetNameMap.size();
                    // Deep copy a animator set, and then setup the target map again.
                    mAnimatorSet = copy.mAnimatorSet.clone();
                    mTargetNameMap = new ArrayMap<>(numAnimators);
                    // Since the new AnimatorSet is cloned from the old one, the order must be the
                    // same inside the array.
                    ArrayList<Animator> oldAnim = copy.mAnimatorSet.getChildAnimations();
                    ArrayList<Animator> newAnim = mAnimatorSet.getChildAnimations();

                    for (int i = 0; i < numAnimators; ++i) {
                        // Target name must be the same for new and old
                        String targetName = copy.mTargetNameMap.get(oldAnim.get(i));

                        Object newTargetObject = mVectorDrawable.getTargetByName(targetName);
                        newAnim.get(i).setTarget(newTargetObject);
                        mTargetNameMap.put(newAnim.get(i), targetName);
                    }
                }

                // Shallow copy since the array is immutable after inflate().
                mPendingAnims = copy.mPendingAnims;
            } else {
                mVectorDrawable = new VectorDrawable();
            }
        }

        @Override
        public boolean canApplyTheme() {
            return (mVectorDrawable != null && mVectorDrawable.canApplyTheme())
                    || mPendingAnims != null || super.canApplyTheme();
        }

        @Override
        public Drawable newDrawable() {
            return new AnimatedVectorDrawable(this, null);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new AnimatedVectorDrawable(this, res);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }
    }

    /**
     * Basically a constant state for Animators until we actually implement
     * constant states for Animators.
     */
    private static class PendingAnimator {
        public final int animResId;
        public final float pathErrorScale;
        public final String target;

        public PendingAnimator(int animResId, float pathErrorScale, String target) {
            this.animResId = animResId;
            this.pathErrorScale = pathErrorScale;
            this.target = target;
        }

        public Animator newInstance(Resources res, Theme theme) {
            return AnimatorInflater.loadAnimator(res, theme, animResId, pathErrorScale);
        }
    }

    private void setupAnimatorsForTarget(String name, Animator animator) {
        Object target = mAnimatedVectorState.mVectorDrawable.getTargetByName(name);
        animator.setTarget(target);
        if (mAnimatedVectorState.mTempAnimators == null) {
            mAnimatedVectorState.mTempAnimators = new ArrayList<>();
            mAnimatedVectorState.mTargetNameMap = new ArrayMap<>();
        }
        mAnimatedVectorState.mTempAnimators.add(animator);
        mAnimatedVectorState.mTargetNameMap.put(animator, name);
        if (DBG_ANIMATION_VECTOR_DRAWABLE) {
            Log.v(LOGTAG, "add animator  for target " + name + " " + animator);
        }
    }

    @Override
    public boolean isRunning() {
        return mAnimatedVectorState.mAnimatorSet.isRunning();
    }

    private boolean isStarted() {
        return mAnimatedVectorState.mAnimatorSet.isStarted();
    }

    @Override
    public void start() {
        // If any one of the animator has not ended, do nothing.
        if (isStarted()) {
            return;
        }

        // Check for uninflated animators. We can remove this after we add
        // support for Animator.applyTheme(). See comments in inflate().
        if (mAnimatedVectorState.mPendingAnims != null) {
            // Attempt to load animators without applying a theme.
            if (mRes != null) {
                inflatePendingAnimators(mRes, null);
                mRes = null;
            } else {
                Log.e(LOGTAG, "Failed to load animators. Either the AnimatedVectorDrawable must be"
                        + " created using a Resources object or applyTheme() must be called with"
                        + " a non-null Theme object.");
            }

            mAnimatedVectorState.mPendingAnims = null;
        }

        mAnimatedVectorState.mAnimatorSet.start();
        invalidateSelf();
    }

    @Override
    public void stop() {
        mAnimatedVectorState.mAnimatorSet.end();
    }

    /**
     * Reverses ongoing animations or starts pending animations in reverse.
     * <p>
     * NOTE: Only works if all animations support reverse. Otherwise, this will
     * do nothing.
     * @hide
     */
    public void reverse() {
        // Only reverse when all the animators can be reverse. Otherwise, partially
        // reverse is confusing.
        if (!canReverse()) {
            Log.w(LOGTAG, "AnimatedVectorDrawable can't reverse()");
            return;
        }
        mAnimatedVectorState.mAnimatorSet.reverse();
    }

    /**
     * @hide
     */
    public boolean canReverse() {
        return mAnimatedVectorState.mAnimatorSet.canReverse();
    }

    private final Callback mCallback = new Callback() {
        @Override
        public void invalidateDrawable(Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            unscheduleSelf(what);
        }
    };
}
