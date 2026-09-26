package com.fitflow.training.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.fitflow.training.catalog.CatalogScreen
import com.fitflow.training.checkin.CheckInScreen
import com.fitflow.training.plan.PlanScreen
import com.fitflow.training.preset.PresetEditScreen
import com.fitflow.training.preset.PresetListScreen
import com.fitflow.training.session.SessionScreen
import com.fitflow.training.settings.SettingsScreen
import com.fitflow.training.stretch.FinishScreen

object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val PLAN = "plan"
    const val SESSION = "session"
    const val FINISH = "finish"
    const val CHECKIN = "checkin"
    const val SETTINGS = "settings"
    const val PRESETS = "presets"
    const val PRESET_EDIT = "preset_edit"
}

private data class NavState(
    val route: String,
    val exerciseId: String?,
    val presetSlot: Int?,
    val catalogPresetSlot: Int?,
)

private const val NAV_SEPARATOR = "\u001F"
private fun NavState.encode(): String = listOf(
    route,
    exerciseId.orEmpty(),
    presetSlot?.toString().orEmpty(),
    catalogPresetSlot?.toString().orEmpty(),
).joinToString(NAV_SEPARATOR)
private fun String.decodeNavState(): NavState {
    val parts = split(NAV_SEPARATOR)
    return NavState(
        route = parts[0],
        exerciseId = parts.getOrNull(1)?.ifEmpty { null },
        presetSlot = parts.getOrNull(2)?.toIntOrNull(),
        catalogPresetSlot = parts.getOrNull(3)?.toIntOrNull(),
    )
}

@Composable
fun AppNav() {
    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    var selectedExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPresetSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var catalogPresetSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var backStack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    fun navigate(
        destination: String,
        exerciseId: String? = null,
        presetSlot: Int? = null,
        catalogForPreset: Int? = null,
    ) {
        backStack = backStack + NavState(route, selectedExerciseId, selectedPresetSlot, catalogPresetSlot).encode()
        route = destination
        selectedExerciseId = exerciseId
        selectedPresetSlot = presetSlot
        catalogPresetSlot = catalogForPreset
    }
    fun back() {
        if (backStack.isNotEmpty()) {
            val previous = backStack.last().decodeNavState()
            backStack = backStack.dropLast(1)
            route = previous.route
            selectedExerciseId = previous.exerciseId
            selectedPresetSlot = previous.presetSlot
            catalogPresetSlot = previous.catalogPresetSlot
        } else route = Routes.HOME
    }
    BackHandler(route != Routes.HOME) { back() }
    when (route) {
        Routes.HOME -> HomeScreen(
            onStrengthClick = { navigate(Routes.CATALOG) },
            onResumeClick = { navigate(Routes.SESSION) },
            onOpenFinish = { navigate(Routes.FINISH) },
            onCheckinsClick = { navigate(Routes.CHECKIN) },
            onSettingsClick = { navigate(Routes.SETTINGS) },
        )
        Routes.CATALOG -> CatalogScreen(
            onAdd = { exercise ->
                val slot = catalogPresetSlot
                if (slot == null) navigate(Routes.PLAN, exercise.id) else {
                    if (backStack.lastOrNull()?.decodeNavState()?.route == Routes.PRESET_EDIT) backStack = backStack.dropLast(1)
                    route = Routes.PRESET_EDIT
                    selectedExerciseId = exercise.id
                    selectedPresetSlot = slot
                    catalogPresetSlot = null
                }
            },
            onBack = ::back,
            onOpenPlan = { navigate(Routes.PLAN) },
            onOpenPresets = { navigate(Routes.PRESETS) },
        )
        Routes.PLAN -> PlanScreen(
            onBack = ::back,
            onStart = { navigate(Routes.SESSION) },
            onAddCatalog = { navigate(Routes.CATALOG) },
            selectedExerciseId = selectedExerciseId,
        )
        Routes.SESSION -> SessionScreen(onBack = ::back, onFinish = { navigate(Routes.FINISH) })
        Routes.FINISH -> FinishScreen(onCheckIn = { navigate(Routes.CHECKIN) }, onBack = ::back)
        Routes.CHECKIN -> CheckInScreen(onBack = ::back)
        Routes.SETTINGS -> SettingsScreen(onBack = ::back)
        Routes.PRESETS -> PresetListScreen(
            onBack = ::back,
            onEditSlot = { navigate(Routes.PRESET_EDIT, presetSlot = it) },
        )
        Routes.PRESET_EDIT -> PresetEditScreen(
            slot = requireNotNull(selectedPresetSlot),
            selectedExerciseId = selectedExerciseId,
            onBack = ::back,
            onAddCatalog = {
                val slot = requireNotNull(selectedPresetSlot)
                navigate(Routes.CATALOG, presetSlot = slot, catalogForPreset = slot)
            },
            onExerciseConsumed = { selectedExerciseId = null },
        )
    }
}
