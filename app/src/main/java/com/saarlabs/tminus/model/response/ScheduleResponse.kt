package com.saarlabs.tminus.model.response

import com.saarlabs.tminus.model.Schedule
import com.saarlabs.tminus.model.Trip

public data class ScheduleResponse(
    val schedules: List<Schedule>,
    val trips: Map<String, Trip>,
)
