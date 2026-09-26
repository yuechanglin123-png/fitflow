package com.fitflow.training.preset

import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRulesTest {
    @Test fun draftRequiresValidSlotAndTrimmedName() {
        assertFalse(PresetRules.validateDraft(TrainingPreset(0, "胸部", emptyList())).valid)
        assertFalse(PresetRules.validateDraft(TrainingPreset(8, "胸部", emptyList())).valid)
        assertFalse(PresetRules.validateDraft(TrainingPreset(1, "   ", emptyList())).valid)
        assertFalse(PresetRules.validateDraft(TrainingPreset(1, "胸".repeat(31), emptyList())).valid)
        assertTrue(PresetRules.validateDraft(TrainingPreset(1, " 胸部训练 ", emptyList())).valid)
    }

    @Test fun emptyDraftCanBeStoredButCannotBeImported() {
        val empty = TrainingPreset(1, "胸部", emptyList())
        assertTrue(PresetRules.validateDraft(empty).valid)
        assertFalse(PresetRules.validateImport(empty).valid)
    }

    @Test fun actionWithoutASetCannotBeImported() {
        val preset = TrainingPreset(1, "胸部", listOf(PlannedExercise("e", "bench", null, emptyList())))
        assertTrue(PresetRules.validateDraft(preset).valid)
        assertFalse(PresetRules.validateImport(preset).valid)
    }

    @Test fun invalidBlockMakesDraftInvalid() {
        val invalid = PlannedBlock("b", BigDecimal.ZERO, 1, 0, 60, "")
        val preset = TrainingPreset(1, "胸部", listOf(PlannedExercise("e", "bench", null, listOf(invalid))))
        assertFalse(PresetRules.validateDraft(preset).valid)
    }
}
