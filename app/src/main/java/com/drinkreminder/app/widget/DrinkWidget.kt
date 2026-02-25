package com.drinkreminder.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.room.Room
import com.drinkreminder.app.MainActivity
import com.drinkreminder.app.data.local.AppDatabase
import com.drinkreminder.app.data.local.DrinkLog
import com.drinkreminder.app.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class DrinkWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read data directly (widget runs outside Hilt)
        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "drink_reminder_db"
        ).build()

        val prefs = PreferencesManager(context.applicationContext)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val todayTotal = db.drinkLogDao().getTotalForDateOnce(startOfDay, endOfDay)
        val goalBottles = prefs.dailyGoalBottles.first()
        val bottleSize = prefs.bottleSizeMl.first()
        val goalMl = goalBottles * bottleSize
        val currentBottles = todayTotal.toFloat() / bottleSize

        db.close()

        provideContent {
            GlanceTheme {
                WidgetContent(
                    currentBottles = currentBottles,
                    goalBottles = goalBottles,
                    todayTotalMl = todayTotal,
                    goalMl = goalMl,
                    progress = if (goalMl > 0) (todayTotal.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        currentBottles: Float,
        goalBottles: Int,
        todayTotalMl: Int,
        goalMl: Int,
        progress: Float
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // Progress text (percentage)
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = GlanceTheme.colors.primary
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth()
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Bottles text
                Text(
                    text = "${"%.1f".format(currentBottles)}/$goalBottles",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GlanceTheme.colors.onBackground
                    )
                )

                Text(
                    text = "bottles",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onBackground
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Add button
                Row(
                    modifier = GlanceModifier
                        .clickable(actionRunCallback<AddBottleAction>())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+ Add Bottle",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }
            }
        }
    }
}

class AddBottleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "drink_reminder_db"
        ).build()

        val prefs = PreferencesManager(context.applicationContext)
        val bottleSize = prefs.bottleSizeMl.first()

        db.drinkLogDao().insert(DrinkLog(amountMl = bottleSize))
        db.close()

        // Update the widget
        DrinkWidget().update(context, glanceId)
    }
}
