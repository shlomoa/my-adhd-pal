package com.shlomoa.myadhdpal.wear

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Whether the Data Layer can be used: it needs Google Play services on the watch (plan.md section 2). */
enum class PlayServicesStatus { Available, Unavailable }

internal fun playServicesStatusOf(connectionResultCode: Int): PlayServicesStatus = if (connectionResultCode ==
    ConnectionResult.SUCCESS
) {
    PlayServicesStatus.Available
} else {
    PlayServicesStatus.Unavailable
}

fun playServicesStatus(context: Context): PlayServicesStatus =
    playServicesStatusOf(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context))
