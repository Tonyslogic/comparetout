/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.scenario.loadprofile;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.tfcode.comparetout.R;

import java.util.ArrayList;
import java.util.Arrays;

public class StandardLoadProfileDialog extends Dialog  {

    private TableLayout mTableLayout;
    private final StandardLoadProfileDialogListener mSLPDialogListener;

    public StandardLoadProfileDialog(@NonNull Context context, StandardLoadProfileDialogListener listener) {
        super(context);
        mSLPDialogListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_download);
        mTableLayout = findViewById(R.id.download_dialog_table);
        updateView();
    }

    private void updateView(){
        mTableLayout.removeAllViews();
        mTableLayout.setShrinkAllColumns(false);
        mTableLayout.setStretchAllColumns(true);
        mTableLayout.setColumnShrinkable(0, true);
        mTableLayout.setColumnStretchable(0, false);

        ArrayList<String> slpNames = new ArrayList<>(Arrays.asList(StandardLoadProfiles.standardLoadProfiles));

        // Add the SLP buttons
        for (String name: slpNames)
        {
            TableRow customRow = new TableRow(getContext());
            MaterialButton customButton = new MaterialButton(getContext());
            customButton.setText(name);
            customButton.setOnClickListener(v -> {
                mSLPDialogListener.slpSpecified(name);
                dismiss();
            });
            customRow.addView(customButton);
            mTableLayout.addView(customRow);
        }

        // And the cancel button
        {
            TableRow tableRow = new TableRow(getContext());
            MaterialButton button = new MaterialButton(getContext());
            button.setText(R.string.cancel);
            button.setOnClickListener(v -> dismiss());
            tableRow.addView(button);
            mTableLayout.addView(tableRow);
        }
    }
}
