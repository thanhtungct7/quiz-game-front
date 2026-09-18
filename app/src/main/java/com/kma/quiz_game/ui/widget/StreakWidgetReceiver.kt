package com.kma.quiz_game.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * What the manifest points at. Nothing here but the widget it hosts -- the receiver exists because
 * the framework needs a `BroadcastReceiver` for `APPWIDGET_UPDATE`, and Glance handles the rest.
 */
class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
