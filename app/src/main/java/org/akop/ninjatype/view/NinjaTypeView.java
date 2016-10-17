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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class NinjaTypeView
		extends TextView
{
	private static final String LOG_TAG = NinjaTypeView.class.getSimpleName();

	private static final String[][] KEYS = new String[][] {
			{ "Q","W","E","R","T","Y","U","I","O","P" },
			{ "A","S","D","F","G","H","J","K","L" },
			{ "Z","X","C","V","B","N","M" },
	};

	private static final int OUTLINE_COLOR = Color.parseColor("#777777");
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
	private Drawable mKeyboardDrawable;
	private Drawable mSwipyDrawable;
	private Canvas mSwipyCanvas;

	private final Paint mLabelPaint;
	private final Paint mKeyOutlinePaint;
	private final Paint mSwipyPaint;

	private final Keyboard mKeyboard;
	private final Dictionary mDictionary;

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

		if (attrs != null) {
			Resources.Theme theme = context.getTheme();
			TypedArray a = theme.obtainStyledAttributes(attrs,
					R.styleable.NinjaTypeView, 0, 0);

			labelColor = a.getColor(R.styleable.NinjaTypeView_labelColor, labelColor);
			labelSize = a.getDimensionPixelSize(R.styleable.NinjaTypeView_labelSize, (int) labelSize);
			mKeyVpadding = a.getDimensionPixelSize(R.styleable.NinjaTypeView_keyVerticalPadding, (int) mKeyVpadding);
			swipeColor = a.getColor(R.styleable.NinjaTypeView_swipeColor, swipeColor);
			swipeThickness = a.getDimensionPixelSize(R.styleable.NinjaTypeView_swipeThickness, (int) swipeThickness);

			a.recycle();
		}

		mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mLabelPaint.setTextSize(labelSize);
		mLabelPaint.setColor(labelColor);

		mKeyOutlinePaint = new Paint();
		mKeyOutlinePaint.setColor(OUTLINE_COLOR);
		mKeyOutlinePaint.setStrokeWidth(OUTLINE_THICKNESS * dm.density);
		mKeyOutlinePaint.setStyle(Paint.Style.STROKE);

		mSwipyPaint = new Paint();
		mSwipyPaint.setColor(swipeColor);
		mSwipyPaint.setStrokeWidth(swipeThickness);
		mSwipyPaint.setStyle(Paint.Style.STROKE);

		mKeyboard = new Keyboard();
		mDictionary = new Dictionary();

		readDict();
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

	// FIXME: decrapify
	private void readDict()
	{
		Resources res = getResources();
		InputStream resStream = res.openRawResource(R.raw.roget);

		try {
			mDictionary.scanFile(resStream);
		} catch (IOException e) {
			// FIXME
			throw new RuntimeException(e);
		} finally {
			try { resStream.close(); }
			catch (IOException e) { /* */ }
		}
	}

	private void femputeKeyboardRect()
	{
		mLabelPaint.getTextBounds("Q" /* FIXME */, 0, 1, mTempRect);

		mKeyHeight = mKeyVpadding * 2 + mTempRect.height();
		mKeyboardRect.set(0, 0, mContentRect.width(), KEYS.length * mKeyHeight);
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

			KeyRow keyRow = mKeyboard.add(new KeyRow(keyRect.top, keyRect.bottom));
			for (String keyLabel: row) {
				float labelWidth = mLabelPaint.measureText(keyLabel);

				keyboardCanvas.drawRect(keyRect, mKeyOutlinePaint);
				keyboardCanvas.drawText(keyLabel, keyRect.centerX() - labelWidth / 2,
						keyRect.bottom - mKeyVpadding, mLabelPaint);

				keyRow.add(new Key(keyRect.left, keyRect.right,
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

	private class TouchHandler
			implements OnTouchListener
	{
		final PointF mPt;
		final PointF mPrevPt;
		Key mPrevKey;
		Dictionary.INode mNode;

		TouchHandler()
		{
			mPt = new PointF();
			mPrevPt = new PointF();
		}

		void initSwipe(float x, float y)
		{
			mPrevKey = null;
			mPt.set(x, y);
			mNode = mDictionary.mRoot;
		}

		void swipeChanged(float x, float y)
		{
			mPrevPt.set(mPt);
			mPt.set(x, y);

			Key key;
			if ((key = mKeyboard.lookup(mPt.x, mPt.y)) != null
					&& key != mPrevKey) {
				handleLookups(key);
				mPrevKey = key;
			}

			mSwipyCanvas.drawLine(mPrevPt.x, mPrevPt.y,
					mPt.x, mPt.y, mSwipyPaint);
		}

		void endSwipe()
		{
			mSwipyCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		}

		void handleLookups(Key key)
		{
			if (mNode == null) {
				return;
			}

			// FIXME
			Dictionary.INode next = mNode.next(key.mChar);
			if (next != null) {
				Log.v(LOG_TAG, "* " + key.mChar);
				mNode = next;
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

	private static abstract class KeyObj
	{
		final float mStart;
		final float mEnd;

		KeyObj(float start, float end)
		{
			mStart = start;
			mEnd = end;
		}
	}

	private static class Key
			extends KeyObj
	{
		char mChar;
		String mLabel;

		Key(float start, float end, char ch, String label)
		{
			super(start, end);

			mChar = ch;
			mLabel = label;
		}

		@Override
		public String toString()
		{
			return "[" + mLabel + "]";
		}
	}

	private static class KeyRow
			extends KeyObj
	{
		final List<Key> mKeys;

		KeyRow(float start, float end)
		{
			super(start, end);

			mKeys = new ArrayList<>();
		}

		void add(Key key)
		{
			mKeys.add(key);
		}
	}

	private static class Keyboard
	{
		final List<KeyRow> mRows;

		Keyboard()
		{
			mRows = new ArrayList<>();
		}

		void clear()
		{
			mRows.clear();
		}

		KeyRow add(KeyRow row)
		{
			mRows.add(row);
			return row;
		}

		Key lookup(float x, float y)
		{
			KeyRow row = find(mRows, 0, mRows.size(), y);
			if (row != null) {
				return find(row.mKeys, 0, row.mKeys.size(), x);
			}

			return null;
		}

		static <T extends KeyObj> T find(List<T> list, int start, int end, float v)
		{
			if (start > end) {
				return null;
			}

			int mid = (start + end) / 2;
			T obj = null;

			if (mid >= 0 && mid < list.size()) {
				obj = list.get(mid);
				if (v < obj.mStart) {
					return find(list, start, mid - 1, v);
				} else if (v >= obj.mEnd) {
					return find(list, mid + 1, end, v);
				}
			}

			return obj;
		}
	}
}
