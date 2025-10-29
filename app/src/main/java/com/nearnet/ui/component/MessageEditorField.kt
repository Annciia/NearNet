package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearnet.R
import com.nearnet.ui.model.MESSAGE_MAX_LENGTH
import com.nearnet.ui.theme.standardIconStyleTransparent

@Composable
fun MessageEditorField(
    modifier: Modifier = Modifier,
    placeholderText: String = "",
    minHeight: Dp = 36.dp,
    maxHeight: Dp = 140.dp,
    value: String,
    onValueChange: ((String) -> Unit)
){
    var lineCount by rememberSaveable { mutableIntStateOf(1) }
    val lineHeight = 18.sp
    BasicTextField(
        value = value,
        onValueChange = { text -> if (text.length <= MESSAGE_MAX_LENGTH) onValueChange(text) },
        onTextLayout = { textLayoutResult ->
            lineCount = textLayoutResult.lineCount
        },
        modifier = modifier
            .height(with(LocalDensity.current) { (minHeight + lineHeight.toDp() * (lineCount - 1)).coerceIn(minHeight, maxHeight) }),
        singleLine = false,
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 14.sp,
            lineHeight = lineHeight,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            )
                        )
                    }
                    innerTextField()
                }
                //trailing icon
                Icon(
                    painter = painterResource(R.drawable.emoji),
                    contentDescription = "Emoji",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .standardIconStyleTransparent()
                        .clickable {}
                )
            }
        }
    )
}
