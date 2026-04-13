package com.saarlabs.tminus.commute

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * A saved commute: recurring days, target departure window, and notification lead time
 * (minutes before the train departs the origin stop).
 */
@Serializable
public data class CommuteProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fromStopId: String,
    val toStopId: String,
    val fromLabel: String = "",
    val toLabel: String = "",
    /** Days: 1 = Monday … 7 = Sunday (ISO, same as [java.time.DayOfWeek]). */
    val daysOfWeek: List<Int>,
    /**
     * Target commute time as minutes from midnight in America/New_York (e.g. 7:30 AM = 7*60+30).
     * The schedule lookup window is centered on this time.
     */
    val targetMinutesFromMidnight: Int,
    /** Minutes before [targetMinutesFromMidnight] to start looking for trips (default 45). */
    val windowMinutesBefore: Int = 45,
    /** Minutes after target to end the window (default 45). */
    val windowMinutesAfter: Int = 45,
    /**
     * Notify this many minutes before the train departs your origin stop (time to leave).
     * Example: 12 means "leave" alert at departure − 12 minutes.
     */
    val notifyLeadMinutes: Int = 12,
    /** Optional second ping when the train is due at the destination (schedule-based). */
    val notifyOnArrival: Boolean = true,
    val enabled: Boolean = true,
)
