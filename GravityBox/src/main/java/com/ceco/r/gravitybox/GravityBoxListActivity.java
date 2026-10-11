/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.r.gravitybox;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;

@SuppressLint("Registered")
public class GravityBoxListActivity extends ListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File file = new File(Utils.getFilesDir(this) + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(R.style.AppThemeDark);
        }
        super.onCreate(savedInstanceState);

        try {
            int labelRes = getPackageManager().getActivityInfo(getComponentName(), 0).labelRes;
            if (labelRes > 0) {
                setTitle(labelRes);
            }
        } catch (NameNotFoundException e) { /* ignore */ }

        ActivityContentInsets.install(this);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(GravityBoxContextWrapper.wrap(newBase));
    }
}
