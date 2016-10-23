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

package org.akop.ninjatype.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.akop.ninjatype.R;
import org.akop.ninjatype.view.Dictionary;
import org.akop.ninjatype.view.NinjaTypeView;

import java.util.ArrayList;
import java.util.List;


public class MainActivity
		extends AppCompatActivity
		implements NinjaTypeView.OnWordSwipedListener,
		Dictionary.OnStatusChangeListener
{
	private static final String LOG_TAG = MainActivity.class.getSimpleName();

	private final Adapter mAdapter = new Adapter();

	private TextView mStatus;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		NinjaTypeView ntv = (NinjaTypeView) findViewById(R.id.ninja_type);
		ListView lv = (ListView) findViewById(R.id.list_view);
		mStatus = (TextView) findViewById(R.id.status);

		ntv.setOnWordSwipedListener(this);
		ntv.setDictionaryStatusListener(this);

		lv.setAdapter(mAdapter);
	}

	@Override
	public void onWordSwiped(List<String> candidates)
	{
		mAdapter.reset(candidates);
	}

	@Override
	public void onNoMatches()
	{
		mAdapter.clear();
	}

	@Override
	public void onDictionaryLoading()
	{
	}

	@Override
	public void onDictionaryReady()
	{
		if (mStatus != null) {
			mStatus.setText(R.string.ready);
		}
	}

	private class Adapter
			extends BaseAdapter
	{
		final List<String> mStrings = new ArrayList<>();

		void reset(List<String> strings)
		{
			mStrings.clear();
			mStrings.addAll(strings);
			notifyDataSetChanged();
		}

		void clear()
		{
			mStrings.clear();
			notifyDataSetChanged();
		}

		@Override
		public int getCount()
		{
			return mStrings.size();
		}

		@Override
		public String getItem(int position)
		{
			return mStrings.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			if (convertView == null) {
				LayoutInflater li = getLayoutInflater();
				convertView = li.inflate(R.layout.template_option, parent, false);
			}

			TextView tv = (TextView) convertView;
			tv.setText(mStrings.get(position));

			return convertView;
		}
	}
}
