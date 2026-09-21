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

/** 2x1, the size the widget is offered at: streak, one line, mascot along the bottom. */
private val SHORT = DpSize(140.dp, 70.dp)

/** 2x2 after a vertical resize: the streak, one line, and a larger mascot. */
private val COMPACT = DpSize(140.dp, 140.dp)

/**
 * 2x2 where a launcher's rows are tall -- One UI gives two of its rows about 250dp, well over the
 * 140dp a square 2x2 suggests.
 *
 * Without a size class this tall the card was composed for [COMPACT] and then stretched: the copy
 * sat at the top, a mascot sized from 140dp sat at the bottom, and a third of the card in between
 * was empty gradient. The sizes here exist so the mascot and the streak figure are measured
 * against the height the card really has.
 */
private val TALL = DpSize(140.dp, 200.dp)

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

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SHORT, COMPACT, TALL, WIDE))

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
 * One column over the gradient: the copy, then the mascot on the bottom edge, where the card crops
 * it the way the app's own dialogs crop it.
 *
 * On 2x1 the copy sits at the top and the mascot at the bottom, both centered. On a taller resize
 * the slack is split by two weighted spacers so the copy stays between the top edge and the mascot
 * rather than sticking to one of them when the launcher stretches the layout past the size class
 * Glance composed for (One UI's 2x2 is about 250dp).
 */
@Composable
private fun StreakCard(ui: StreakWidgetUi) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= WIDE.width
    val tall = size.height >= TALL.height
    val short = size.height < COMPACT.height
    // Tied to the card's height rather than fixed: a learner who resizes the widget down to one
    // row must not end up with the mascot printed over the message, and one resized up should get
    // a mascot that grows into the space instead of a hole where the gradient shows through.
    val mascotSize = if (short) {
        56.dp
    } else {
        (size.height * 0.40f).coerceIn(40.dp, 92.dp)
    }
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
        Column(
            // No bottom padding: the mascot is meant to meet the edge of the card.
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 10.dp, top = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!short) {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
            if (ui.showStreak) {
                Text(
                    text = "🔥 ${ui.streak}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = when {
                            short -> 18.sp
                            tall -> 32.sp
                            wide -> 30.sp
                            else -> 26.sp
                        },
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            Text(
                text = ui.message,
                // One line on a card one row high: there is no room for a second, and a message
                // cut in half reads worse than one that ends in an ellipsis.
                maxLines = if (short) 1 else 2,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = when {
                        short -> 12.sp
                        wide || tall -> 15.sp
                        else -> 13.sp
                    },
                    fontWeight = if (ui.showStreak) FontWeight.Medium else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
            if (wide && ui.level > 0) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                LevelStrip(ui)
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(mascotFor(ui.mood)),
                contentDescription = null,
                modifier = GlanceModifier.size(mascotSize),
            )
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
