package com.nearnet.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.standardIconStyle() : Modifier = this
    .size(22.dp)
    .clip(RoundedCornerShape(6.dp))
    .background(MaterialTheme.colorScheme.secondary)
    .padding(1.dp)

@Composable
fun Modifier.standardIconStyleTransparent() : Modifier = this
    .size(22.dp)
    .clip(RoundedCornerShape(6.dp))
    .padding(1.dp)

@Composable
fun Modifier.smallIconStyleTransparent() : Modifier = this
    .size(18.dp)
    .clip(RoundedCornerShape(6.dp))
    .padding(1.dp)