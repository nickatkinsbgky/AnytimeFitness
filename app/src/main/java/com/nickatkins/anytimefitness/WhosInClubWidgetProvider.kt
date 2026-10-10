package com.nickatkins.anytimefitness

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WhosInClubWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.whos_in_club_widget)

            // TODO: Implement actual fetch + parse of Club OS dashboard
            // 1. Load stored credentials from EncryptedSharedPreferences
            // 2. Login to https://app.club-os.com if needed (handle cookies/session)
            // 3. GET https://app.club-os.com/action/Dashboard/view
            // 4. Parse HTML for the "Who's in the club" section (use Jsoup or regex carefully)
            // 5. Format the list of names

            val placeholder = "Who's in the Club\n(Implement login + scrape)"
            views.setTextViewText(R.id.widget_text, placeholder)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
