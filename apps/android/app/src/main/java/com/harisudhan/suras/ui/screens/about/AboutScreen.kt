package com.harisudhan.suras.ui.screens.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.harisudhan.suras.BuildConfig
import com.harisudhan.suras.R
import com.harisudhan.suras.ui.components.DisclaimerCard
import com.harisudhan.suras.ui.components.KeyValueRow
import com.harisudhan.suras.ui.components.SectionCard
import com.harisudhan.suras.ui.components.SurasTopBar
import org.json.JSONObject

private data class AuthorProfile(
    val name: String = "Harisudhan Baskaran",
    val title: String = "",
    val intro: String = "",
    val goal: String = "",
    val whatIDo: String = "",
    val approach: String = "",
    val degree: String = "",
    val institution: String = "",
    val portfolio: String = "",
    val github: String = "",
    val linkedin: String = "",
    val email: String = "",
    val contactNote: String = "",
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { loadAuthorProfile(context) }

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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (profile.title.isNotBlank()) {
                        Text(
                            text = profile.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (profile.intro.isNotBlank()) {
                        Text(text = profile.intro, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (profile.goal.isNotBlank()) {
                        Text(
                            text = profile.goal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "What I Do") {
                    Text(text = profile.whatIDo, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                SectionCard(title = "My Approach") {
                    Text(text = profile.approach, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                SectionCard(title = "Explore My Work") {
                    LinkRow(label = "Projects / Portfolio", value = profile.portfolio) {
                        openUrl(context, profile.portfolio)
                    }
                    LinkRow(label = "GitHub", value = profile.github) {
                        openUrl(context, profile.github)
                    }
                    LinkRow(label = "LinkedIn", value = profile.linkedin) {
                        openUrl(context, profile.linkedin)
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_education_heading)) {
                    if (profile.degree.isNotBlank()) {
                        Text(
                            text = profile.degree,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (profile.institution.isNotBlank()) {
                        Text(text = profile.institution, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_contact_heading)) {
                    if (profile.contactNote.isNotBlank()) {
                        Text(
                            text = profile.contactNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinkRow(label = stringResource(R.string.about_email), value = profile.email) {
                        openEmail(context, profile.email)
                    }
                    LinkRow(label = "LinkedIn", value = profile.linkedin) {
                        openUrl(context, profile.linkedin)
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_project)) {
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    KeyValueRow(
                        label = stringResource(R.string.result_version_app),
                        value = BuildConfig.VERSION_NAME,
                    )
                    KeyValueRow(
                        label = stringResource(R.string.result_version_model),
                        value = BuildConfig.MODEL_VERSION,
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

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun openEmail(context: Context, email: String) {
    if (email.isBlank()) return
    runCatching {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun loadAuthorProfile(context: Context): AuthorProfile = try {
    val text = context.assets.open("author.json").bufferedReader().use { it.readText() }
    val obj = JSONObject(text)
    val links = obj.optJSONObject("links") ?: JSONObject()
    val edu = obj.optJSONObject("education") ?: JSONObject()
    AuthorProfile(
        name = obj.optString("name", "Harisudhan Baskaran"),
        title = obj.optString("title"),
        intro = obj.optString("intro"),
        goal = obj.optString("goal"),
        whatIDo = obj.optString("whatIDo"),
        approach = obj.optString("approach"),
        degree = edu.optString("degree"),
        institution = edu.optString("institution"),
        portfolio = links.optString("portfolio", obj.optString("portfolio")),
        github = links.optString("github", obj.optString("github")),
        linkedin = links.optString("linkedin", obj.optString("linkedin")),
        email = links.optString("email", obj.optString("email")),
        contactNote = obj.optString("contactNote"),
    )
} catch (_: Throwable) {
    AuthorProfile()
}
