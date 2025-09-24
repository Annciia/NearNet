package com.nearnet.sessionlayer.data

data class PackageCommand(
    val roomID: String,
    val command: String,
    val data: String
)
