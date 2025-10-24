package com.nearnet.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


enum class PasswordValidationResult {
    CORRECT,
    NO_UPPERCASE,
    NO_LOWERCASE,
    NO_NUMBERS,
    TOO_SHORT,
    CONFIRMATION_MISMATCH
}

val passwordValidationResultText = mapOf(
    PasswordValidationResult.CORRECT to "Password is correct.",
    PasswordValidationResult.NO_UPPERCASE to "Password must contain at least one uppercase letter.",
    PasswordValidationResult.NO_LOWERCASE to "Password must contain at least one lowercase letter.",
    PasswordValidationResult.NO_NUMBERS to "Password must contain at least one number.",
    PasswordValidationResult.TOO_SHORT to "Password is too short.",
    PasswordValidationResult.CONFIRMATION_MISMATCH to "Password does not match the confirmation.",
)

fun validatePassword(password: String, passwordConfirmation: String): PasswordValidationResult {
    if (!password.any { it in 'a'..'z' }) return PasswordValidationResult.NO_LOWERCASE;
    if (!password.any { it in 'A'..'Z' }) return PasswordValidationResult.NO_UPPERCASE;
    if (!password.any { it in '0'..'9' }) return PasswordValidationResult.NO_NUMBERS;
    if (password.length < 8) return PasswordValidationResult.TOO_SHORT;
    if (password != passwordConfirmation) return PasswordValidationResult.CONFIRMATION_MISMATCH
    return PasswordValidationResult.CORRECT
}

@Composable
fun PasswordValidationText(password: String, passwordConfirmation: String) {
    val result = validatePassword(password, passwordConfirmation)
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = passwordValidationResultText[result] ?: "Unknown error.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
