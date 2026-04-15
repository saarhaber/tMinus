package com.saarlabs.tminus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.features.AccessibilityRepository
import com.saarlabs.tminus.features.LastTrainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Requests [Manifest.permission.POST_NOTIFICATIONS] once when any alert profile is enabled,
 * so last-train and accessibility-only users are not blocked from alerts (Android 13+).
 */
@Composable
internal fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val app = context.applicationContext

    var needsPermission by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        needsPermission =
            withContext(Dispatchers.IO) {
                val commute = CommuteRepository(app).loadProfiles().any { it.enabled }
                val lastTrain = LastTrainRepository(app).load().any { it.enabled }
                val access = AccessibilityRepository(app).load().any { it.enabled }
                commute || lastTrain || access
            }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(needsPermission) {
        if (needsPermission != true) return@LaunchedEffect
        when (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> {}
            else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
