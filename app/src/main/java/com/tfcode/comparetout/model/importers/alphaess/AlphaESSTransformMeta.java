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

package com.tfcode.comparetout.model.importers.alphaess;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Per-SN stamp of which version of the AlphaESS transform produced the rows
 * currently sitting in {@code alphaESSTransformedData} for that system.
 *
 * Absent row (or {@code transformVersion < TRANSFORM_VERSION_CURRENT}) means
 * the processed rows are pre-enrichment and the UI should surface a Migrate
 * button on the system card.
 */
@Entity(tableName = "alphaESSTransformMeta")
public class AlphaESSTransformMeta {

    /** v1 = original transform (pv/load/feed/buy/charge only). */
    public static final int TRANSFORM_VERSION_V1 = 1;
    /** v2 = adds pv2x / bat2x / grid2x flows, evActual, batChargeIn, batDischargeOut. */
    public static final int TRANSFORM_VERSION_V2 = 2;
    /** What new rows are stamped with today. */
    public static final int TRANSFORM_VERSION_CURRENT = TRANSFORM_VERSION_V2;

    @PrimaryKey
    @NonNull
    private String sysSn = "";

    private int transformVersion;

    @ColumnInfo(defaultValue = "NULL")
    private Long lastMigratedAt;

    @NonNull
    public String getSysSn() { return sysSn; }
    public void setSysSn(@NonNull String sysSn) { this.sysSn = sysSn; }

    public int getTransformVersion() { return transformVersion; }
    public void setTransformVersion(int transformVersion) { this.transformVersion = transformVersion; }

    public Long getLastMigratedAt() { return lastMigratedAt; }
    public void setLastMigratedAt(Long lastMigratedAt) { this.lastMigratedAt = lastMigratedAt; }
}
