package com.nearnet.sessionlayer.data.model

data class RoomListResponse(
    val command: String,
    val data: List<RoomData>,
    val error: String? = null
)

