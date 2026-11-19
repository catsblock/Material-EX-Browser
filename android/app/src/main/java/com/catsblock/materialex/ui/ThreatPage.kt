package com.catsblock.materialex.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.security.ThreatInfo
import com.catsblock.materialex.core.security.ThreatKind
import java.text.DateFormat
import java.util.Date

private val DangerRed = Color(0xFFB3261E)
private val OnDanger = Color(0xFFFFFFFF)

@Composable
fun ThreatPage(threat: ThreatInfo, onLeave: () -> Unit, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    var advanced by rememberSaveable(threat.tabId, threat.url) { mutableStateOf(false) }
    val explanation = when (threat.kind) {
        ThreatKind.PHISHING -> R.string.threat_phishing
        ThreatKind.MALWARE -> R.string.threat_malware
        ThreatKind.UNWANTED -> R.string.threat_unwanted
        ThreatKind.HARMFUL -> R.string.threat_harmful
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DangerRed)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 88.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = OnDanger, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.threat_title), style = MaterialTheme.typography.headlineMedium, color = OnDanger)
        Text(threat.url, style = MaterialTheme.typography.bodyMedium, color = OnDanger)
        Text(stringResource(explanation), style = MaterialTheme.typography.bodyLarge, color = OnDanger)
        Text(stringResource(R.string.threat_certainty), style = MaterialTheme.typography.bodyMedium, color = OnDanger)
        Button(
            onClick = onLeave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OnDanger, contentColor = DangerRed)
        ) { Text(stringResource(R.string.threat_back)) }
        TextButton(
            onClick = { advanced = !advanced },
            colors = ButtonDefaults.textButtonColors(contentColor = OnDanger)
        ) { Text(stringResource(R.string.threat_advanced)) }
        AnimatedVisibility(
            visible = advanced,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Detail(R.string.threat_detail_source, stringResource(R.string.threat_source_value))
                Detail(R.string.threat_detail_code, "${threat.engineCodeName} (${threat.engineCode})")
                Detail(R.string.threat_detail_host, threat.host)
                Detail(
                    R.string.threat_detail_time,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(threat.detectedAt))
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = OnDanger.copy(alpha = 0.4f))
        Text(stringResource(R.string.threat_risk), style = MaterialTheme.typography.bodyMedium, color = OnDanger)
        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnDanger)
        ) { Text(stringResource(R.string.threat_continue)) }
    }
}

@Composable
private fun Detail(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = OnDanger.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnDanger)
    }
}
