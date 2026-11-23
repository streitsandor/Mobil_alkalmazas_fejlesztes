package hu.omegacode.meetingnotes

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

// Naptárszerkesztő megnyitása, kitöltése
object CalendarEventLauncher {
    fun openEditor(context: Context, event: MeetingCalendarEvent) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, event.zoneId.id)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endTimeMillis)
        }

        context.startActivity(intent)
    }
}
