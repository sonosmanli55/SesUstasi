package com.aile.sesustasi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Paket yükleyicisinin sonucunu alır: onay penceresini açar ya da hatayı arayüze iletir. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    i.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (Updater.listener != null) {
                        c.startActivity(confirm)
                    } else {
                        notifyConfirm(c, confirm)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.report(Updater.State.IDLE)
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                Updater.report(Updater.State.AVAILABLE, "Kurulum iptal edildi.")
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                Updater.report(
                    Updater.State.ERROR,
                    "İmza uyuşmuyor. Sabit imza ayarlanmadan derlenen eski sürüm kurulu olabilir; " +
                        "bir kez silip yeni APK'yı elle kurun, sonraki güncellemeler tek dokunuşla gelir."
                )
            else -> {
                val m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "bilinmeyen hata"
                Updater.report(Updater.State.ERROR, "Kurulum başarısız: $m")
            }
        }
    }

    /** Uygulama ekranda değilse onay penceresini doğrudan açmak yerine bildirimle sunar. */
    private fun notifyConfirm(c: Context, confirm: Intent) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("guncelleme", "Güncellemeler", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(c, 2, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, "guncelleme")
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Ses Ustası güncellemesi hazır")
            .setContentText("Kurmak için dokunun")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(18, n)
    }
}
