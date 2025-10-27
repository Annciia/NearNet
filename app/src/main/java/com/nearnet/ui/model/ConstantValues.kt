package com.nearnet.ui.model

import androidx.compose.ui.unit.dp

const val ROOM_NAME_MAX_LENGTH = 64
const val ROOM_DESCRIPTION_MAX_LENGTH = 256
const val ROOM_DESCRIPTION_MAX_LINES = 6

const val FILE_ATTACHMENT_MAX_SIZE = 500 * 1024 // 500 KiB (to 711kB tested correct sending, 911kB is too big)
const val IMAGE_ATTACHMENT_MAX_SIZE = 2*256 //Max edge in pixels
const val IMAGE_COMPRESSION_QUALITY = 100 // Quality in % sended images
const val MESSAGE_MAX_LENGTH = 10 * 1024 // 100KiB  //TODO zablokować możliwość pisania więcej tekstu użytkownikowi

val MINIATURE_MAX_SIZE = 150.dp
