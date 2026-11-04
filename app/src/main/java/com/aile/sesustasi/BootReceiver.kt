package com.aile.sesustasi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Telefon açılınca (veya uygulama güncellenince) servisi kendiliğinden başlatır. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Store.master(context)) VolumeService.refresh(context)
    }
}
