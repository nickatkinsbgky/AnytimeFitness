package com.nickatkins.anytimefitness

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        private val scope = CoroutineScope(Dispatchers.IO)

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.whos_in_club_widget)
            views.setTextViewText(R.id.widget_text, "Loading...")
            appWidgetManager.updateAppWidget(appWidgetId, views)

            scope.launch {
                val (username, password) = CredentialStore.load(context)
                val text = if (username.isNullOrBlank() || password.isNullOrBlank()) {
                    "Set Club OS credentials\nin AFBGKY app"
                } else {
                    val result = ClubOsClient().fetchWhosInClub(username, password)
                    result.getOrElse { e ->
                        "Error: ${e.message?.take(60) ?: "unknown"}"
                    }
                }

                val updated = RemoteViews(context.packageName, R.layout.whos_in_club_widget)
                updated.setTextViewText(R.id.widget_text, "Who's in the Club\n$text")
                appWidgetManager.updateAppWidget(appWidgetId, updated)
            }
        }
    }
}
