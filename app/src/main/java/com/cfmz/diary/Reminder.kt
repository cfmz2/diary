package com.cfmz.diary

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminder {
    private const val CH = "hw"

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, "Домашние задания", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("hw", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    @SuppressLint("MissingPermission")
    fun notifyNow(c: Context, list: List<Hw>) {
        if (list.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.InboxStyle()
        list.take(6).forEach { style.addLine("${it.subject}: ${it.text}") }
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Невыполненное ДЗ: ${list.size}")
            .setContentText(list.first().let { "${it.subject}: ${it.text}" })
            .setStyle(style).setContentIntent(open).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(c).notify(1, n)
    }
}

class ReminderWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        HwStore.init(applicationContext)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        if (now.hour < HwStore.hour || HwStore.lastNotified == today.toString()) return Result.success()
        val pending = HwStore.items.value.filter {
            !it.done && runCatching { LocalDate.parse(it.due) <= today.plusDays(1) }.getOrDefault(true)
        }
        if (pending.isNotEmpty()) {
            Reminder.notifyNow(applicationContext, pending)
            HwStore.lastNotified = today.toString()
        }
        return Result.success()
    }
}
