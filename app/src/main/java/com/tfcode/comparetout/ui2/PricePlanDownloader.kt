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

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the community-maintained Irish supplier tariffs published on the
 * project's doc site and inserts them as price plans.
 *
 * This is the same payload + transform the legacy `MainActivity` "download"
 * menu used (`MainActivity.java:600-665`), ported to Kotlin/coroutines and
 * routed through the public [ToutcRepository] surface — no DAO/Java edits.
 *
 * Simple mode needs *real* tariffs to show a meaningful cost (fabricated
 * samples are unacceptable), but the list is community-maintained and **may be
 * out of date** — callers must surface that caveat. The price-plan editor stays
 * available so the user can correct anything that has drifted.
 */
@Singleton
class PricePlanDownloader @Inject constructor(
    private val repository: ToutcRepository
) {

    sealed class Result {
        /** [added] new plans inserted, [replaced] existing names overwritten
         * (only possible when clobber = true). */
        data class Loaded(val added: Int, val replaced: Int) : Result()
        /** The fetch succeeded but the list was empty. */
        data object Empty : Result()
        /** No connectivity / host unreachable — distinct so the UI can prompt a retry. */
        data object NoNetwork : Result()
        data class Failed(val error: Throwable) : Result()
    }

    /**
     * @param clobber when true, a downloaded plan whose name already exists
     *   overwrites it; when false (default) the existing plan is kept and the
     *   download is effectively additive.
     */
    suspend fun download(clobber: Boolean = false): Result = withContext(Dispatchers.IO) {
        try {
            val plans: List<PricePlanJsonFile> = URL(RATES_URL).openStream().use { stream ->
                InputStreamReader(stream).use { reader ->
                    Gson().fromJson(reader, object : TypeToken<List<PricePlanJsonFile>>() {}.type)
                }
            } ?: emptyList()

            if (plans.isEmpty()) return@withContext Result.Empty

            val existingNames: Set<String> =
                repository.allPricePlansNow?.mapNotNull { it.planName }?.toSet().orEmpty()
            var added = 0
            var replaced = 0
            plans.forEach { pp ->
                val plan = JsonTools.createPricePlan(pp)
                val drs = ArrayList<DayRate>()
                pp.rates?.forEach { drs.add(JsonTools.createDayRate(it)) }
                repository.insert(plan, drs, clobber)
                if (plan.planName in existingNames) replaced += 1 else added += 1
            }
            Result.Loaded(added, replaced)
        } catch (e: UnknownHostException) {
            Result.NoNetwork
        } catch (e: ConnectException) {
            Result.NoNetwork
        } catch (e: SocketTimeoutException) {
            Result.NoNetwork
        } catch (e: IOException) {
            Result.Failed(e)
        } catch (e: Exception) {
            Result.Failed(e)
        }
    }

    companion object {
        const val RATES_URL =
            "https://raw.githubusercontent.com/Tonyslogic/comparetout-doc/main/price-plans/rates.json"
    }
}

/** Hilt EntryPoint so Composables can resolve [PricePlanDownloader] without an injected ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PricePlanDownloaderEntryPoint {
    fun pricePlanDownloader(): PricePlanDownloader
}
