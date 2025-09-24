package com.nearnet.sessionlayer.data.model

data class Message(
    val username: String,
    val message: String,
    val timestamp: Long,
    val roomId: String
)
