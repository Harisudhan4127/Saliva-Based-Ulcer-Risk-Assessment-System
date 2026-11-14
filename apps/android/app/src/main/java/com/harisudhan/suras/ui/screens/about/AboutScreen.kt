package com.harisudhan.suras.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harisudhan.suras.BuildConfig
import com.harisudhan.suras.R
import com.harisudhan.suras.ui.components.DisclaimerCard
import com.harisudhan.suras.ui.components.KeyValueRow
import com.harisudhan.suras.ui.components.SectionCard
import com.harisudhan.suras.ui.components.SurasTopBar

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        SurasTopBar(title = stringResource(R.string.about_title), onBack = onBack)
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.about_project),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_author_heading)) {
                    Text(
                        text = stringResource(R.string.about_author_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.about_author_degree),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_author_role),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_contact_heading)) {
                    KeyValueRow(
                        label = stringResource(R.string.about_email),
                        value = stringResource(R.string.about_contact_todo),
                    )
                    KeyValueRow(
                        label = stringResource(R.string.about_github),
                        value = stringResource(R.string.about_contact_todo),
                    )
                    KeyValueRow(
                        label = stringResource(R.string.about_linkedin),
                        value = stringResource(R.string.about_contact_todo),
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_model_heading)) {
                    Text(
                        text = stringResource(R.string.about_model_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.about_version_line,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.MODEL_VERSION,
                            BuildConfig.SWBRA_SPEC_VERSION,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_swbra_heading)) {
                    Text(
                        text = stringResource(R.string.about_swbra_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_data_heading)) {
                    Text(
                        text = stringResource(R.string.about_data_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_license_heading)) {
                    Text(
                        text = stringResource(R.string.about_license_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_disclaimer_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                DisclaimerCard(text = stringResource(R.string.about_disclaimer_body))
            }
        }
    }
}
