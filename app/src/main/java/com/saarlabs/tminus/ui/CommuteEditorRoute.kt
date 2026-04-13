package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.saarlabs.tminus.commute.CommuteProfile
import com.saarlabs.tminus.commute.CommuteRepository
import kotlinx.coroutines.launch

@Composable
public fun CommuteEditorRoute(
    navController: NavController,
    profileId: String,
) {
    val context = LocalContext.current
    val repo = remember { CommuteRepository(context) }
    var initial by remember { mutableStateOf<CommuteProfile?>(null) }
    var ready by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profileId) {
        initial =
            if (profileId == "new") {
                null
            } else {
                repo.loadProfiles().find { it.id == profileId }
            }
        ready = true
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    CommuteEditorScreen(
        initial = initial,
        onSave = { profile ->
            scope.launch {
                val list = repo.loadProfiles().toMutableList()
                val idx = list.indexOfFirst { it.id == profile.id }
                if (idx >= 0) {
                    list[idx] = profile
                } else {
                    list.add(profile)
                }
                repo.saveProfiles(list)
                navController.popBackStack()
            }
        },
        onCancel = { navController.popBackStack() },
    )
}
