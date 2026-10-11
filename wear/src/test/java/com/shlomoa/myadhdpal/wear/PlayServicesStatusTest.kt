package com.shlomoa.myadhdpal.wear

import com.google.android.gms.common.ConnectionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayServicesStatusTest {
    @Test
    fun `TC-W-052 availability reports unavailable without Google Play services`() {
        assertEquals(PlayServicesStatus.Unavailable, playServicesStatusOf(ConnectionResult.SERVICE_MISSING))
        assertEquals(PlayServicesStatus.Unavailable, playServicesStatusOf(ConnectionResult.SERVICE_DISABLED))
        assertEquals(PlayServicesStatus.Unavailable, playServicesStatusOf(ConnectionResult.SERVICE_INVALID))
    }

    @Test
    fun `TC-W-052 availability reports available when the connection result is success`() {
        assertEquals(PlayServicesStatus.Available, playServicesStatusOf(ConnectionResult.SUCCESS))
    }
}
