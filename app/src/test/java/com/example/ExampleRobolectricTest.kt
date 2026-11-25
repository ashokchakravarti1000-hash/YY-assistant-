package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.CompatibilityStatus
import com.example.core.common.DeviceTier
import com.example.core.device.DeviceProfiler
import com.example.core.storage.CatalogLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and curated 10-model catalog are valid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("YY Assistant", context.getString(R.string.app_name))

        val catalog = CatalogLoader(context).loadCatalog()
        assertEquals(10, catalog.models.size)
        assertTrue(catalog.voicePacks.size >= 8)

        // Verify Models 9 & 10 enforce 16 GB RAM gate
        val gated16Gb = catalog.models.filter { it.requires16GbGate }
        assertEquals(2, gated16Gb.size)

        // Verify vision models (3, 4, 7) are tagged vision-capable
        val visionModels = catalog.models.filter { it.isVisionCapable }
        assertEquals(3, visionModels.size)

        // Verify 16 GB gate blocks 30B model on an 8 GB Tier 1 profile
        val profiler = DeviceProfiler(context)
        val tier1Profile = profiler.profile.value.copy(
            totalRamBytes = 8L * 1_073_741_824L,
            memoryBudgetBytes = (8L * 1_073_741_824L * 0.60).toLong(),
            effectiveTier = DeviceTier.TIER_1_LITE
        )
        val qwen30b = catalog.models.first { it.id == "qwen3-30b-a3b-q4km" }
        assertEquals(CompatibilityStatus.BLOCKED, profiler.checkModelCompatibility(qwen30b, tier1Profile))
    }
}
