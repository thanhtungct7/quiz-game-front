package com.kma.quiz_game.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kma.quiz_game.MainActivity
import com.kma.quiz_game.R
import com.kma.quiz_game.data.local.WidgetStateStore
import com.kma.quiz_game.data.push.PushRoute

/** 2x2, the size the widget is offered at: the streak, one line, and the mascot. */
private val COMPACT = DpSize(140.dp, 140.dp)

/** 4x2. The extra width is spent on the level badge and the EXP bar, not on bigger type. */
private val WIDE = DpSize(250.dp, 140.dp)

private const val CARD_RADIUS_DP = 24

/**
 * The home-screen streak card.
 *
 * Reads a snapshot the app wrote ([com.kma.quiz_game.data.widget.WidgetSync] ->
 * [WidgetStateStore]) and never talks to the server: an update pass can happen with no session,
 * no network and the app not running, and the home screen is the last place that should show a
 * spinner.
 *
 * The clock is read once per pass rather than observed. Everything time-dependent lives in
 * [streakWidgetUi], and `updatePeriodMillis` in `res/xml/streak_widget_info.xml` is what brings
 * the next pass around when only the hour has changed.
 */
class StreakWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Built on the context handed in rather than pulled off the Application: the launcher can
        // start this process for an update alone, and the widget must not depend on anything the
        // app sets up on its way to a screen.
        val snapshot = WidgetStateStore(context.applicationContext).current()
        val ui = streakWidgetUi(snapshot, System.currentTimeMillis())
        provideContent { StreakCard(ui) }
    }
}

/**
 * Two layers over the gradient: the mascot pinned to the bottom edge, where the card crops it the
 * way the app's own dialogs crop it, and the text stacked from the top. Two boxes rather than one
 * column because Glance has no proportional weights to push the mascot down with.
 */
@Composable
private fun StreakCard(ui: StreakWidgetUi) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= WIDE.width
    // Tied to the card's height rather than fixed: a learner who resizes the widget down to one
    // row must not end up with the mascot printed over the message.
    val mascotSize = (size.height * 0.42f).coerceIn(44.dp, 80.dp)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(CARD_RADIUS_DP.dp)
            // The whole card, not a button: a widget is a shortcut first, and the tap lands on
            // the learn path through the same extra a push notification uses.
            .clickable(actionStartActivity(learnIntent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(backgroundFor(ui.mood)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Image(
                provider = ImageProvider(mascotFor(ui.mood)),
                contentDescription = null,
                modifier = GlanceModifier.size(mascotSize),
            )
        }
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ui.showStreak) {
                Text(
                    text = "🔥 ${ui.streak}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = if (wide) 30.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            Text(
                text = ui.message,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = if (wide) 15.sp else 13.sp,
                    fontWeight = if (ui.showStreak) FontWeight.Medium else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
            if (wide && ui.level > 0) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                LevelStrip(ui)
            }
        }
    }
}

/**
 * `Lv.34 · B1` over the EXP bar -- the same two figures the learn header and the RPG card show, so
 * the home screen cannot disagree with the app about what level the learner is.
 */
@Composable
private fun LevelStrip(ui: StreakWidgetUi) {
    Text(
        text = if (ui.cefr.isBlank()) "Lv.${ui.level}" else "Lv.${ui.level} · ${ui.cefr}",
        style = TextStyle(
            color = ColorProvider(Color.White),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
    Spacer(modifier = GlanceModifier.height(4.dp))
    LinearProgressIndicator(
        ui.levelFraction,
        GlanceModifier.fillMaxWidth().height(6.dp),
        ColorProvider(Color.White),
        ColorProvider(Color(0x40FFFFFF)),
    )
}

/** Where a tap goes. `MainActivity` is `singleTask` and already routes this extra, from pushes. */
private fun learnIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(PushRoute.EXTRA_KEY, PushRoute.LEARN.wireName)
    }

private fun backgroundFor(mood: StreakMood): Int = when (mood) {
    StreakMood.SIGNED_OUT, StreakMood.FRESH_START -> R.drawable.widget_bg_green
    StreakMood.MORNING -> R.drawable.widget_bg_pink
    StreakMood.AT_RISK -> R.drawable.widget_bg_red
    StreakMood.DONE_TODAY, StreakMood.DAYTIME, StreakMood.UNVERIFIED -> R.drawable.widget_bg_purple
}

/** The sad mascot is kept for the one state that has actually gone wrong; using it for every
 * reminder would make it mean nothing. */
private fun mascotFor(mood: StreakMood): Int =
    if (mood == StreakMood.AT_RISK) R.drawable.mascot_sad else R.drawable.mascot
