package com.catsblock.materialex.ui

import android.accounts.AccountManager
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R

private const val GOOGLE_ACCOUNT_TYPE = "com.google"
private const val MAX_EMAIL_LENGTH = 254

class GoogleAccountPicker internal constructor(
    val failed: Boolean,
    val open: () -> Unit,
    val clearError: () -> Unit
)

@Composable
fun rememberGoogleAccountPicker(onPicked: (String) -> Unit): GoogleAccountPicker {
    var failed by rememberSaveable { mutableStateOf(false) }
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty().trim()
                if (name.length in 3..MAX_EMAIL_LENGTH && Patterns.EMAIL_ADDRESS.matcher(name).matches()) {
                    failed = false
                    currentOnPicked(name)
                } else {
                    failed = true
                }
            }
            Activity.RESULT_CANCELED -> Unit
            else -> failed = true
        }
    }
    return GoogleAccountPicker(
        failed = failed,
        open = {
            runCatching {
                launcher.launch(
                    AccountManager.newChooseAccountIntent(
                        null,
                        null,
                        arrayOf(GOOGLE_ACCOUNT_TYPE),
                        null,
                        null,
                        null,
                        null
                    )
                )
            }.onFailure { failed = true }
        },
        clearError = { failed = false }
    )
}

@Composable
fun AccountAndDefaultsSection(
    googleEmail: String,
    onGoogleConnected: (String) -> Unit,
    onGoogleDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val picker = rememberGoogleAccountPicker(onGoogleConnected)
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var isDefault by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    }
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.onboarding_google_title), style = MaterialTheme.typography.titleMedium)
            if (googleEmail.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.onboarding_google_connected, googleEmail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Column {
                    TextButton(onClick = {
                        picker.clearError()
                        picker.open()
                    }) { Text(stringResource(R.string.onboarding_google_action)) }
                    TextButton(onClick = onGoogleDisconnect) {
                        Text(stringResource(R.string.onboarding_google_disconnect))
                    }
                }
            } else {
                FilledTonalButton(onClick = {
                    picker.clearError()
                    picker.open()
                }) { Text(stringResource(R.string.onboarding_google_action)) }
            }
            if (picker.failed) {
                Text(
                    text = stringResource(R.string.onboarding_google_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.onboarding_default_title), style = MaterialTheme.typography.titleMedium)
            if (isDefault) {
                Text(
                    text = stringResource(R.string.onboarding_default_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                FilledTonalButton(onClick = {
                    val intent = if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    } else {
                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    }
                    roleLauncher.launch(intent)
                }) { Text(stringResource(R.string.onboarding_default_action)) }
            }
        }
    }
}
