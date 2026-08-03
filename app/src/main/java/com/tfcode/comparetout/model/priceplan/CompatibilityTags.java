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

package com.tfcode.comparetout.model.priceplan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which import plans an export plan may legitimately be paired with — supplier
 * bundling rules, expressed as a list of match strings. Stored as JSON TEXT via
 * a {@link com.tfcode.comparetout.model.Converters} TypeConverter, the same
 * pattern as {@link Restrictions} and {@link DynamicTerms}.
 *
 * <p>Tag grammar (case-insensitive, trimmed):
 * <table>
 *   <tr><td>{@code Supplier:*}</td><td>any import plan from that supplier</td></tr>
 *   <tr><td>{@code Supplier:Plan name}</td><td>exactly that supplier + plan</td></tr>
 *   <tr><td>{@code *}</td><td>every import plan (explicit open-market marker)</td></tr>
 * </table>
 *
 * <p>An export plan with <b>no tags at all</b> is an open-market tariff and
 * matches everything — so the absence of this object is never a restriction.
 *
 * <p>Matching is <b>live</b>: tags are evaluated against import plans' current
 * supplier/planName every time, never snapshotted, so renaming an import plan
 * cannot silently break a pairing.
 *
 * <p>Exclusion (e.g. {@code !Octopus Energy:Tracker}) is deliberately not
 * implemented. Because a tag is just a string, adding it later needs no schema
 * change — see plans/region/import-plans.md §2.2.
 */
public class CompatibilityTags {

    /** Wildcard accepted both as a whole tag and as the plan half of a tag. */
    public static final String WILDCARD = "*";

    @NonNull
    private List<String> tags = new ArrayList<>();

    public CompatibilityTags() {}

    public CompatibilityTags(@NonNull List<String> tags) {
        this.tags = new ArrayList<>(tags);
    }

    @NonNull
    public List<String> getTags() {
        // Gson can deserialise a null into the field, bypassing the initialiser.
        return null == tags ? new ArrayList<>() : tags;
    }

    public void setTags(@NonNull List<String> tags) {
        this.tags = tags;
    }

    /** True when no tag restricts pairing — an open-market export tariff. */
    public boolean isOpenMarket() {
        for (String tag : getTags()) {
            if (!(null == tag) && !tag.trim().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Does this tag set admit {@code importPlan}?
     *
     * @param importPlan the candidate import plan; null never matches
     * @return true when open-market or when any tag matches
     */
    public boolean matches(@Nullable PricePlan importPlan) {
        if (null == importPlan) return false;
        if (isOpenMarket()) return true;
        for (String tag : getTags()) {
            if (matchesTag(tag, importPlan.getSupplier(), importPlan.getPlanName())) return true;
        }
        return false;
    }

    /**
     * Match one tag against one supplier/plan pair. Split on the FIRST colon
     * only, so a plan name containing a colon still matches.
     *
     * <p>Public so the combination-selection UI can evaluate a single tag against
     * a list row without re-implementing the wildcard grammar — there must be
     * exactly one definition of what a tag means.
     */
    public static boolean matchesTag(@Nullable String tag, @NonNull String supplier,
                                     @NonNull String planName) {
        if (null == tag) return false;
        String t = tag.trim();
        if (t.isEmpty()) return false;
        if (WILDCARD.equals(t)) return true;

        int split = t.indexOf(':');
        if (split < 0) {
            // A bare supplier name behaves as "Supplier:*" — the shape users type
            // by hand before they discover the wildcard syntax.
            return supplier.trim().equalsIgnoreCase(t);
        }
        String tagSupplier = t.substring(0, split).trim();
        String tagPlan = t.substring(split + 1).trim();
        if (!supplier.trim().equalsIgnoreCase(tagSupplier)) return false;
        return WILDCARD.equals(tagPlan) || planName.trim().equalsIgnoreCase(tagPlan);
    }

    /** A tag naming every plan from {@code supplier}, the auto-discovery default. */
    @NonNull
    public static String supplierWildcard(@NonNull String supplier) {
        return supplier + ":" + WILDCARD;
    }
}
