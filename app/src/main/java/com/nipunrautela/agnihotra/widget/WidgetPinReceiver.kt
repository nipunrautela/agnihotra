package com.nipunrautela.agnihotra.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.nipunrautela.agnihotra.analytics.UsageEvent
import com.nipunrautela.agnihotra.appGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The explicit, immutable pin callback needs no launcher-supplied extras. */
class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.appGraph.scope.launch {
            try {
                AgnihotraWidget().updateAll(context)
                context.appGraph.analytics.record(UsageEvent.WIDGET_PIN_COMPLETED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Normal app refresh/provider updates can retry rendering. */ }
            finally { pending.finish() }
        }
    }
}
