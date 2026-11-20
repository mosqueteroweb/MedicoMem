package com.medicomem.upload

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadPolicyTest {
    @Test fun mobileDataByDefaultMeansAnyConnection() =
        assertEquals(NetworkType.CONNECTED, UploadPolicy.networkType(useMobileData = true))

    @Test fun wifiOnlyMeansUnmetered() =
        assertEquals(NetworkType.UNMETERED, UploadPolicy.networkType(useMobileData = false))
}
