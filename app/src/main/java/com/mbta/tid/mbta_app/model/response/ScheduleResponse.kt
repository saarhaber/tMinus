package com.mbta.tid.mbta_app.model.response

import com.mbta.tid.mbta_app.model.Schedule
import com.mbta.tid.mbta_app.model.Trip

public data class ScheduleResponse(
    val schedules: List<Schedule>,
    val trips: Map<String, Trip>,
)
