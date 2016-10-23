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
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;


public class Dictionary
{
	private static final String LOG_TAG = Dictionary.class.getSimpleName();

	public interface OnStatusChangeListener
	{
		void onDictionaryLoading();
		void onDictionaryReady();
	}

	private static final INode STUB_NODE = new INode();

	INode mRoot;
	OnStatusChangeListener mOnStatusChangeListener;

	Dictionary()
	{
		mRoot = STUB_NODE;
	}

	void readFromResource(final Context context, final int resourceId)
	{
		final long started = SystemClock.uptimeMillis();
		final Handler handler = new Handler();

		if (mOnStatusChangeListener != null) {
			mOnStatusChangeListener.onDictionaryLoading();
		}

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Resources res = context.getResources();
				InputStream resStream = res.openRawResource(resourceId);

				INode newRoot = null;

				try {
					newRoot = readFromStream(resStream);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { resStream.close(); }
					catch (IOException e) { /* */ }
				}

				if (newRoot != null) {
					mRoot = newRoot;

					if (mOnStatusChangeListener != null) {
						handler.post(new Runnable()
						{
							@Override
							public void run()
							{
								mOnStatusChangeListener.onDictionaryReady();
							}
						});
					}
					Log.v(LOG_TAG, String.format("Loaded dictionary in %.02fs",
							(SystemClock.uptimeMillis() - started) / 1000f));
				}
			}
		}).start();
	}

	private static INode readFromStream(InputStream inputStream)
			throws IOException
	{
		BufferedReader reader = null;
		INode root = new INode();

		try {
			reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			while ((line = reader.readLine()) != null) {
				INode node = root;
				for (char ch: line.toCharArray()) {
					if (Character.isLetter(ch)) {
						node = node.appendINode(ch);
					}
				}
				node.mEnd = true;
			}
		} finally {
			if (reader != null) {
				try { reader.close(); }
				catch (IOException e2) { /* */ }
			}
		}

		return root;
	}

	static class INode
	{
		private Map<Character, INode> mNodes = null;
		private boolean mEnd;

		private INode appendINode(char ch)
		{
			if (mNodes == null) {
				mNodes = new HashMap<>();
			}

			ch = Character.toUpperCase(ch);

			INode in = mNodes.get(ch);
			if (in == null) {
				mNodes.put(ch, in = new INode());
			}

			return in;
		}

		INode next(char ch)
		{
			return (mNodes == null)
					? null : mNodes.get(Character.toUpperCase(ch));
		}

		boolean terminal()
		{
			return mEnd;
		}
	}
}
