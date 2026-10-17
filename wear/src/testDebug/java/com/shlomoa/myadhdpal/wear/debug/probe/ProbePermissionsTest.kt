package com.shlomoa.myadhdpal.wear.debug.probe

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which permissions the probe needs on each Wear OS level, and the lines it prints (names only, never values). */
class ProbePermissionsTest {
    private val health = listOf(
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    )
    private val bodySensors = listOf(
        "android.permission.BODY_SENSORS",
        "android.permission.BODY_SENSORS_BACKGROUND"
    )

    @Test
    fun `TC-W-139 Wear OS 6 and later need the health permissions, API 33 to 35 the body sensor ones`() {
        assertEquals(health, requiredPermissions(36))
        assertEquals(health, requiredPermissions(37))
        assertEquals(bodySensors, requiredPermissions(35))
        assertEquals(bodySensors, requiredPermissions(33))
        assertEquals(listOf("android.permission.BODY_SENSORS"), requiredPermissions(30))
    }

    @Test
    fun `TC-W-140 the permission report names every missing permission and says when all are granted`() {
        val granted = setOf("android.permission.health.READ_HEART_RATE")
        assertEquals(
            listOf(
                "PROBE permission granted: android.permission.health.READ_HEART_RATE",
                "PROBE permission missing: android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
            ),
            permissionReport(requiredPermissions(36)) { it in granted }
        )
        assertEquals(
            health.map { "PROBE permission granted: $it" },
            permissionReport(health) { true }
        )
    }

    @Test
    fun `TC-W-141 the probe may start only when no permission is missing`() {
        assertEquals(emptyList<String>(), missingPermissions(health) { true })
        assertEquals(health, missingPermissions(health) { false })
    }
}
