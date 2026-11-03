package app.anchor.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.anchor.R
import app.anchor.core.policy.RuntimeState
import app.anchor.ui.MainActivity
import java.text.DateFormat
import java.util.Date

object Notifications {
    const val CHANNEL_GUARD = "guard"
    const val GUARD_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GUARD, context.getString(R.string.notification_channel_guard), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    fun guard(context: Context, state: RuntimeState, taggedCount: Int, nowMs: Long): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_anchor)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        val focus = state.focus
        val block = state.instantBlockUntilMs?.takeIf { it > nowMs }
        when {
            focus != null -> {
                val end = focus.plannedEndMs()
                b.setContentTitle(if (focus.strict) "Strict focus session" else "Focus session")
                if (end != null) {
                    b.setContentText("Ends at ${time(end)}").setWhen(end).setUsesChronometer(true).setChronometerCountDown(true)
                } else {
                    b.setContentText("Stopwatch running").setWhen(focus.startedAtMs).setUsesChronometer(true)
                }
            }
            block != null -> b.setContentTitle("Distracting apps blocked").setContentText("Until ${time(block)}")
            else -> b.setContentTitle("Anchor is on").setContentText(
                if (taggedCount == 0) "Tag apps to start protecting your time" else "Watching $taggedCount tagged apps · nothing leaves this phone",
            )
        }
        return b.build()
    }

    private fun time(ms: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
}
