/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.alphaess;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.tfcode.comparetout.R;

import java.util.List;

public class SelectSerialDialog extends Dialog  {

    private TableLayout mTableLayout;
    private final SelectSerialDialogListener mSelectSerialDialogListener;
    private final List<String> mSerials;

    public SelectSerialDialog(@NonNull Context context, List<String> serials, SelectSerialDialogListener listener) {
        super(context);
        mSelectSerialDialogListener = listener;
        mSerials = serials;
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

        // Add the serial buttons
        for (String serial : mSerials)
        {
            TableRow tableRow = new TableRow(getContext());
            MaterialButton serialButton = new MaterialButton(getContext());
            serialButton.setText(serial);
            serialButton.setOnClickListener(v -> {
                mSelectSerialDialogListener.serialSelected(serial);
                dismiss();
            });
            tableRow.addView(serialButton);
            mTableLayout.addView(tableRow);
        }

        // Add the freetext
        {
            TableRow tableRow2 = new TableRow(getContext());
            MaterialButton serialButton2 = new MaterialButton(getContext());

            TableRow tableRow = new TableRow(getContext());
            EditText serialButton = new EditText(getContext());
            serialButton.setText(R.string.not_listed);
            tableRow.addView(serialButton);
            mTableLayout.addView(tableRow);

            serialButton2.setText(R.string.enter_manually);
            serialButton2.setOnClickListener(v -> {
                mSelectSerialDialogListener.serialSelected(serialButton.getText().toString());
                dismiss();
            });
            tableRow2.addView(serialButton2);
            mTableLayout.addView(tableRow2);
        }

        // And the cancel button
        {
            TableRow tableRow = new TableRow(getContext());
            tableRow.setPadding(0,30, 0, 0);
            MaterialButton cancelButton = new MaterialButton(getContext());
            cancelButton.setText(R.string.cancel);
            cancelButton.setOnClickListener(v -> {
                mSelectSerialDialogListener.canceled();
                dismiss();
            });
            tableRow.addView(cancelButton);
            mTableLayout.addView(tableRow);
        }
    }
}
