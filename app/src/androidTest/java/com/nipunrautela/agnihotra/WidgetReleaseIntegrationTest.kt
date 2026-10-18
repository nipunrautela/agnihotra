package com.nipunrautela.agnihotra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Run with tools/verify_device.ps1 -Release: debug builds cannot detect stripped constructors. */
class WidgetReleaseIntegrationTest {
    @Test fun workManagerCanReflectivelyCreateGlanceInputMergersAfterShrinking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (name in listOf("androidx.work.OverwritingInputMerger", "androidx.work.ArrayCreatingInputMerger")) {
            val merger = context.classLoader.loadClass(name).getDeclaredConstructor().newInstance()
            assertNotNull(merger)
        }
        assertNotNull(context.classLoader.loadClass("androidx.glance.session.SessionWorker")
            .getDeclaredConstructor(Context::class.java, context.classLoader.loadClass("androidx.work.WorkerParameters")))
    }
}
