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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import java.util.HashMap;
import java.util.Map;

public abstract class InsetRespectingFragment extends Fragment {
    private final Map<Integer, EdgeInsets> insetTargets = new HashMap<>();

    /**
     * Call this in onCreateView or onViewCreated to register a view
     * that should receive specific edge insets.
     */
    protected void applyInsetsToView(@IdRes int viewId, EdgeInsets.Edge... edges) {
        insetTargets.put(viewId, new EdgeInsets(edges));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply insets to all registered views
        for (Map.Entry<Integer, EdgeInsets> entry : insetTargets.entrySet()) {
            final View target = view.findViewById(entry.getKey());
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
            }
        }
    }
}
