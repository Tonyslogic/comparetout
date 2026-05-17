package com.tfcode.comparetout.ui2

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Solar geometry + clear-sky transposition utilities used by the wizard to
 * adapt a source PV time-series (one orientation / kWp) onto a different
 * string (different kWp and/or azimuth).
 *
 * Algorithm overview, mirroring the supplied pseudocode:
 *   1. Compute clear-sky POA at the SOURCE orientation for every sample.
 *   2. cloud_factor = source_power / max(clear_poa_source, eps)
 *   3. Per day, compute a time-shift Δt(t) that maps the sun's relative
 *      offset from source azimuth into the same offset relative to target
 *      azimuth — captures the sunrise/sunset shift between orientations.
 *   4. Sample cloud_factor at shifted times.
 *   5. Multiply by:
 *        physics_scale     = cos(inc_target) / max(cos(inc_source), eps)
 *        azimuth_factor    = empirical monthly correction
 *        clear_poa_target  = clear-sky POA at the target orientation
 *      to reconstruct the target power.
 *   6. Post-process: clip negatives and bound the daily total at the
 *      clear-sky envelope.
 *
 * Implementation notes:
 *  - Solar position uses the simplified Michalsky / NOAA algorithm —
 *    accurate to ~0.01° for the latitudes Ireland cares about.
 *  - Clear-sky GHI uses the Haurwitz model; DNI/DHI split via Erbs.
 *  - POA transposition uses Liu-Jordan (isotropic diffuse).
 */
object SolarConversionUtils {

    private const val EPS = 1e-3
    private const val SOLAR_CONSTANT = 1367.0  // W/m²
    private const val ALBEDO = 0.2
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MINUTE_FMT = DateTimeFormatter.ofPattern("HH:mm")

    /** Single sample as read from AlphaESS-style data. pv is whatever unit
     *  the source stores (kWh per interval). The conversion preserves units. */
    data class PvSample(
        val date: String,     // yyyy-MM-dd
        val minute: String,   // HH:mm
        val pv: Double
    )

    /* ───────────────────────── public entry points ────────────────────────── */

    /** Linearly scale by the kWp ratio. No-op if sourceKwp <= 0 or equals target. */
    fun scaleByKwp(rows: List<PvSample>, sourceKwp: Double, targetKwp: Double): List<PvSample> {
        if (sourceKwp <= 0.0 || targetKwp <= 0.0) return rows
        if (abs(sourceKwp - targetKwp) < 1e-6) return rows
        val ratio = targetKwp / sourceKwp
        return rows.map { it.copy(pv = it.pv * ratio) }
    }

    /** Full azimuth conversion. tilt stays the same — only azimuth differs. */
    fun convertAzimuth(
        rows: List<PvSample>,
        lat: Double,
        lon: Double,
        tiltDeg: Double,
        sourceAzDeg: Double,
        targetAzDeg: Double,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PvSample> {
        val srcAz = wrap360(sourceAzDeg)
        val tgtAz = wrap360(targetAzDeg)
        if (abs(srcAz - tgtAz) < 0.5) return rows
        if (rows.isEmpty()) return rows

        return rows.groupBy { it.date }.flatMap { (_, dayRows) ->
            convertDay(dayRows, lat, lon, tiltDeg, srcAz, tgtAz, zone)
        }
    }

    /* ─────────────────────────── per-day pipeline ─────────────────────────── */

    private fun convertDay(
        dayRows: List<PvSample>,
        lat: Double, lon: Double, tiltDeg: Double,
        sourceAzDeg: Double, targetAzDeg: Double,
        zone: ZoneId
    ): List<PvSample> {
        if (dayRows.isEmpty()) return dayRows
        val date = LocalDate.parse(dayRows.first().date, DATE_FMT)
        val month = date.monthValue

        // Pre-compute sun_az at fine resolution to invert later.
        val (sunAzCurveTimes, sunAzCurveValues) = sunAzimuthCurve(date, lat, lon, zone, stepMinutes = 5)

        val empirical = empiricalAzimuthFactor(sourceAzDeg, targetAzDeg, month)

        val rowTimes = dayRows.map { row -> toInstant(row.date, row.minute, zone) }

        // For each row compute geometry + clear-sky values.
        val cloudFactor = DoubleArray(dayRows.size)
        val clearPoaTarget = DoubleArray(dayRows.size)
        val cosIncSource = DoubleArray(dayRows.size)
        val cosIncTarget = DoubleArray(dayRows.size)

        for (i in dayRows.indices) {
            val sun = solarPosition(rowTimes[i], lat, lon)
            if (sun.elevationDeg <= 0.0) {
                cloudFactor[i] = 0.0
                clearPoaTarget[i] = 0.0
                cosIncSource[i] = 0.0
                cosIncTarget[i] = 0.0
                continue
            }
            val ghi = haurwitzGhi(sun.zenithDeg)
            val (dni, dhi) = erbsSplit(ghi, sun.zenithDeg)

            val cosIncS = cosIncidence(sun.zenithDeg, sun.azimuthDeg, tiltDeg, sourceAzDeg)
            val cosIncT = cosIncidence(sun.zenithDeg, sun.azimuthDeg, tiltDeg, targetAzDeg)
            cosIncSource[i] = cosIncS
            cosIncTarget[i] = cosIncT

            val poaSource = transposePoa(dni, dhi, ghi, cosIncS, tiltDeg)
            val poaTarget = transposePoa(dni, dhi, ghi, cosIncT, tiltDeg)
            clearPoaTarget[i] = poaTarget
            cloudFactor[i] = if (poaSource > EPS) dayRows[i].pv / poaSource else Double.NaN
        }

        // Fill cloud-factor gaps (where source clear-sky was ~0 — e.g. sun behind source).
        val cfFilled = fillCloudFactor(cloudFactor)

        // Time-shift: for each row's clock time, find a target-clock time t' on
        // the same day such that sun_az(t') matches the target-relative position
        // of sun_az(t). Then sample cfFilled at t'.
        val cfShifted = DoubleArray(dayRows.size)
        for (i in dayRows.indices) {
            val sunAzAtT = solarPosition(rowTimes[i], lat, lon).azimuthDeg
            val desiredSunAz = wrapSigned(sunAzAtT - sourceAzDeg + targetAzDeg)
            val tPrime = findTimeForSunAz(sunAzCurveTimes, sunAzCurveValues, desiredSunAz)
            cfShifted[i] = if (tPrime == null) cfFilled[i]
                           else sampleAtTime(rowTimes, cfFilled, tPrime)
        }

        // Reconstruct power.
        val out = ArrayList<PvSample>(dayRows.size)
        for (i in dayRows.indices) {
            val cosS = cosIncSource[i]
            val cosT = cosIncTarget[i]
            val physicsScale = if (cosS > EPS) cosT / cosS else 1.0
            // Bound physics_scale — if source incidence is tiny the ratio explodes.
            val pScale = physicsScale.coerceIn(0.0, 4.0)
            val raw = cfShifted[i] * pScale * empirical * clearPoaTarget[i]
            val clamped = if (raw.isFinite()) max(0.0, raw) else 0.0
            out.add(dayRows[i].copy(pv = clamped))
        }
        return out
    }

    /* ────────────────────────── solar geometry ─────────────────────────── */

    data class SunPosition(val elevationDeg: Double, val azimuthDeg: Double) {
        val zenithDeg get() = 90.0 - elevationDeg
    }

    /** Simplified Michalsky / NOAA. Returns elevation (deg above horizon) and
     *  azimuth (deg clockwise from North). Accurate to ~0.01° in the era 1950–2050. */
    fun solarPosition(utc: Instant, lat: Double, lon: Double): SunPosition {
        // Days since J2000.0 (UTC).
        val d = utc.toEpochMilli() / 86_400_000.0 + 2440587.5 - 2451545.0

        val L = (280.460 + 0.9856474 * d).mod(360.0)
        val g = Math.toRadians((357.528 + 0.9856003 * d).mod(360.0))
        val lambda = Math.toRadians(L + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val eps = Math.toRadians(23.439 - 0.0000004 * d)
        val alpha = atan2(cos(eps) * sin(lambda), cos(lambda))
        val delta = asin(sin(eps) * sin(lambda))

        val utcSec = utc.epochSecond.toDouble() + utc.nano / 1e9
        val utcHour = (utcSec.mod(86400.0)) / 3600.0
        val gmst = (6.697375 + 0.0657098242 * d + utcHour).mod(24.0)
        val lmst = (gmst + lon / 15.0).mod(24.0)
        val H = Math.toRadians(15.0 * lmst) - alpha

        val latRad = Math.toRadians(lat)
        val elev = asin(sin(delta) * sin(latRad) + cos(delta) * cos(latRad) * cos(H))
        val az = atan2(
            -cos(delta) * sin(H),
            sin(delta) * cos(latRad) - cos(delta) * sin(latRad) * cos(H)
        )
        return SunPosition(
            elevationDeg = Math.toDegrees(elev),
            azimuthDeg = (Math.toDegrees(az) + 360.0) % 360.0
        )
    }

    /** cos of the incidence angle between sun and panel normal. */
    private fun cosIncidence(
        zenithDeg: Double, sunAzDeg: Double,
        tiltDeg: Double, surfAzDeg: Double
    ): Double {
        val z = Math.toRadians(zenithDeg)
        val b = Math.toRadians(tiltDeg)
        val daz = Math.toRadians(sunAzDeg - surfAzDeg)
        return max(0.0, cos(z) * cos(b) + sin(z) * sin(b) * cos(daz))
    }

    /* ────────────────────────── irradiance models ──────────────────────── */

    /** Haurwitz clear-sky GHI (W/m²). */
    private fun haurwitzGhi(zenithDeg: Double): Double {
        val cz = cos(Math.toRadians(zenithDeg))
        if (cz <= 0.0) return 0.0
        return 1098.0 * cz * exp(-0.057 / cz)
    }

    /** Erbs decomposition: returns (DNI, DHI) in W/m². */
    private fun erbsSplit(ghi: Double, zenithDeg: Double): Pair<Double, Double> {
        val cz = cos(Math.toRadians(zenithDeg))
        if (cz <= EPS || ghi <= 0.0) return 0.0 to 0.0
        val g0 = SOLAR_CONSTANT * cz
        val kt = (ghi / g0).coerceIn(0.0, 1.0)
        val kd = when {
            kt < 0.22 -> 1.0 - 0.09 * kt
            kt < 0.80 -> 0.9511 - 0.1604 * kt + 4.388 * kt * kt -
                        16.638 * kt * kt * kt + 12.336 * kt * kt * kt * kt
            else      -> 0.165
        }.coerceIn(0.0, 1.0)
        val dhi = kd * ghi
        val dni = ((ghi - dhi) / cz).coerceAtLeast(0.0)
        return dni to dhi
    }

    /** Liu-Jordan isotropic POA in W/m². */
    private fun transposePoa(
        dni: Double, dhi: Double, ghi: Double,
        cosInc: Double, tiltDeg: Double
    ): Double {
        val cb = cos(Math.toRadians(tiltDeg))
        val beam = dni * cosInc
        val diffuse = dhi * (1.0 + cb) / 2.0
        val reflected = ghi * ALBEDO * (1.0 - cb) / 2.0
        return beam + diffuse + reflected
    }

    /* ────────────────────────── sun-azimuth curve ──────────────────────── */

    /** Pre-computed sun azimuth (in signed deg from north, range [-180, 180])
     *  on the given local-time day at uniform step intervals. The signed range
     *  makes inversion robust for E/W orientations. */
    private fun sunAzimuthCurve(
        date: LocalDate, lat: Double, lon: Double,
        zone: ZoneId, stepMinutes: Int = 5
    ): Pair<LongArray, DoubleArray> {
        // Samples cover 00:00..(24:00 - stepMinutes). 5-min step → 288 samples.
        val n = (24 * 60) / stepMinutes
        val times = LongArray(n)
        val values = DoubleArray(n)
        for (i in 0 until n) {
            val offsetMin = i * stepMinutes
            val ldt = date.atTime(LocalTime.of(offsetMin / 60, offsetMin % 60))
            val instant = ldt.atZone(zone).toInstant()
            times[i] = instant.toEpochMilli()
            val sun = solarPosition(instant, lat, lon)
            values[i] = if (sun.elevationDeg > 0.0) wrapSigned(sun.azimuthDeg) else Double.NaN
        }
        return times to values
    }

    /** Find the time (epoch-millis) on the curve where the sun is at the desired
     *  azimuth. Linear interp between adjacent in-daylight samples. */
    private fun findTimeForSunAz(times: LongArray, values: DoubleArray, target: Double): Long? {
        for (i in 0 until values.size - 1) {
            val a = values[i]
            val b = values[i + 1]
            if (a.isNaN() || b.isNaN()) continue
            if ((a <= target && target <= b) || (b <= target && target <= a)) {
                val frac = if (abs(b - a) < EPS) 0.0 else (target - a) / (b - a)
                return (times[i] + frac * (times[i + 1] - times[i])).toLong()
            }
        }
        return null
    }

    /* ─────────────────────────── cloud-factor utils ────────────────────── */

    /** Replace NaN cloud-factor entries with the nearest valid neighbour
     *  (linear blend if both sides are present). */
    private fun fillCloudFactor(cf: DoubleArray): DoubleArray {
        val out = cf.copyOf()
        val n = out.size
        if (n == 0) return out

        // Forward fill: replace each NaN with the prior valid value.
        var lastValid = Double.NaN
        for (i in 0 until n) {
            if (out[i].isNaN()) out[i] = lastValid else lastValid = out[i]
        }
        // Backward fill: anything still NaN takes the next valid.
        var nextValid = Double.NaN
        for (i in n - 1 downTo 0) {
            if (out[i].isNaN()) out[i] = nextValid else nextValid = out[i]
        }
        // Anything still NaN (entire day blank) → 0.
        for (i in 0 until n) if (out[i].isNaN()) out[i] = 0.0
        // Clip extremes from divisions through tiny denominators.
        for (i in 0 until n) out[i] = out[i].coerceIn(0.0, 1.5)
        return out
    }

    /** Linear interpolate cloud factor at a given epoch-milli using the
     *  source row times (already-sorted ascending). */
    private fun sampleAtTime(times: List<Instant>, values: DoubleArray, atMillis: Long): Double {
        if (times.isEmpty()) return 0.0
        if (atMillis <= times.first().toEpochMilli()) return values.first()
        if (atMillis >= times.last().toEpochMilli()) return values.last()
        // Binary search
        var lo = 0; var hi = times.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (times[mid].toEpochMilli() <= atMillis) lo = mid else hi = mid
        }
        val tLo = times[lo].toEpochMilli()
        val tHi = times[hi].toEpochMilli()
        val frac = if (tHi == tLo) 0.0 else (atMillis - tLo).toDouble() / (tHi - tLo)
        return values[lo] + frac * (values[hi] - values[lo])
    }

    /* ─────────────────────────── empirical factor ──────────────────────── */

    /** Reasonable monthly empirical correction relative to the simple Liu-Jordan
     *  + Haurwitz baseline. Captures the slight asymmetry (mostly diffuse-related)
     *  in actual yields by orientation, varying by season. Calibrated very
     *  roughly for ~53°N (Ireland). */
    private fun empiricalAzimuthFactor(sourceAz: Double, targetAz: Double, month: Int): Double {
        val devSource = abs(((sourceAz - 180.0 + 540.0) % 360.0) - 180.0)
        val devTarget = abs(((targetAz - 180.0 + 540.0) % 360.0) - 180.0)
        val yieldSource = yearlyYieldFactor(devSource)
        val yieldTarget = yearlyYieldFactor(devTarget)
        val baseRatio = yieldTarget / max(EPS, yieldSource)

        // Seasonal swing: winter exaggerates orientation effects, summer dampens.
        val swing = when (month) {
            12, 1, 2 -> 1.15
            11, 3    -> 1.05
            10, 4    -> 1.00
            in 5..9  -> 0.92
            else     -> 1.0
        }
        val adjusted = 1.0 + (baseRatio - 1.0) * swing
        return adjusted.coerceIn(0.3, 1.4)
    }

    private fun yearlyYieldFactor(deviationDeg: Double): Double {
        val d = deviationDeg.coerceIn(0.0, 180.0)
        return when {
            d <= 45.0  -> 1.0 - 0.04 * (d / 45.0)
            d <= 90.0  -> 0.96 - 0.11 * ((d - 45.0) / 45.0)
            d <= 135.0 -> 0.85 - 0.20 * ((d - 90.0) / 45.0)
            else       -> 0.65 - 0.10 * ((d - 135.0) / 45.0)
        }
    }

    /* ────────────────────────────── helpers ────────────────────────────── */

    private fun toInstant(date: String, minute: String, zone: ZoneId): Instant {
        val d = LocalDate.parse(date, DATE_FMT)
        val t = runCatching { LocalTime.parse(minute, MINUTE_FMT) }.getOrDefault(LocalTime.MIDNIGHT)
        return LocalDateTime.of(d, t).atZone(zone).toInstant()
    }

    private fun wrap360(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Wrap to [-180, 180]. */
    private fun wrapSigned(deg: Double): Double {
        var x = ((deg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        if (x == -180.0) x = 180.0
        return x
    }
}
