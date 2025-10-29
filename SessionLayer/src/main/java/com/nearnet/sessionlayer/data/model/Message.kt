package com.nearnet.sessionlayer.data.model

//data class Message(
//    val username: String,
//    val message: String,
//    val timestamp: Long,
//    val roomId: String
//)

data class Message(
    val id: String = "",
    val roomId: String,
    val userId: String,
    val messageType: String = "TEXT",
    val message: String,
    val additionalData: String = "",
    val timestamp: String
)
