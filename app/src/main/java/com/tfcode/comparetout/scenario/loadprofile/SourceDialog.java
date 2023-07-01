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

public class SourceDialog extends Dialog  {

    public static final int CUSTOM = 0;
    public static final int SLP = 1;
    public static final int HDF = 2;

    private TableLayout mTableLayout;
    private final SourceDialogListener mSourceDialogListener;

    public SourceDialog(@NonNull Context context, SourceDialogListener listener) {
        super(context);
        mSourceDialogListener = listener;
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

        // Add the source buttons
        {
            TableRow customRow = new TableRow(getContext());
            MaterialButton customButton = new MaterialButton(getContext());
            customButton.setText(R.string.reset_custom_lp);
            customButton.setOnClickListener(v -> {
                mSourceDialogListener.sourceSpecified(CUSTOM);
                dismiss();
            });
            customRow.addView(customButton);
            mTableLayout.addView(customRow);

            TableRow slpRow = new TableRow(getContext());
            MaterialButton slpButton = new MaterialButton(getContext());
            slpButton.setText(R.string.standard_lp);
            slpButton.setOnClickListener(v -> {
                mSourceDialogListener.sourceSpecified(SLP);
                dismiss();
            });
            slpRow.addView(slpButton);
            mTableLayout.addView(slpRow);

            TableRow hdfRow = new TableRow(getContext());
            MaterialButton hdfButton = new MaterialButton(getContext());
            hdfButton.setText(R.string.hdf_lp);
            hdfButton.setOnClickListener(v -> {
                mSourceDialogListener.sourceSpecified(HDF);
                dismiss();
            });
            hdfRow.addView(hdfButton);
            mTableLayout.addView(hdfRow);
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
