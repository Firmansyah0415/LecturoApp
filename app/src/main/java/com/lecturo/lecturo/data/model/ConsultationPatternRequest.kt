package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

data class ConsultationPatternRequest(
    @SerializedName("uid") val uid: String,
    @SerializedName("pattern_id") val patternId: String?,
    @SerializedName("title_template") val titleTemplate: String,
    @SerializedName("day_of_week") val dayOfWeek: Int,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("location_default") val locationDefault: String?,
    @SerializedName("is_active") val isActive: Boolean
)