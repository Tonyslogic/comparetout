/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.ui2;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.TOUTCApplication;

/**
 * Bridges the legacy importer notifications to the UI2 surface.
 *
 * Reactive notifications (the "fetch done" notifications each importer worker
 * posts) launch an activity with context — which system/source just imported.
 * The legacy target is one of the per-source Import* activities. When the user
 * has switched to UI2 ({@code use_ui2 == "true"}), the same tap should instead
 * land on the UI2 dashboard with that source pre-selected.
 *
 * The worker reads the flag once on its background thread (the DataStore read
 * blocks) and hands the cached value here so the notification-building path —
 * which runs on the main looper — never blocks on I/O.
 */
public final class UI2NotificationLaunch {

    /** Intent extras read by {@link UI2MainActivity} to pre-select a source. */
    public static final String EXTRA_DS_SYSSN    = "ui2.select.ds.sysSn";
    public static final String EXTRA_DS_IMPORTER = "ui2.select.ds.importer";

    private static final String USE_UI2_KEY = "use_ui2";

    private UI2NotificationLaunch() {}

    /**
     * Whether the user has switched to the UI2 experience. Blocks on the
     * DataStore — call from a background thread (e.g. the worker's doWork),
     * not the main looper.
     */
    public static boolean isUI2Enabled(Context context) {
        TOUTCApplication app = (TOUTCApplication) context.getApplicationContext();
        return "true".equals(app.getStringValueFromDataStore(USE_UI2_KEY));
    }

    /**
     * Build the content PendingIntent for an importer's notification.
     *
     * @param ui2Enabled  the cached {@link #isUI2Enabled} result
     * @param importer    which source this notification is about
     * @param sysSn       the system SN / MPRN that just imported (may be null)
     * @param legacyActivity the Import* activity to use when UI2 is off
     */
    public static PendingIntent contentIntent(
            Context context,
            boolean ui2Enabled,
            ComparisonUIViewModel.Importer importer,
            String sysSn,
            Class<?> legacyActivity) {

        if (ui2Enabled) {
            Intent intent = new Intent(context, UI2MainActivity.class);
            // CLEAR_TOP so a running UI2 task surfaces (and onNewIntent fires)
            // rather than stacking a second dashboard.
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (sysSn != null) intent.putExtra(EXTRA_DS_SYSSN, sysSn);
            if (importer != null) intent.putExtra(EXTRA_DS_IMPORTER, importer.name());
            return PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        Intent legacy = new Intent(context, legacyActivity);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(legacy);
        return stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
