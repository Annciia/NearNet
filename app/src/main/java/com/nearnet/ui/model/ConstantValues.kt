package com.nearnet.ui.model

import androidx.compose.ui.unit.dp

const val ROOM_NAME_MAX_LENGTH = 64
const val ROOM_DESCRIPTION_MAX_LENGTH = 256
const val ROOM_DESCRIPTION_MAX_LINES = 6
const val ROOM_PASSWORD_MAX_LENGTH = 256
const val USER_LOGIN_MAX_LENGTH = 64
const val USER_NAME_MAX_LENGTH = 64
const val USER_PASSWORD_MAX_LENGTH = 256

const val MESSAGE_MAX_LENGTH = 10 * 1024
const val FILE_ATTACHMENT_MAX_SIZE = 500 * 1024 //500 KiB (up to 711kB sent successfully, 911kB was too large)
const val IMAGE_ATTACHMENT_MAX_SIZE = 2*256 //Max edge in pixels
const val IMAGE_COMPRESSION_QUALITY = 100 //Quality in % of sent images

val MINIATURE_MAX_SIZE = 150.dp
