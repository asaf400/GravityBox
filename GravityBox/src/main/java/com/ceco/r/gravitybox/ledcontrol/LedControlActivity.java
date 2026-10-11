/*
 * Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.r.gravitybox.ledcontrol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ceco.r.gravitybox.R;
import com.ceco.r.gravitybox.GravityBoxListActivity;
import com.ceco.r.gravitybox.ledcontrol.LedListAdapter.ListItemActionHandler;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.ListView;
import android.widget.SearchView;

public class LedControlActivity extends GravityBoxListActivity implements ListItemActionHandler, OnItemClickListener {

    private static final int REQ_SETTINGS = 1;
    private static final String KEY_ACTIVE_ONLY = "showActiveOnly";
    private static final String KEY_SEARCH_QUERY = "searchQuery";

    private ListView mList;
    private AsyncTask<Void, Void, ArrayList<LedListItem>> mAsyncTask;
    private ProgressDialog mProgressDialog;
    private LedListItem mCurrentItem;
    private boolean mShowActiveOnly;
    private String mSearchQuery;
    private SearchView mSearchView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mShowActiveOnly = savedInstanceState.getBoolean(KEY_ACTIVE_ONLY, false);
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, null);
        }

        setContentView(R.layout.led_control_activity);

        mList = getListView();
        mList.setOnItemClickListener(this);

        setData();
    }

    @Override
    public void onStop() {
        cancelSetData();
        mSearchView.clearFocus();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.led_control_activity_menu, menu);

        final MenuItem search = menu.findItem(R.id.search);
        mSearchView = (SearchView) search.getActionView();

        if (mSearchQuery != null) {
            mSearchView.setQuery(mSearchQuery, false);
        }

        mSearchView.setOnQueryTextListener(new OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String text) {
                mSearchQuery = text;
                if (mList.getAdapter() != null) {
                    ((LedListAdapter)mList.getAdapter()).getFilter().filter(mSearchQuery);
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
        if (id == R.id.lc_activity_menu_show_all) {
            mShowActiveOnly = false;
            setData();
            return true;
        } else if (id == R.id.lc_activity_menu_show_active) {
            mShowActiveOnly = true;
            setData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setData() {
        mAsyncTask = new AsyncTask<Void,Void,ArrayList<LedListItem>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showProgressDialog();
            }

            @Override
            protected ArrayList<LedListItem> doInBackground(Void... arg0) {
                ArrayList<LedListItem> itemList = new ArrayList<>();

                PackageManager pm = LedControlActivity.this.getPackageManager();
                List<ApplicationInfo> packages = pm.getInstalledApplications(0);
                Collections.sort(packages, new ApplicationInfo.DisplayNameComparator(pm));
                for(ApplicationInfo ai : packages) {
                    if (isCancelled()) break;
                    if (ai.packageName.equals(LedControlActivity.this.getPackageName())) continue;
                    LedListItem item = new LedListItem(LedControlActivity.this, ai);
                    if (mShowActiveOnly && !item.isEnabled()) continue;
                    itemList.add(item);
                }

                return itemList;
            }

            @Override
            protected void onPostExecute(ArrayList<LedListItem> result) {
                dismissProgressDialog();
                LedListAdapter adapter = new LedListAdapter(LedControlActivity.this, result, 
                        LedControlActivity.this);
                if (mSearchQuery != null) {
                    adapter.getFilter().filter(mSearchQuery);
                }
                mList.setAdapter(adapter);
            }
        }.execute();
    }

    private void showProgressDialog() {
        mProgressDialog = new ProgressDialog(LedControlActivity.this);
        mProgressDialog.setMessage(getString(R.string.lc_please_wait));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }

    private void cancelSetData() {
        dismissProgressDialog();
        if (mAsyncTask != null && mAsyncTask.getStatus() == AsyncTask.Status.RUNNING) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = null;
    }

    private void openSettingsActivityFor(LedListItem item) {
        mCurrentItem = item;
        Intent intent = new Intent(this, LedSettingsActivity.class);
        intent.putExtra(LedSettingsActivity.EXTRA_PACKAGE_NAME, mCurrentItem.getAppInfo().packageName);
        intent.putExtra(LedSettingsActivity.EXTRA_APP_NAME, mCurrentItem.getAppName());
        this.startActivityForResult(intent, REQ_SETTINGS);
    }

    @Override
    public void onItemCheckedChanged(LedListItem item, boolean checked) {
        item.setEnabled(checked);
        mList.invalidateViews();
        if (checked) {
            openSettingsActivityFor(item);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        LedListItem item = (LedListItem) mList.getItemAtPosition(position);
        if (!item.isEnabled()) {
            item.setEnabled(true);
            mList.invalidateViews();
        }
        openSettingsActivityFor(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK && mCurrentItem != null) {
            if (mCurrentItem.getAppInfo().packageName.equals(
                    data.getStringExtra(LedSettingsActivity.EXTRA_PACKAGE_NAME))) {
                mCurrentItem.refreshLedSettings();
                mList.invalidateViews();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putBoolean(KEY_ACTIVE_ONLY, mShowActiveOnly);
        if (mSearchQuery != null) {
            bundle.putString(KEY_SEARCH_QUERY, mSearchQuery);
        }
        super.onSaveInstanceState(bundle);
    }
}
