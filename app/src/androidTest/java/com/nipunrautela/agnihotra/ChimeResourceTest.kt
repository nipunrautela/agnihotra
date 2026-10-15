package com.nipunrautela.agnihotra

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import com.nipunrautela.agnihotra.audio.ReminderChime
import org.junit.Assert.*
import org.junit.Test

class ChimeResourceTest {
    @Test fun notificationResourceUriDecodesAsAShortOfflineChime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(context, ReminderChime.uri(context))
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Chime should last about 2.8 seconds", duration in 2_600..3_000)
        } finally { metadata.release() }
    }
}
