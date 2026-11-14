package com.harisudhan.suras.ui.screens.consent

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harisudhan.suras.R
import com.harisudhan.suras.ui.components.DisclaimerCard

@Composable
fun ConsentScreen(
    onAccepted: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    val accepted by viewModel.accepted.collectAsStateWithLifecycle()
    var checked by remember { mutableStateOf(false) }
    var showRequired by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Hoisted so the content description is read through stringResource (which
    // is configuration-aware) rather than a raw Context lookup inside semantics.
    val checkboxLabel = stringResource(R.string.consent_checkbox)

    LaunchedEffect(accepted) {
        if (accepted) onAccepted()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.consent_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
            ConsentPoint(stringResource(R.string.consent_research))
            ConsentPoint(stringResource(R.string.consent_local_data))
            ConsentPoint(stringResource(R.string.consent_network))
            ConsentPoint(stringResource(R.string.consent_control))
            DisclaimerCard(text = stringResource(R.string.about_disclaimer_body))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        if (it) showRequired = false
                    },
                    modifier = Modifier.semantics {
                        contentDescription = checkboxLabel
                    },
                )
                Text(
                    text = stringResource(R.string.consent_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (showRequired) {
                Text(
                    text = stringResource(R.string.consent_declined),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    if (checked) viewModel.accept() else showRequired = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(stringResource(R.string.action_accept))
            }
            TextButton(
                onClick = { (context as? Activity)?.finish() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_decline))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConsentPoint(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
