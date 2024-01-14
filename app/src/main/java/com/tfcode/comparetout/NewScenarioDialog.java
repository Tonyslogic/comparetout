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

package com.tfcode.comparetout;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Window;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class NewScenarioDialog extends Dialog  {

    private TableLayout mTableLayout;
    private final NewScenarioDialogListener mNewScenarioDialogListener;
    private final List<String> mUsedNames;

    private EditText mNameValue = null;

    public NewScenarioDialog(@NonNull Context context, NewScenarioDialogListener listener, List<String> usedNames) {
        super(context);
        mNewScenarioDialogListener = listener;
        mUsedNames = usedNames;
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


        MaterialButton okButton = new MaterialButton(getContext());
        okButton.setEnabled(false);

        // Add the source buttons
        {
            TableRow nameRow = new TableRow(getContext());
            MaterialTextView namePrompt = new MaterialTextView(getContext());
            namePrompt.setText(R.string.UsageName);

            TableRow nameValueRow = new TableRow(getContext());
            mNameValue = new EditText(getContext());
            String newScenarioName = "";
            mNameValue.setText(newScenarioName);
            mNameValue.setLines(5);
            mNameValue.setSingleLine(false);
            mNameValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            mNameValue.setEnabled(true);
            mNameValue.setPadding(0,25, 0, 25);
            mNameValue.setBackgroundResource(R.drawable.row_border_red);
            mNameValue.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (mUsedNames.contains(s.toString()) || s.toString().isEmpty()) {
                        okButton.setEnabled(false);
                        mNameValue.setBackgroundResource(R.drawable.row_border_red);
                    }
                    else {
                        okButton.setEnabled(true);
                        mNameValue.setBackgroundResource(R.drawable.row_border);
                    }
                }
            });

            nameRow.addView(namePrompt);
            nameValueRow.addView(mNameValue);
            mTableLayout.addView(nameRow);
            mTableLayout.addView(nameValueRow);
        }

        // And the cancel button
        {
            TableRow tableRow = new TableRow(getContext());
            okButton.setText(R.string.ok);
            okButton.setOnClickListener(v -> {
                    mNewScenarioDialogListener.nameSpecified(
                            mNameValue.getText().toString());
                    dismiss();
            });
            tableRow.addView(okButton);

            TableRow cancelRow = new TableRow(getContext());
            MaterialButton cancelButton = new MaterialButton(getContext());
            cancelButton.setText(R.string.cancel);
            cancelButton.setOnClickListener(v -> dismiss());
            cancelRow.addView(cancelButton);
            mTableLayout.addView(tableRow);
            mTableLayout.addView(cancelRow);
        }
    }
}
