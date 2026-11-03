package app.anchor.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.anchor.graph

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GuardService.start(context)
        WatchdogWorker.schedule(context)
    }
}

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GuardService.start(context)
        GuardBus.poke()
        AlarmScheduler.reschedule(context)
    }
}

/** NFR-07: clock and time-zone changes can be used to dodge schedules; log them. */
class TamperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val g = context.graph
        val strict = g.guard.state.value.focus?.strict == true
        val what = if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) "Time zone changed" else "Clock changed"
        g.secure.logTamper("time", if (strict) "$what during a Strict session" else what)
        GuardBus.poke()
        AlarmScheduler.reschedule(context)
    }
}
