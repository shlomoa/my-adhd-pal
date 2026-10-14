package com.shlomoa.myadhdpal.wear

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

class ManifestTest {
    private val manifest: Document =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))

    private fun application(): Element = manifest.getElementsByTagName("application").item(0) as Element

    private fun androidAttr(element: Element, name: String): String = element.getAttributeNS(ANDROID_NS, name)

    private fun metaData(name: String): String? = (0 until application().getElementsByTagName("meta-data").length)
        .map { application().getElementsByTagName("meta-data").item(it) as Element }
        .firstOrNull { androidAttr(it, "name") == name }
        ?.let { androidAttr(it, "value") }

    private fun permissions(): Set<String> = (0 until manifest.getElementsByTagName("uses-permission").length)
        .map { androidAttr(manifest.getElementsByTagName("uses-permission").item(it) as Element, "name") }
        .toSet()

    @Test
    fun `TC-W-050 notification bridge is NO_BRIDGING`() {
        assertEquals("NO_BRIDGING", metaData("com.google.android.wearable.notificationBridgeMode"))
    }

    private fun receivers(): Set<String> = (0 until application().getElementsByTagName("receiver").length)
        .map { androidAttr(application().getElementsByTagName("receiver").item(it) as Element, "name") }
        .toSet()

    private fun permissionMaxSdk(name: String): String? =
        (0 until manifest.getElementsByTagName("uses-permission").length)
            .map { manifest.getElementsByTagName("uses-permission").item(it) as Element }
            .firstOrNull { androidAttr(it, "name") == name }
            ?.let { androidAttr(it, "maxSdkVersion") }

    @Test
    fun `TC-W-027 exact alarm and boot permissions are declared and the alarm and boot receivers are registered`() {
        assertTrue(permissions().contains("android.permission.USE_EXACT_ALARM"))
        assertTrue(permissions().contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertEquals("32", permissionMaxSdk("android.permission.SCHEDULE_EXACT_ALARM"))
        assertTrue(receivers().containsAll(listOf(".scheduler.AlarmReceiver", ".scheduler.BootReceiver")))
    }

    @Test
    fun `TC-W-051 vibrate and notification permissions are declared and backup is off`() {
        assertTrue(permissions().contains("android.permission.VIBRATE"))
        assertTrue(permissions().contains("android.permission.POST_NOTIFICATIONS"))
        assertEquals("false", androidAttr(application(), "allowBackup"))
    }
}
