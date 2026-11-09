package com.shlomoa.myadhdpal.protocol

import org.junit.Assert.assertThrows
import org.junit.Test

class PureJvmTest {
    @Test
    fun `TC-S-005 protocol module runs on a plain JVM without Android classes`() {
        assertThrows(ClassNotFoundException::class.java) { Class.forName("android.os.Bundle") }
    }
}
