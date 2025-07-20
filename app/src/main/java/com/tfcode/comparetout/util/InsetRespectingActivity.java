/*
 * Copyright (c) 2025. Tony Finnerty
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

package com.tfcode.comparetout.util;

import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashMap;
import java.util.Map;

public abstract class InsetRespectingActivity  extends AppCompatActivity {

    private final Map<Integer, EdgeInsets> insetTargets = new HashMap<>();


    /**
     * Register a view (by ID) to receive specified system bar insets.
     * Call this after setContentView, typically in onCreate.
     */
    protected void applyInsetsToView(@IdRes int viewId, EdgeInsets.Edge... edges) {
        insetTargets.put(viewId, new EdgeInsets(edges));
    }

    /**
     * Update the top inset target based on the visibility of the tab view.
     * If the tab view is visible, apply insets to it; otherwise, apply to the fallback view.
     */
    protected void updateTopInsetTarget(View tabView, View fallbackView) {
        if (tabView.getVisibility() == View.VISIBLE) {
            applyTopInset(tabView);
            clearInset(fallbackView);
        } else {
            clearInset(tabView);
            applyTopInset(fallbackView);
        }
    }

    private void applyTopInset(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), sysBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    private void clearInset(View view) {
        view.setPadding(view.getPaddingLeft(), 0, view.getPaddingRight(), view.getPaddingBottom());
        ViewCompat.setOnApplyWindowInsetsListener(view, null);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Required for edge-to-edge to work; disables automatic system window insets
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Window window = getWindow();
        View rootView = window.getDecorView().findViewById(android.R.id.content);

        for (Map.Entry<Integer, EdgeInsets> entry : insetTargets.entrySet()) {
            final View target = rootView.findViewById(entry.getKey());
            final EdgeInsets edgeSet = entry.getValue();

            if (target != null) {
                ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
                    Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                    int left = edgeSet.contains(EdgeInsets.Edge.LEFT) ? sysBars.left : v.getPaddingLeft();
                    int top = edgeSet.contains(EdgeInsets.Edge.TOP) ? sysBars.top : v.getPaddingTop();
                    int right = edgeSet.contains(EdgeInsets.Edge.RIGHT) ? sysBars.right : v.getPaddingRight();
                    int bottom = edgeSet.contains(EdgeInsets.Edge.BOTTOM) ? sysBars.bottom : v.getPaddingBottom();

                    v.setPadding(left, top, right, bottom);
                    return insets;
                });
                ViewCompat.requestApplyInsets(target);
            }
        }
    }


}
