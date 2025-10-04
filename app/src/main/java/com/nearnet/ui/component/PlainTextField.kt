package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlainTextField(
    modifier: Modifier = Modifier,
    placeholderText: String = "",
    lineHeight: TextUnit = 18.sp,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    maxChars: Int = Int.MAX_VALUE,
    enable: Boolean = true,
    value: String,
    onValueChange: ((String) -> Unit)
) {
    BasicTextField(
        value = value,
        onValueChange = { text -> if (text.length <= maxChars) onValueChange(text) },
        singleLine = singleLine,
        textStyle = LocalTextStyle.current.copy(
            color = if(enable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
        enabled = enable,
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = if(enable) MaterialTheme.colorScheme.primary else Color.LightGray,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = LocalTextStyle.current.copy(
                                color = if(enable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                lineHeight = lineHeight,
                            )
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}
