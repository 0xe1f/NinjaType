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

import java.util.ArrayList;
import java.util.List;


class Keyboard
{
	private final List<Row> mRows;

	Keyboard()
	{
		mRows = new ArrayList<>();
	}

	void clear()
	{
		mRows.clear();
	}

	Row add(Row row)
	{
		mRows.add(row);
		return row;
	}

	Key keyAt(float x, float y)
	{
		Row row = find(mRows, 0, mRows.size() - 1, y);
		if (row != null) {
			return find(row.mKeys, 0, row.mKeys.size() - 1, x);
		}

		return null;
	}

	private static <T extends KeyObj> T find(List<T> list,
			int start, int end, float v)
	{
		if (start > end) {
			return null;
		}

		int mid = (start + end) / 2;
		T obj = list.get(mid);
		if (v < obj.mStart) {
			return find(list, start, mid - 1, v);
		} else if (v >= obj.mEnd) {
			return find(list, mid + 1, end, v);
		}

		return obj;
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

	static class Key
			extends KeyObj
	{
		private Row mRow;
		char mChar;
		String mLabel;

		Key(float start, float end, char ch, String label)
		{
			super(start, end);

			mChar = ch;
			mLabel = label;
		}

		float distanceFromCenter(float x, float y)
		{
			float xc = (mStart + mEnd) * .5f;
			float yc = (mRow.mStart + mRow.mEnd) * .5f;

			return (float) Math.hypot(x - xc, y - yc);
		}

		@Override
		public String toString()
		{
			return "[" + mLabel + "]";
		}
	}

	static class Row
			extends KeyObj
	{
		final List<Key> mKeys;

		Row(float start, float end)
		{
			super(start, end);

			mKeys = new ArrayList<>();
		}

		void add(Key key)
		{
			key.mRow = this;
			mKeys.add(key);
		}
	}
}
