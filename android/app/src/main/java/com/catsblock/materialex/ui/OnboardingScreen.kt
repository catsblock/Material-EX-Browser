package com.catsblock.materialex.ui

import com.catsblock.materialex.core.SigningFingerprint
import com.catsblock.materialex.core.GoogleFailure
import com.catsblock.materialex.core.GoogleErrors
import androidx.compose.foundation.text.selection.SelectionContainer
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.AppLanguages
import com.catsblock.materialex.data.LocaleApplier
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import androidx.activity.result.IntentSenderRequest

private enum class Step { WELCOME, LEGAL, GOOGLE, DEFAULT, DONE }

private enum class LegalDoc(val asset: String, val title: Int) {
    TERMS("TERMS_OF_SERVICE.md", R.string.doc_terms),
    PRIVACY("PRIVACY_POLICY.md", R.string.doc_privacy),
    LICENSE("GPL_LICENSE_INFO.md", R.string.doc_license)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    googleEmail: String,
    onGoogleConnected: (String) -> Unit,
    onGoogleDisconnect: () -> Unit,
    onFinish: () -> Unit
) {
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var licenseAccepted by rememberSaveable { mutableStateOf(false) }
    var openDoc by rememberSaveable { mutableStateOf<String?>(null) }
    val steps = Step.entries
    val step = steps[stepIndex]
    val progress by animateFloatAsState(
        targetValue = (stepIndex + 1f) / steps.size,
        animationSpec = spring(),
        label = "onboardingProgress"
    )

    BackHandler(enabled = openDoc != null) { openDoc = null }
    BackHandler(enabled = openDoc == null && stepIndex > 0) { stepIndex -= 1 }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.onboarding_step_counter, stepIndex + 1, steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = stepIndex,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(spring()) { if (forward) it / 4 else -it / 4 } + fadeIn(spring()))
                        .togetherWith(slideOutHorizontally(spring()) { if (forward) -it / 4 else it / 4 } + fadeOut(spring()))
                },
                label = "onboardingStep"
            ) { index ->
                when (steps[index]) {
                    Step.WELCOME -> WelcomeStep(selectedLanguage, onLanguageSelected)
                    Step.LEGAL -> LegalStep(
                        termsAccepted, { termsAccepted = it },
                        privacyAccepted, { privacyAccepted = it },
                        licenseAccepted, { licenseAccepted = it },
                        onRead = { openDoc = it.name }
                    )
                    Step.DEFAULT -> DefaultBrowserStep()
                    Step.GOOGLE -> GoogleStep(googleEmail, onGoogleConnected, onGoogleDisconnect)
                    Step.DONE -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        HeroHeader(
                            icon = Icons.Default.Check,
                            shape = MaterialShapes.Sunny.toShape(),
                            title = stringResource(R.string.onboarding_done_title),
                            body = stringResource(R.string.onboarding_done_body)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stepIndex > 0) {
                    TextButton(onClick = { stepIndex -= 1 }) { Text(stringResource(R.string.onboarding_back)) }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                val canContinue = step != Step.LEGAL || (termsAccepted && privacyAccepted && licenseAccepted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step == Step.DEFAULT || (step == Step.GOOGLE && googleEmail.isEmpty())) {
                        TextButton(onClick = { stepIndex += 1 }) { Text(stringResource(R.string.onboarding_skip)) }
                    }
                    Button(
                        enabled = canContinue,
                        onClick = { if (step == Step.DONE) onFinish() else stepIndex += 1 }
                    ) {
                        Text(
                            stringResource(
                                if (step == Step.DONE) R.string.onboarding_finish else R.string.onboarding_continue
                            )
                        )
                    }
                }
            }
        }
    }

    val doc = openDoc?.let { LegalDoc.valueOf(it) }
    if (doc != null) {
        LegalDocumentViewer(doc = doc, onClose = { openDoc = null })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroHeader(icon: ImageVector, shape: Shape, title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(80.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeStep(selectedLanguage: String, onLanguageSelected: (String) -> Unit) {
    val detected = remember { AppLanguages.resolve(LocaleApplier.deviceTags()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(64.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_language_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            item {
                LanguageRow(
                    label = stringResource(R.string.onboarding_language_system),
                    supporting = detected.nativeName,
                    selected = selectedLanguage == AppLanguages.SYSTEM,
                    onClick = { onLanguageSelected(AppLanguages.SYSTEM) }
                )
            }
            items(AppLanguages.all, key = { it.tag }) { language ->
                LanguageRow(
                    label = language.nativeName,
                    supporting = null,
                    selected = selectedLanguage.equals(language.tag, ignoreCase = true),
                    onClick = { onLanguageSelected(language.tag) }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, supporting: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LegalStep(
    termsAccepted: Boolean,
    onTerms: (Boolean) -> Unit,
    privacyAccepted: Boolean,
    onPrivacy: (Boolean) -> Unit,
    licenseAccepted: Boolean,
    onLicense: (Boolean) -> Unit,
    onRead: (LegalDoc) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier.size(80.dp).clip(MaterialShapes.Pentagon.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_legal_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_legal_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        ConsentRow(stringResource(R.string.onboarding_accept_terms), termsAccepted, onTerms) { onRead(LegalDoc.TERMS) }
        ConsentRow(stringResource(R.string.onboarding_accept_privacy), privacyAccepted, onPrivacy) { onRead(LegalDoc.PRIVACY) }
        ConsentRow(stringResource(R.string.onboarding_accept_license), licenseAccepted, onLicense) { onRead(LegalDoc.LICENSE) }
    }
}

@Composable
private fun ConsentRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, onRead: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(
            text = label,
            modifier = Modifier.weight(1f).clickable { onChecked(!checked) },
            style = MaterialTheme.typography.bodyLarge
        )
        TextButton(onClick = onRead) { Text(stringResource(R.string.onboarding_read)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoogleStep(email: String, onConnected: (String) -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    var failed by rememberSaveable { mutableStateOf(false) }
    var errorCode by rememberSaveable { mutableStateOf("") }
    val client = remember { Identity.getAuthorizationClient(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { client.getAuthorizationResultFromIntent(result.data) }
            .onSuccess { authorization ->
                val mail = authorization.toGoogleSignInAccount()?.email
                if (mail.isNullOrEmpty()) {
                    failed = true
                    errorCode = "NO_EMAIL"
                } else {
                    failed = false
                    errorCode = ""
                    onConnected(mail)
                }
            }
            .onFailure { error ->
                failed = true
                errorCode = (error as? ApiException)?.statusCode?.toString() ?: error.javaClass.simpleName
            }
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        HeroHeader(
            icon = Icons.Default.Person,
            shape = MaterialShapes.Clover4Leaf.toShape(),
            title = stringResource(R.string.onboarding_google_title),
            body = stringResource(R.string.onboarding_google_body)
        )
        Spacer(Modifier.height(24.dp))
        if (email.isNotEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_google_connected, email),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDisconnect) { Text(stringResource(R.string.onboarding_google_disconnect)) }
        } else {
            FilledTonalButton(onClick = {
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(Scopes.EMAIL), Scope(Scopes.PROFILE)))
                    .build()
                client.authorize(request)
                    .addOnSuccessListener { authorization ->
                        val pending = authorization.pendingIntent
                        if (authorization.hasResolution() && pending != null) {
                            launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        } else {
                            val mail = authorization.toGoogleSignInAccount()?.email
                            if (mail.isNullOrEmpty()) {
                                failed = true
                                errorCode = "NO_EMAIL"
                            } else {
                                failed = false
                                errorCode = ""
                                onConnected(mail)
                            }
                        }
                    }
                    .addOnFailureListener { error ->
                        failed = true
                        errorCode = (error as? ApiException)?.statusCode?.toString() ?: error.javaClass.simpleName
                    }
            }) { Text(stringResource(R.string.onboarding_google_action)) }
            if (failed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_google_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                if (errorCode.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_google_error_code, errorCode),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when (GoogleErrors.classify(errorCode)) {
                        GoogleFailure.NOT_REGISTERED -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_google_error_config, errorCode),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val fingerprint = remember { SigningFingerprint.sha1(context).orEmpty() }
                            SelectionContainer {
                                Text(
                                    text = "${context.packageName}\nSHA-1 $fingerprint",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        GoogleFailure.NETWORK -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_google_error_network),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        GoogleFailure.OTHER -> Unit
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DefaultBrowserStep() {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var isDefault by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        HeroHeader(
            icon = Icons.Default.Home,
            shape = MaterialShapes.Cookie6Sided.toShape(),
            title = stringResource(R.string.onboarding_default_title),
            body = stringResource(R.string.onboarding_default_body)
        )
        Spacer(Modifier.height(24.dp))
        if (isDefault) {
            Text(
                text = stringResource(R.string.onboarding_default_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            FilledTonalButton(onClick = {
                val intent = if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                } else {
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                }
                launcher.launch(intent)
            }) { Text(stringResource(R.string.onboarding_default_action)) }
        }
    }
}

@Composable
private fun LegalDocumentViewer(doc: LegalDoc, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember(doc) {
        runCatching { context.assets.open(doc.asset).bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.onboarding_close))
                }
                Text(
                    text = stringResource(doc.title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}
