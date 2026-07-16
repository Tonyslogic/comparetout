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

package com.tfcode.comparetout.ui2

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.tfcode.comparetout.MainActivity.CHANNEL_ID
import com.tfcode.comparetout.R
import com.tfcode.comparetout.SimulatorLauncher
import com.tfcode.comparetout.dynamic.DynamicRateSources
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.priceplan.PricePlan
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException

/**
 * Materialises a dynamic tariff in the background: fetch the historical
 * wholesale year (a first fetch is minutes of polite downloads, not seconds —
 * hence a worker, not an on-screen browser like the Octopus flow), transform
 * through the plan's terms, insert the 365-DayRate plan. Two entry shapes:
 * a terms-only plan JSON from the generate sheet, or the id of an imported
 * pending plan (the self-heal poke on import).
 *
 * Notifications follow the importer conventions: id 17 (2-16 and 4242 taken),
 * silent-by-builder on the DEFAULT-importance channel, cancel action while
 * fetching, terminal note kept on failure.
 */
class DynamicTariffWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val notifier by lazy { NotificationManagerCompat.from(applicationContext) }

    override fun doWork(): Result {
        val repository = ToutcRepository(applicationContext as Application)
        val planId = inputData.getLong(KEY_PLAN_ID, 0L)
        val planJson = inputData.getString(KEY_PLAN_JSON)
        val year = inputData.getInt(KEY_YEAR, 0)

        val plan: PricePlan? = when {
            planId > 0L -> repository.allPricePlansNow?.firstOrNull { it.pricePlanIndex == planId }
            !planJson.isNullOrEmpty() -> JsonTools.createPricePlan(
                Gson().fromJson(planJson, PricePlanJsonFile::class.java)
            )
            else -> null
        }
        val terms = plan?.dynamicTerms
        if (plan == null || terms == null || !terms.isComplete) {
            failTerminal("Dynamic tariff: the plan's terms are missing or incomplete — " +
                    "edit the plan and try again.")
            return Result.failure()
        }
        // Auto plans re-derive their window from the market's latest data, so a
        // year need not be stored; a fixed-window plan must have one.
        val auto = terms.isAutoWindow
        val startMonth = terms.periodStartMonth ?: 1
        val targetYear = when {
            year > 0 -> year
            terms.year != null -> terms.year!!
            auto -> java.time.LocalDate.now().year - 1 // placeholder; discovery sets the real window
            else -> {
                failTerminal("Dynamic tariff '${plan.planName}': no window chosen — " +
                        "pick a 12-month window and regenerate.")
                return Result.failure()
            }
        }
        val source = DynamicRateSources.forMarket(terms.market, applicationContext)
        if (source == null) {
            failTerminal("Dynamic tariff '${plan.planName}': market '${terms.market}' is not " +
                    "available in this edition — the plan stays pending.")
            return Result.failure()
        }

        // Persist a terms-only PENDING row up-front — BEFORE the (minutes-long,
        // possibly failing) fetch — so a plan created from the wizard/generator
        // pane still appears in the list with a pending badge and can be retried,
        // instead of vanishing when generation fails. Only for create paths
        // (a plan JSON, no planId) whose supplier+plan isn't already stored: an
        // imported pending row, or a materialised plan being regenerated, must be
        // left intact so a failed re-fetch keeps the existing plan. On success
        // materialiseBlocking clobbers by supplier+plan, so there's never a dupe.
        if (planId <= 0L && repository.allPricePlansNow
                ?.any { it.supplier == plan.supplier && it.planName == plan.planName } != true) {
            plan.pricePlanIndex = 0
            repository.insert(plan, emptyList(), false)
        }

        progress((if (auto) "Fetching the latest year of ${terms.market} prices"
                  else "Fetching ${terms.market} prices for the 12 months from " +
                          "$targetYear-${"%02d".format(startMonth)}") +
                "… a first fetch takes a few minutes; later generates reuse the cache.")
        val plans = EntryPointAccessors.fromApplication(
            applicationContext, DynamicTariffPlansEntryPoint::class.java
        ).dynamicTariffPlans()

        return when (val result =
                plans.materialiseBlocking(source, plan, targetYear, startMonth, auto)) {
            is DynamicTariffPlans.Result.Generated -> {
                finish("Plan '${result.planName}' is ready" +
                        (if (result.gapFilled > 0) " (${result.gapFilled} half-hours gap-filled)"
                         else "") + " — comparing costs now.")
                // Self-heal poke: the insert seam flagged every scenario for costing;
                // run the chain now rather than waiting for a navigation observer.
                SimulatorLauncher.simulateIfNeeded(applicationContext)
                Result.success()
            }
            is DynamicTariffPlans.Result.Incomplete -> {
                // Name the months (with year — the window can cross a year boundary),
                // in window order, so the user knows exactly what to step past.
                val months = result.missingMonths.distinct()
                    .sortedBy { (it - startMonth + 12) % 12 }
                    .joinToString(", ") { m ->
                        val name = java.time.Month.of(m).getDisplayName(
                            java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                        // A fixed window has a known year per month; an auto window
                        // spans the boundary month across two years, so name only.
                        if (auto) name
                        else "$name ${if (m >= startMonth) targetYear else targetYear + 1}"
                    }
                failTerminal("Couldn't materialise '${plan.planName}': no published market data " +
                        "for $months. Recent months may not be published yet, and windows older " +
                        "than about a year roll off the market's online history — step the window " +
                        "to a fully-covered period. The plan stays pending.")
                Result.failure()
            }
            is DynamicTariffPlans.Result.Failed -> {
                Log.e(TAG, "materialise failed for '${plan.planName}' (attempt $runAttemptCount)",
                        result.error)
                if (result.error is IOException && runAttemptCount + 1 < MAX_RETRIES && !isStopped) {
                    progress("Dynamic tariff: ${reasonOf(result.error)} — retrying " +
                            "(attempt ${runAttemptCount + 2}/$MAX_RETRIES)…")
                    Result.retry()
                } else {
                    failTerminal("Couldn't generate '${plan.planName}': ${reasonOf(result.error)}. " +
                            "The plan stays pending — retry from the plan list.")
                    Result.failure()
                }
            }
        }
    }

    private fun progress(text: String) {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Dynamic tariff")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.housetick)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel)
            .build()
        runCatching { notifier.notify(NOTIFICATION_ID, n) }
    }

    private fun finish(text: String) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Dynamic tariff")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.housetick)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setTimeoutAfter(8000)
            .build()
        runCatching { notifier.notify(NOTIFICATION_ID, n) }
    }

    /** Terminal failure note — stays until dismissed so a quiet failure is still seen. */
    private fun failTerminal(text: String) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Dynamic tariff — not generated")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.housetick)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        runCatching { notifier.notify(NOTIFICATION_ID, n) }
    }

    private fun reasonOf(e: Throwable): String =
        (e.message ?: e.javaClass.simpleName).lineSequence().firstOrNull()?.trim()?.take(200).orEmpty()

    override fun onStopped() {
        runCatching { notifier.cancel(NOTIFICATION_ID) }
    }

    companion object {
        private const val TAG = "DynamicTariff"
        private const val NOTIFICATION_ID = 17
        private const val MAX_RETRIES = 3
        const val KEY_PLAN_ID = "planId"
        const val KEY_PLAN_JSON = "planJson"
        const val KEY_YEAR = "year"

        /** Generate from the sheet: a terms-only plan JSON + the chosen backtest year. */
        @JvmStatic
        fun enqueue(context: Context, planJson: String, planName: String, year: Int) {
            enqueueInternal(context, Data.Builder()
                .putString(KEY_PLAN_JSON, planJson)
                .putInt(KEY_YEAR, year)
                .build(), planName)
        }

        /**
         * Import poke: when an imported plan JSON is a terms-only dynamic plan,
         * auto-enqueue its materialisation. The backtest year defaults to the
         * last complete year when the offer isn't year-bound; if that year has
         * no public data the worker reports it and the pending badge offers
         * retry. No-op for conventional plans.
         */
        @JvmStatic
        fun maybeEnqueuePendingImport(context: Context,
                                      ppj: com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile) {
            val dynamic = ppj.dynamic ?: return
            if (!ppj.rates.isNullOrEmpty()) return
            val planName = ppj.plan ?: return
            val year = dynamic.year ?: (java.time.LocalDate.now().year - 1)
            enqueue(context, Gson().toJson(ppj), planName, year)
        }

        /** Materialise an imported pending plan (the import poke / tap-to-retry). */
        @JvmStatic
        fun enqueueForPlan(context: Context, planId: Long, planName: String, year: Int) {
            enqueueInternal(context, Data.Builder()
                .putLong(KEY_PLAN_ID, planId)
                .putInt(KEY_YEAR, year)
                .build(), planName)
        }

        private fun enqueueInternal(context: Context, data: Data, planName: String) {
            // Profiles without dynamic tariffs never materialise rates — one gate
            // covers every enqueue path (downloader, import, list retry, wizard).
            if (!com.tfcode.comparetout.profile.AppProfiles.current.hasDynamicTariffs) return
            val request = OneTimeWorkRequest.Builder(DynamicTariffWorker::class.java)
                .setInputData(data)
                .build()
            // Unique per plan: repeated taps coalesce instead of stacking fetches.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("DynamicTariff:$planName", ExistingWorkPolicy.KEEP, request)
        }
    }
}
