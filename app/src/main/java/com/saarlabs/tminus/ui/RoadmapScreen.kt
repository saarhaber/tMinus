package com.saarlabs.tminus.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R

private const val GITHUB_NEW_ISSUE_URL = "https://github.com/saarhaber/Tminus/issues/new"

@Composable
public fun RoadmapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.roadmap_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        FeatureSection(
            title = stringResource(R.string.roadmap_section_in_app),
            items =
                listOf(
                    R.string.roadmap_feature_trip_widget,
                    R.string.roadmap_feature_api_keys,
                    R.string.roadmap_feature_commutes,
                    R.string.roadmap_feature_last_first_train,
                    R.string.roadmap_feature_accessibility,
                    R.string.roadmap_feature_roadmap_screen,
                ),
        )
        Spacer(Modifier.height(16.dp))
        FeatureSection(
            title = stringResource(R.string.roadmap_section_planned),
            items =
                listOf(
                    R.string.roadmap_feature_notifications,
                    R.string.roadmap_feature_alerts,
                    R.string.roadmap_feature_predictions,
                    R.string.roadmap_feature_favorites,
                    R.string.roadmap_feature_more_widgets,
                ),
        )
        Spacer(Modifier.height(16.dp))
        FeatureSection(
            title = stringResource(R.string.roadmap_section_community),
            items =
                listOf(
                    R.string.roadmap_feature_contributing,
                ),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.roadmap_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.roadmap_report_issue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier =
                Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_NEW_ISSUE_URL)),
                    )
                },
        )
    }
}

@Composable
private fun FeatureSection(title: String, items: List<Int>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    items.forEach { resId ->
        Text(
            text = "• ${stringResource(resId)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
        )
    }
}
