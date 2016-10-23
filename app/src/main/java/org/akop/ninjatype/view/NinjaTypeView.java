// Copyright 2016 Akop Karapetyan
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.akop.ninjatype.view;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.akop.ninjatype.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class NinjaTypeView
		extends TextView
{
	private static final String LOG_TAG = NinjaTypeView.class.getSimpleName();

	public interface OnWordSwipedListener
	{
		void onWordSwiped(List<String> candidates);
		void onNoMatches();
	}

	private static final int MAX_CANDIDATES = 10;

	private static final String[][] KEYS = new String[][] {
			{ "Q","W","E","R","T","Y","U","I","O","P" },
			{ "A","S","D","F","G","H","J","K","L" },
			{ "Z","X","C","V","B","N","M" },
	};

	private static final int OUTLINE_COLOR = Color.parseColor("#00000000");
	private static final float OUTLINE_THICKNESS = 0;
	private static final int LABEL_COLOR = Color.parseColor("#000000");
	private static final float LABEL_SIZE = 20;
	private static final float KEY_VPADDING = 12;
	private static final int SWIPE_COLOR = Color.parseColor("#ff0000");
	private static final float SWIPE_THICKNESS = 4;

	private final RectF mContentRect;
	private final RectF mKeyboardRect;
	private final Rect mTempRect;
	private final Rect mKeyboardBounds;
	private float mKeyVpadding;
	private float mKeyHeight;
	private int mLongestKeySpan;
	private Drawable mKeyboardDrawable;
	private Drawable mSwipyDrawable;
	private Canvas mSwipyCanvas;

	private final Paint mLabelPaint;
	private final Paint mKeyOutlinePaint;
	private final Paint mSwipyPaint;

	private final Keyboard mKeyboard;
	private final Dictionary mDictionary;

	private static final Comparator<Match> SCORE_COMPARATOR
			= new Comparator<Match>()
	{
		@Override
		public int compare(Match o1, Match o2)
		{
			return Float.compare(o2.score(), o1.score());
		}
	};

	private OnWordSwipedListener mOnWordSwipedListener;

	public NinjaTypeView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		mContentRect = new RectF();
		mKeyboardRect = new RectF();
		mTempRect = new Rect();
		mKeyboardBounds = new Rect();

		setOnTouchListener(new TouchHandler());

		Resources r = context.getResources();
		DisplayMetrics dm = r.getDisplayMetrics();

		int labelColor = LABEL_COLOR;
		float labelSize = LABEL_SIZE * dm.scaledDensity;
		mKeyVpadding = KEY_VPADDING * dm.density;
		int swipeColor = SWIPE_COLOR;
		float swipeThickness = SWIPE_THICKNESS * dm.density;
		int dictionaryResId = R.raw.default_dictionary;
		int outlineColor = OUTLINE_COLOR;
		float outlineThickness = OUTLINE_THICKNESS * dm.density;

		if (attrs != null) {
			Resources.Theme theme = context.getTheme();
			TypedArray a = theme.obtainStyledAttributes(attrs,
					R.styleable.NinjaTypeView, 0, 0);

			labelColor = a.getColor(R.styleable.NinjaTypeView_labelColor, labelColor);
			labelSize = a.getDimensionPixelSize(R.styleable.NinjaTypeView_labelSize, (int) labelSize);
			mKeyVpadding = a.getDimensionPixelSize(R.styleable.NinjaTypeView_keyVerticalPadding, (int) mKeyVpadding);
			swipeColor = a.getColor(R.styleable.NinjaTypeView_swipeColor, swipeColor);
			swipeThickness = a.getDimensionPixelSize(R.styleable.NinjaTypeView_swipeThickness, (int) swipeThickness);
			dictionaryResId = a.getResourceId(R.styleable.NinjaTypeView_dictionary, dictionaryResId);
			outlineColor = a.getColor(R.styleable.NinjaTypeView_outlineColor, outlineColor);
			outlineThickness = a.getDimensionPixelSize(R.styleable.NinjaTypeView_outlineThickness, (int) outlineThickness);

			a.recycle();
		}

		mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mLabelPaint.setTextSize(labelSize);
		mLabelPaint.setColor(labelColor);

		mKeyOutlinePaint = new Paint();
		mKeyOutlinePaint.setColor(outlineColor);
		mKeyOutlinePaint.setStrokeWidth(outlineThickness);
		mKeyOutlinePaint.setStyle(Paint.Style.STROKE);

		mSwipyPaint = new Paint();
		mSwipyPaint.setColor(swipeColor);
		mSwipyPaint.setStrokeWidth(swipeThickness);
		mSwipyPaint.setStyle(Paint.Style.STROKE);

		mKeyboard = new Keyboard();
		mDictionary = new Dictionary();

		if (dictionaryResId != 0) {
			mDictionary.readFromResource(getContext(), dictionaryResId);
		}

		initKeyboard();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int desiredHeight = (int) keyHeight() * KEYS.length;

		int height;
		switch (MeasureSpec.getMode(heightMeasureSpec)) {
		case MeasureSpec.EXACTLY:
			height = MeasureSpec.getSize(heightMeasureSpec);
			break;
		case MeasureSpec.AT_MOST:
			height = Math.min(desiredHeight,
					MeasureSpec.getSize(heightMeasureSpec));
			break;
		case MeasureSpec.UNSPECIFIED:
		default:
			height = desiredHeight;
			break;
		}

		setMeasuredDimension(widthMeasureSpec, height);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);

		mContentRect.set(getPaddingLeft(), getPaddingTop(),
				w - getPaddingRight(), h - getPaddingBottom());

		femputeKeyboardRect();

		Resources res = getResources();
		if (res != null) {
			prepareBitmaps(res);
		}
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

		canvas.save();
		canvas.clipRect(mContentRect);

		if (mKeyboardDrawable != null) {
			mKeyboardDrawable.draw(canvas);
		}
		if (mSwipyDrawable != null) {
			mSwipyDrawable.draw(canvas);
		}

		canvas.restore();
	}

	public void setDictionaryStatusListener(Dictionary.OnStatusChangeListener l)
	{
		mDictionary.mOnStatusChangeListener = l;
	}

	public void setOnWordSwipedListener(OnWordSwipedListener l)
	{
		mOnWordSwipedListener = l;
	}

	private float keyHeight()
	{
		mLabelPaint.getTextBounds("Q" /* FIXME */, 0, 1, mTempRect);
		return mKeyVpadding * 2 + mTempRect.height();
	}

	private void femputeKeyboardRect()
	{
		mKeyHeight = keyHeight();
		mKeyboardRect.set(0, 0, mContentRect.width(), KEYS.length * mKeyHeight);
	}

	private void initKeyboard()
	{
		// This isn't entirely accurate - just assume that the longest span is
		// a straight horizontal one
		mLongestKeySpan = KEYS[0].length;
		for (int i = 1; i < KEYS.length; i++) {
			if (KEYS[i].length > mLongestKeySpan) {
				mLongestKeySpan = KEYS[i].length;
			}
		}
	}

	private void prepareBitmaps(Resources res)
	{
		int bmpWidth = (int) mKeyboardRect.width();
		int bmpHeight = (int) mKeyboardRect.height();

		// Centered horizontally and aligned to the bottom
		int keyboardLeft = (int) (mContentRect.centerX() - mKeyboardRect.width() / 2);
		int keyboardTop = (int) (mContentRect.bottom - mKeyboardRect.height());

		int mostKeysPerRow = KEYS[0].length;
		for (int i = 1; i < KEYS.length; i++) {
			if (KEYS[i].length > mostKeysPerRow) {
				mostKeysPerRow = KEYS[i].length;
			}
		}

		Bitmap keyboardBmp = Bitmap.createBitmap(bmpWidth, bmpHeight,
				Bitmap.Config.ARGB_8888);
		Canvas keyboardCanvas = new Canvas(keyboardBmp);

		Bitmap swipyBmp = Bitmap.createBitmap(bmpWidth, bmpHeight,
				Bitmap.Config.ARGB_8888);
		mSwipyCanvas = new Canvas(swipyBmp);

		float minKeyWidth = mKeyboardRect.width() / mostKeysPerRow;
		RectF keyRect = new RectF(0, 0, minKeyWidth, mKeyHeight);

		mKeyboard.clear();

		for (String[] row: KEYS) {
			float rowWidth = minKeyWidth * row.length;
			keyRect.offsetTo(mKeyboardRect.centerX() - rowWidth / 2, keyRect.top);

			Keyboard.Row keyRow = mKeyboard.add(new Keyboard.Row(keyRect.top, keyRect.bottom));
			for (String keyLabel: row) {
				float labelWidth = mLabelPaint.measureText(keyLabel);

				keyboardCanvas.drawRect(keyRect, mKeyOutlinePaint);
				keyboardCanvas.drawText(keyLabel, keyRect.centerX() - labelWidth / 2,
						keyRect.bottom - mKeyVpadding, mLabelPaint);

				keyRow.add(new Keyboard.Key(keyRect.left, keyRect.right,
						keyLabel.charAt(0) /* FIXME */, keyLabel));

				keyRect.offset(minKeyWidth, 0);
			}
			keyRect.offset(0, mKeyHeight);
		}

		// Create drawables
		mKeyboardBounds.set(keyboardLeft, keyboardTop,
				keyboardLeft + bmpWidth, keyboardTop + bmpHeight);

		mKeyboardDrawable = new BitmapDrawable(res, keyboardBmp);
		mKeyboardDrawable.setBounds(mKeyboardBounds);

		mSwipyDrawable = new BitmapDrawable(res, swipyBmp);
		mSwipyDrawable.setBounds(mKeyboardBounds);
	}

	private static class Match
	{
		final Dictionary.INode mNode;
		final String mWord;
		final int mKeyIndex;
		final float mStartingScore;
		float mDistance;
		final int mHits;

		Match(Dictionary.INode node, String str, int keyIndex,
				int hits, float startingScore)
		{
			mHits = hits;
			mNode = node;
			mWord = str;
			mKeyIndex = keyIndex;
			mStartingScore = startingScore;
			mDistance = Float.MAX_VALUE;
		}

		float score()
		{
			return mStartingScore + (100f / (mDistance + 50f));
		}

		@Override
		public String toString()
		{
			return String.format(Locale.getDefault(),
					"%s (%.02f)", mWord, score());
		}
	}

	private class TouchHandler
			implements OnTouchListener
	{
		final PointF mPt;
		final PointF mPrevPt;
		final List<Match> mMatches;
		final Set<String> mCandidates;
		int mKeyCounter;
		Keyboard.Key mPrevKey;

		TouchHandler()
		{
			mPt = new PointF();
			mPrevPt = new PointF();
			mMatches = new ArrayList<>();
			mCandidates = new HashSet<>();
		}

		void initSwipe(float x, float y)
		{
			mKeyCounter = 0;
			mPrevKey = null;
			mPt.set(x, y);
			mMatches.clear();
			mCandidates.clear();
		}

		void swipeChanged(float x, float y)
		{
			mPrevPt.set(mPt);
			mPt.set(x, y);

			Keyboard.Key key;
			if ((key = mKeyboard.keyAt(x, y)) != null) {
				if (key != mPrevKey) {
					keyChanged(key, ++mKeyCounter);
					mPrevKey = key;
				} else {
					updateDistances(key.distanceFromCenter(x, y), mKeyCounter);
				}
			}

			mSwipyCanvas.drawLine(mPrevPt.x, mPrevPt.y, x, y, mSwipyPaint);
		}

		void endSwipe()
		{
			mSwipyCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

			if (mOnWordSwipedListener != null) {
				Collections.sort(mMatches, SCORE_COMPARATOR);
				List<String> candidates = new ArrayList<>();
				for (Match m: mMatches) {
					if (m.mNode.terminal()) {
						Log.v(LOG_TAG, m + "");
						candidates.add(m.mWord);
					}
				}

				if (!candidates.isEmpty()) {
					mOnWordSwipedListener.onWordSwiped(candidates.subList(0,
							Math.min(candidates.size(), MAX_CANDIDATES)));
				} else {
					mOnWordSwipedListener.onNoMatches();
				}
			}

			Log.v(LOG_TAG, mMatches.size() + " candidates");

			mCandidates.clear();
			mMatches.clear();
		}

		void updateDistances(float distance, int keyIndex)
		{
			for (Match m: mMatches) {
				if (m.mKeyIndex == keyIndex) {
					if (m.mDistance > distance) {
						m.mDistance = distance;
					} else {
						break; // FIXME?
					}
				}
			}
		}

		void keyChanged(Keyboard.Key key, int keyIndex)
		{
			if (mMatches.isEmpty()) {
				addCandidates(null, key.mChar, keyIndex);
			} else {
				for (int i = mMatches.size() - 1; i >= 0; i--) {
					Match m = mMatches.get(i);
					if (keyIndex - m.mKeyIndex > mLongestKeySpan) {
						// Don't go too far back
						break;
					}
					addCandidates(m, key.mChar, keyIndex);
				}
			}
		}

		void addCandidates(Match match, char ch, int keyIndex)
		{
			Dictionary.INode current;
			String prefix;
			final int hits;
			final float score;

			if (match == null) {
				current = mDictionary.mRoot;
				prefix = "";
				hits = 1;
				score = 0;
			} else {
				current = match.mNode;
				prefix = match.mWord;
				hits = match.mHits + 1;
				score = match.score();
			}

			Dictionary.INode next;
			while ((next = current.next(ch)) != null) {
				String word = prefix + ch;
				if (!mCandidates.contains(word)) {
					mMatches.add(new Match(next, word, keyIndex, hits, score));
					mCandidates.add(word);
				}

				current = next;
				prefix = word;
			}
		}

		@Override
		public boolean onTouch(View v, MotionEvent event)
		{
			switch (event.getActionMasked()) {
			case MotionEvent.ACTION_UP:
				endSwipe();
				break;
			case MotionEvent.ACTION_DOWN:
				initSwipe(event.getX() - mKeyboardBounds.left,
						event.getY() - mKeyboardBounds.top);
				// fallthrough
			case MotionEvent.ACTION_MOVE:
				swipeChanged(event.getX() - mKeyboardBounds.left,
						event.getY() - mKeyboardBounds.top);
				break;
			}

			invalidate();

			return true;
		}
	}
}
