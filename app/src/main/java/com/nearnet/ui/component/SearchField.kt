package com.nearnet.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearnet.ui.theme.standardIconStyle

@Composable
fun SearchField(modifier: Modifier = Modifier,
                placeholderText: String = "",
                maxChars: Int = Int.MAX_VALUE,
                searchText: String,
                onSearch: (String) -> Unit
                ) {
    var context = LocalContext.current
    BasicTextField(
        value = searchText,
        onValueChange = { text -> if (text.length <= maxChars) onSearch(text) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Search // pokaż "Szukaj" na klawiaturze
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch(searchText) // akcja przy Enter/Szukaj
                Toast.makeText(context, searchText, Toast.LENGTH_SHORT).show()
            }
        ),
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
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .standardIconStyle()
                        .clickable { onSearch(searchText) }
                )
            }
        }
    )
}
