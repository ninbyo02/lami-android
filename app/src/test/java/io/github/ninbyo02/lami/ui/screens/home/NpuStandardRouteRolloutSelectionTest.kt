package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.NpuStandardRouteSelectionSource
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class NpuStandardRouteRolloutSelectionTest {
    @Test
    fun `NPU Experimental maps to completed phase8 when dev gate is enabled`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
            },
        )

        assertEquals(true, selection.rolloutGateEnabled)
        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING, selection.selectionMode)
        assertEquals("NPU Experimental", selection.userFacingBackend)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_8, selection.completedPhaseDefault)
        assertEquals(true, selection.completedRouteSelected)
        assertEquals(false, selection.developerPhaseOverride)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_8, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.FULL, selection.effectiveMode)
        assertEquals(NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_NONE, selection.completedRouteBlockReason)
    }

    @Test
    fun `explicit phase property wins over completed route default`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> NPU_STANDARD_ROUTE_PHASE_5
                    else -> null
                }
            },
        )

        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE, selection.selectionMode)
        assertEquals(true, selection.developerPhaseOverride)
        assertEquals(false, selection.completedRouteSelected)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_DEBUG_PROPERTY, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_5, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.FULL, selection.effectiveMode)
    }

    @Test
    fun `NPU Experimental maps to completed phase8 when dev gate is disabled`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { null },
        )

        assertEquals(true, selection.rolloutGateEnabled)
        assertEquals(false, selection.devGateEnabled)
        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING, selection.selectionMode)
        assertEquals(true, selection.completedRouteSelected)
        assertEquals(false, selection.devGateRequired)
        assertEquals(false, selection.developerPhaseOverride)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_8, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.FULL, selection.effectiveMode)
        assertEquals(
            NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_NONE,
            selection.completedRouteBlockReason,
        )
    }

    @Test
    fun `completed route kill switch blocks NPU Experimental without changing local backends`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                if (key == NPU_STANDARD_ROUTE_COMPLETED_ROUTE_DISABLED_PROPERTY) "true" else null
            },
        )

        assertEquals(false, selection.devGateEnabled)
        assertEquals(false, selection.rolloutGateEnabled)
        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING, selection.selectionMode)
        assertEquals(false, selection.completedRouteSelected)
        assertEquals(true, selection.completedRouteKillSwitchEnabled)
        assertEquals(true, selection.completedRouteDisabledByProperty)
        assertEquals(
            NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED,
            selection.completedRouteBlockReason,
        )
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_DISABLED_OR_SAFE_DEFAULT, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_1, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.OFF, selection.effectiveMode)
    }

    @Test
    fun `developer phase source keeps legacy phase mode`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.S3_MARKDOWN,
            selectionSource = NpuStandardRouteSelectionSource.DEVELOPER_PHASE_OVERRIDE,
            propertyReader = { key ->
                if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
            },
        )

        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE, selection.selectionMode)
        assertEquals(true, selection.developerPhaseOverride)
        assertEquals(false, selection.completedRouteSelected)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_DEVELOPER_PHASE_SELECTION, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_1, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.S3_MARKDOWN, selection.effectiveMode)
    }

    @Test
    fun `CPU and GPU are not converted to NPU rollout route`() {
        listOf(
            PreferredBackendDryRunSetting.CPU to NpuStandardRouteMode.S1_ONLY,
            PreferredBackendDryRunSetting.GPU to NpuStandardRouteMode.FULL,
        ).forEach { (backend, mode) ->
            val selection = resolveNpuStandardRouteRolloutSelection(
                preferredBackend = backend,
                npuStandardRouteMode = mode,
                selectionSource = NpuStandardRouteSelectionSource.LOCAL_BACKEND,
                propertyReader = { key ->
                    if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
                },
            )

            assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_LOCAL_BACKEND, selection.selectionMode)
            assertEquals(false, selection.completedRouteSelected)
            assertEquals(mode, selection.effectiveMode)
        }
    }

    @Test
    fun `rollout diagnostics keys are emitted for NPU Experimental selection`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
            },
        )

        val diagnostics = selection.toDiagnosticsMap()

        assertEquals("true", diagnostics["npu_standard_route_rollout_gate_enabled"])
        assertEquals("false", diagnostics["npu_standard_route_dev_gate_required"])
        assertEquals(
            "user_facing_npu_experimental",
            diagnostics["npu_standard_route_selection_mode"],
        )
        assertEquals("8", diagnostics["npu_standard_route_completed_phase_default"])
        assertEquals("true", diagnostics["npu_standard_route_completed_route_selected"])
        assertEquals("false", diagnostics["npu_standard_route_completed_route_kill_switch_enabled"])
        assertEquals("false", diagnostics["npu_standard_route_completed_route_disabled_by_property"])
        assertEquals("none", diagnostics["npu_standard_route_developer_phase_override_block_reason"])
        assertEquals("enabled", diagnostics["npu_standard_route_completed_route_rollout_state"])
        assertEquals("completed_route_default", diagnostics["npu_standard_route_effective_phase_source"])
        assertEquals("8", diagnostics["npu_standard_route_effective_phase"])
        assertEquals("NPU Experimental", diagnostics["npu_standard_route_user_facing_selected_backend"])
        assertEquals(
            "npu_standard_route_completed",
            diagnostics["npu_standard_route_completed_route_family"],
        )
        assertEquals("NPU_S5", diagnostics["npu_standard_route_internal_legacy_backend"])
        assertEquals("npu_s5", diagnostics["npu_standard_route_internal_legacy_route_family"])
    }

    @Test
    fun `phase zero is treated as no explicit developer override`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "0"
                    else -> null
                }
            },
        )

        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING, selection.selectionMode)
        assertEquals(false, selection.developerPhaseOverride)
        assertEquals(true, selection.completedRouteSelected)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_8, selection.effectivePhase)
    }

    @Test
    fun `legacy unspecified full setting is inferred as user facing NPU Experimental when phase zero clears override`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "0"
                    else -> null
                }
            },
        )

        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING, selection.selectionMode)
        assertEquals("NPU Experimental", selection.userFacingBackend)
        assertEquals(false, selection.developerPhaseOverride)
        assertEquals(true, selection.completedRouteSelected)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_8, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.FULL, selection.effectiveMode)
        assertEquals(NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_NONE, selection.completedRouteBlockReason)
    }

    @Test
    fun `explicit phase8 remains developer override even for NPU Experimental`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> NPU_STANDARD_ROUTE_PHASE_8
                    else -> null
                }
            },
        )

        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE, selection.selectionMode)
        assertEquals(true, selection.developerPhaseOverride)
        assertEquals(false, selection.completedRouteSelected)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_DEBUG_PROPERTY, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_8, selection.effectivePhase)
    }

    @Test
    fun `explicit phase5 is blocked without dev gate and falls back to completed route default`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                if (key == NPU_STANDARD_ROUTE_PHASE_PROPERTY) NPU_STANDARD_ROUTE_PHASE_5 else null
            },
        )

        assertEquals(false, selection.devGateEnabled)
        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING, selection.selectionMode)
        assertEquals(true, selection.completedRouteSelected)
        assertEquals(false, selection.developerPhaseOverride)
        assertEquals(
            NPU_STANDARD_ROUTE_DEVELOPER_PHASE_OVERRIDE_BLOCK_DEV_GATE_DISABLED,
            selection.developerPhaseOverrideBlockReason,
        )
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_8, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.FULL, selection.effectiveMode)
    }

    @Test
    fun `developer phase source is blocked without dev gate`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.S3_MARKDOWN,
            selectionSource = NpuStandardRouteSelectionSource.DEVELOPER_PHASE_OVERRIDE,
            propertyReader = { null },
        )

        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE, selection.selectionMode)
        assertEquals(false, selection.developerPhaseOverride)
        assertEquals(false, selection.completedRouteSelected)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_DEVELOPER_PHASE_SELECTION, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_1, selection.effectivePhase)
        assertEquals(NpuStandardRouteMode.OFF, selection.effectiveMode)
    }

    @Test
    fun `explicit phase1 remains route entry diagnostic and not completed route`() {
        val selection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            selectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> NPU_STANDARD_ROUTE_PHASE_1
                    else -> null
                }
            },
        )

        assertEquals(NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE, selection.selectionMode)
        assertEquals(false, selection.completedRouteSelected)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_SOURCE_DEBUG_PROPERTY, selection.effectivePhaseSource)
        assertEquals(NPU_STANDARD_ROUTE_PHASE_1, selection.effectivePhase)
    }
}
