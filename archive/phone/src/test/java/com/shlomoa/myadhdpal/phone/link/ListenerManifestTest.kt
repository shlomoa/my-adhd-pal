package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.protocol.Capabilities
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The listener service must be declared for every path the watch writes, or the process is not started when closed. */
class ListenerManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val capabilities = File("src/main/res/values/wear.xml").readText()

    @Test
    fun `the listener service is declared for the watch-written paths`() {
        assertTrue(manifest.contains(".link.PhoneLinkListenerService"))
        assertTrue(manifest.contains("com.google.android.gms.wearable.DATA_CHANGED"))
        for (prefix in listOf("/ack/", "/selection/", "/alert/")) {
            assertTrue(prefix, manifest.contains("android:pathPrefix=\"$prefix\""))
        }
        assertTrue(manifest.contains("android:path=\"/hello/watch\""))
    }

    @Test
    fun `the phone declares the phone app capability`() {
        assertTrue(capabilities.contains("android_wear_capabilities"))
        assertTrue(capabilities.contains("<item>${Capabilities.PHONE_APP}</item>"))
    }
}
