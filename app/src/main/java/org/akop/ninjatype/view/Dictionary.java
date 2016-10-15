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

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


class Dictionary
{
	private static final String LOG_TAG = Dictionary.class.getSimpleName();

	final INode mRoot = new INode();

	Dictionary()
	{
	}

	int scanFile(InputStream inputStream)
			throws IOException
	{
		int count = 0;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			while ((line = reader.readLine()) != null) {
				INode node = mRoot;
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
				catch (IOException e2) { }
			}
		}

		return count;
	}

	void dump(INode inode, String prefix)
	{
		if (inode == null) {
			return;
		}

		Set<Character> keys = inode.mNodes.keySet();
		for (char ch: keys) {
			Log.v(LOG_TAG, prefix + ch);
			dump(inode.mNodes.get(ch), prefix + " ");
		}
	}

	static class INode
	{
		final Map<Character, INode> mNodes = new HashMap<>();
		boolean mEnd;

		private INode appendINode(char ch)
		{
			ch = Character.toUpperCase(ch);

			INode in = mNodes.get(ch);
			if (in == null) {
				mNodes.put(ch, in = new INode());
			}

			return in;
		}

		INode next(char ch)
		{
			return mNodes.get(Character.toUpperCase(ch));
		}
	}
}
