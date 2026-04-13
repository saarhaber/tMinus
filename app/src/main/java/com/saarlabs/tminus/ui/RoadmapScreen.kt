package com.saarlabs.tminus.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R

@Composable
public fun RoadmapScreen(modifier: Modifier = Modifier) {
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
