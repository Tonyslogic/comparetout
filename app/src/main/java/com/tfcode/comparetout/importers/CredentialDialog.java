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

package com.tfcode.comparetout.importers;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Window;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.tfcode.comparetout.R;

public class CredentialDialog extends Dialog  {

    private TableLayout mTableLayout;
    private final CredentialDialogListener mCredentialDialogListener;

    private EditText appIDValue = null;
    private EditText appSecretValue = null;

    private int userPrompt = R.string.appId;
    private int passPrompt = R.string.appSecret;

    public CredentialDialog(@NonNull Context context, CredentialDialogListener listener) {
        super(context);
        mCredentialDialogListener = listener;
    }

    public void setPrompts (int userPrompt, int passPrompt) {
        this.userPrompt = userPrompt;
        this.passPrompt = passPrompt;
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
            TableRow appIdRow = new TableRow(getContext());
            MaterialTextView appIDPrompt = new MaterialTextView(getContext());
            appIDPrompt.setText(userPrompt);

            TableRow appIdValueRow = new TableRow(getContext());
            appIDValue = new EditText(getContext());
            String mAppId = "";
            appIDValue.setText(mAppId);
            appIDValue.setLines(5);
            appIDValue.setSingleLine(false);
            appIDValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            appIDValue.setEnabled(true);
            appIDValue.setPadding(0,25, 0, 25);
            appIDValue.setBackgroundResource(R.drawable.row_border);

            appIdRow.addView(appIDPrompt);
            appIdValueRow.addView(appIDValue);
            mTableLayout.addView(appIdRow);
            mTableLayout.addView(appIdValueRow);

            TableRow appSecretRow = new TableRow(getContext());
            MaterialTextView appSecretPrompt = new MaterialTextView(getContext());
            appSecretPrompt.setText(passPrompt);

            TableRow appSecretValueRow = new TableRow(getContext());
            appSecretValue = new EditText(getContext());
            String mAppSecret = "";
            appSecretValue.setText(mAppSecret);
            appSecretValue.setLines(5);
            appSecretValue.setSingleLine(false);
            appSecretValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            appSecretValue.setEnabled(true);
            appSecretValue.setPadding(0,25, 0, 25);
            appSecretValue.setBackgroundResource(R.drawable.row_border);
            appSecretRow.addView(appSecretPrompt);
            appSecretValueRow.addView(appSecretValue);
            mTableLayout.addView(appSecretRow);
            mTableLayout.addView(appSecretValueRow);
        }

        // And the cancel button
        {
            TableRow tableRow = new TableRow(getContext());
            MaterialButton okButton = new MaterialButton(getContext());
            okButton.setText(R.string.ok);
            okButton.setOnClickListener(v -> {
                    mCredentialDialogListener.credentialSpecified(
                            appIDValue.getText().toString(),
                            appSecretValue.getText().toString());
                    dismiss();
            });
            tableRow.addView(okButton);

            TableRow cancelRow = new TableRow(getContext());
            MaterialButton cancelButton = new MaterialButton(getContext());
            cancelButton.setText(R.string.cancel);
            cancelButton.setOnClickListener(v -> {
                mCredentialDialogListener.canceled();
                dismiss();
            });
            cancelRow.addView(cancelButton);
            mTableLayout.addView(tableRow);
            mTableLayout.addView(cancelRow);
        }
    }
}
