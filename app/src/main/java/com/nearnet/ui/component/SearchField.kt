package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchField(modifier: Modifier = Modifier, placeholderText: String = "") {
    var searchText by remember { mutableStateOf("") }
    BasicTextField(
        value = searchText,
        onValueChange = { searchText = it },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (searchText.isEmpty()) {
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
                //////
            }
        }
    )
}
