package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearnet.R
import com.nearnet.ui.theme.smallIconStyleTransparent

@Composable
fun PlainTextField(
    modifier: Modifier = Modifier,
    placeholderText: String = "",
    lineHeight: TextUnit = 18.sp,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    maxChars: Int = Int.MAX_VALUE,
    enable: Boolean = true,
    passwordField: Boolean = false,
    value: String,
    onValueChange: ((String) -> Unit)
) {
    val passwordVisible = remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { text -> if (text.length <= maxChars) onValueChange(text) },
        singleLine = singleLine,
        textStyle = LocalTextStyle.current.copy(
            color = if(enable) MaterialTheme.colorScheme.onPrimary else ButtonDefaults.buttonColors().disabledContentColor,
            fontSize = 14.sp,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
        enabled = enable,
        visualTransformation = if (passwordField && !passwordVisible.value) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =  if (passwordField) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = if (enable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = LocalTextStyle.current.copy(
                                color = if (enable) MaterialTheme.colorScheme.onPrimary else ButtonDefaults.buttonColors().disabledContentColor,
                                fontSize = 14.sp,
                                lineHeight = lineHeight,
                            )
                        )
                    }
                    innerTextField()
                }
                //password trailing icon
                if (passwordField && enable) {
                    Icon(
                        painter = painterResource(if (passwordVisible.value) R.drawable.eye_slash else R.drawable.eye),
                        contentDescription = "Password visibility icon.",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .smallIconStyleTransparent()
                            .clickable { passwordVisible.value = !passwordVisible.value }
                    )
                }
            }
        }
    )
}
