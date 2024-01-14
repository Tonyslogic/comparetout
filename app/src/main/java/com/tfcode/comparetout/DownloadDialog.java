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

package com.tfcode.comparetout;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

public class DownloadDialog extends Dialog  {

    private TableLayout mTableLayout;
    private final DownloadDialogListener mDownloadDialogListener;
    private String url;
    private boolean clobber = false;

    public DownloadDialog(@NonNull Context context, String url, DownloadDialogListener listener) {
        super(context);
        mDownloadDialogListener = listener;
        this.url = url;
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

        // Title row
        {
            TableRow titleRow = new TableRow(getContext());
            TextView disclaimer = new TextView(getContext());
            disclaimer.setSingleLine(false);
            disclaimer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            disclaimer.setText(R.string.download_disclaimer);
            titleRow.setGravity(Gravity.CENTER_HORIZONTAL);
            disclaimer.setGravity(Gravity.CENTER_HORIZONTAL);
            titleRow.addView(disclaimer);
            mTableLayout.addView(titleRow);
        }

        // URL edit
        {
            TableRow inputRow = new TableRow(getContext());
            EditText input = new EditText(getContext());
            input.setText(url);
            input.setSingleLine(false);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            input.setEnabled(true);
            input.setPadding(0,25, 0, 25);
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    url = s.toString();
                }
            });
            inputRow.addView(input);
            mTableLayout.addView(inputRow);
        }

        // Clobber checkbox
        {
            TableRow clobberRow = new TableRow(getContext());
            MaterialCheckBox clobberBox = new MaterialCheckBox(getContext());
            clobberBox.setEnabled(true);
            clobberBox.setChecked(clobber);
            clobberBox.setText(R.string.ReplaceExisting);
            clobberBox.setOnClickListener(v -> clobber = clobberBox.isChecked());
            clobberRow.addView(clobberBox);
            mTableLayout.addView(clobberRow);
        }

        // And the ok and cancel buttons
        {
            TableRow tableRowOK = new TableRow(getContext());
            MaterialButton okb = new MaterialButton(getContext());
            okb.setText(R.string.download);
            okb.setOnClickListener(v -> {
                mDownloadDialogListener.urlSpecified(url, clobber);
                dismiss();
            });
            tableRowOK.addView(okb);
            mTableLayout.addView(tableRowOK);
            TableRow tableRow = new TableRow(getContext());
            MaterialButton button = new MaterialButton(getContext());
            button.setText(R.string.cancel);
            button.setOnClickListener(v -> dismiss());
            tableRow.addView(button);
            mTableLayout.addView(tableRow);
        }
    }
}
