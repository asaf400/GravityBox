/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.r.gravitybox.tuner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import com.ceco.r.gravitybox.GravityBoxListActivity;
import com.ceco.r.gravitybox.GravityBoxResultReceiver;
import com.ceco.r.gravitybox.R;
import com.ceco.r.gravitybox.SettingsManager;
import com.ceco.r.gravitybox.managers.SysUiTunerManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.Nullable;

public class TunerCategoryActivity extends GravityBoxListActivity implements
        GravityBoxResultReceiver.Receiver, AdapterView.OnItemClickListener {

    private static final int REQ_SETTINGS = 1;
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_ACTIVE_ONLY = "showActiveOnly";
    private static final String KEY_SEARCH_QUERY = "searchQuery";

    private SysUiTunerManager.Category mCategory;
    private ListView mList;
    private TuneableListItem mCurrentItem;
    private boolean mShowActiveOnly;
    private String mSearchQuery;
    private SearchView mSearchView;
    private List<TuneableItem> mItems;
    private Handler mHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCategory = SysUiTunerManager.Category.valueOf(
                    savedInstanceState.getString(KEY_CATEGORY, "FRAMEWORK"));
            mShowActiveOnly = savedInstanceState.getBoolean(KEY_ACTIVE_ONLY, false);
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, null);
        } else if (getIntent() != null && getIntent().hasExtra(SysUiTunerManager.EXTRA_TUNER_CATEGORY)) {
            mCategory = SysUiTunerManager.Category.valueOf(getIntent().getStringExtra(SysUiTunerManager.EXTRA_TUNER_CATEGORY));
        } else {
            finish();
            return;
        }
        setTitle(getTitle() + " - " + getCategoryName());

        setContentView(R.layout.tuner_category_activity);
        mList = getListView();
        mList.setOnItemClickListener(this);

        mHandler = new Handler(Looper.getMainLooper());
        mHandler.postDelayed(mNoResponseRunnable, 3000);
        GravityBoxResultReceiver receiver = new GravityBoxResultReceiver(mHandler);
        receiver.setReceiver(this);
        Intent intent = new Intent(SysUiTunerManager.ACTION_GET_TUNEABLES);
        intent.putExtra(SysUiTunerManager.EXTRA_TUNER_CATEGORY, mCategory.toString());
        intent.putExtra("receiver", receiver);
        sendBroadcast(intent);
    }

    private Runnable mNoResponseRunnable = () ->
            Toast.makeText(TunerCategoryActivity.this,
            R.string.tuner_manager_not_responding,
            Toast.LENGTH_LONG).show();

    @Override
    protected void onStop() {
        mHandler.removeCallbacks(mNoResponseRunnable);
        super.onStop();
    }

    private String getCategoryName() {
        switch (mCategory) {
            default:
            case FRAMEWORK: return getString(R.string.pref_tuner_framework_title);
            case SYSTEMUI: return getString(R.string.pref_tuner_systemui_title);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tuner_category_activity_menu, menu);

        final MenuItem search = menu.findItem(R.id.search);
        mSearchView = (SearchView) search.getActionView();

        if (mSearchQuery != null) {
            mSearchView.setQuery(mSearchQuery, false);
        }

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String text) {
                mSearchQuery = text;
                if (mList.getAdapter() != null) {
                    ((TuneableListAdapter)mList.getAdapter()).getFilter().filter(mSearchQuery);
                }
                return true;
            }
            @Override
            public boolean onQueryTextSubmit(String text) {
                mSearchView.clearFocus();
                return true;
            }
        });

        int closeBtnResId = getResources().getIdentifier(
                "android:id/search_close_btn", null, null);
        if (closeBtnResId != 0) {
            View closeBtn = mSearchView.findViewById(closeBtnResId);
            if (closeBtn != null) {
                closeBtn.setOnClickListener(v -> search.collapseActionView());
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.menu_show_all) {
            mShowActiveOnly = false;
            setData();
            return true;
        } else if (id == R.id.menu_show_active) {
            mShowActiveOnly = true;
            setData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        mHandler.removeCallbacks(mNoResponseRunnable);
        if (!isDestroyed() && resultData != null) {
            resultData.setClassLoader(getClassLoader());
            mItems = resultData.getParcelableArrayList(SysUiTunerManager.EXTRA_TUNEABLES);
            mItems.sort(Comparator.comparing(TuneableItem::getKey));
            setData();
        }
    }

    private void setData() {
        ArrayList<TuneableListItem> listItems = new ArrayList<>();
        SharedPreferences prefs = SettingsManager.getInstance(this).getTunerPrefs();
        for(TuneableItem item : mItems) {
            item.loadUserSettings(prefs);
            if (mShowActiveOnly && !item.isOverridden())
                continue;
            TuneableListItem listItem = new TuneableListItem(this, item);
            listItems.add(listItem);
        }

        TuneableListAdapter adapter = new TuneableListAdapter(this, listItems);
        if (mSearchQuery != null) {
            adapter.getFilter().filter(mSearchQuery);
        }
        mList.setAdapter(adapter);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mCurrentItem = (TuneableListItem) mList.getItemAtPosition(position);
        Intent intent = new Intent(this, TunerDetailActivity.class);
        intent.putExtra(TunerDetailActivity.EXTRA_TUNEABLE_ITEM, mCurrentItem.getItem());
        startActivityForResult(intent, REQ_SETTINGS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK && mCurrentItem != null) {
            mCurrentItem.getItem().loadUserSettings(
                    SettingsManager.getInstance(this).getTunerPrefs());
            mList.invalidateViews();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putString(KEY_CATEGORY, mCategory.toString());
        bundle.putBoolean(KEY_ACTIVE_ONLY, mShowActiveOnly);
        if (mSearchQuery != null) {
            bundle.putString(KEY_SEARCH_QUERY, mSearchQuery);
        }
        super.onSaveInstanceState(bundle);
    }
}
