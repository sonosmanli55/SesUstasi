package com.aile.sesustasi

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Hızlı ayarlar paneli kutucuğu: tek dokunuşla Normal <-> Özel ayar (sadece ebeveyn modunda). */
class SpecialTile : TileService() {

    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        if (Store.master(this) && !Store.childMode(this)) {
            Store.setSpecial(this, !Store.special(this))
            VolumeService.refresh(this)
        }
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        val active = Store.master(this) && Store.special(this)
        t.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = if (active) "Ses: Özel" else "Ses: Normal"
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        if (Build.VERSION.SDK_INT >= 29) {
            t.subtitle = if (Store.childMode(this)) "Çocuk modu (kilitli)" else "Ebeveyn modu"
        }
        t.updateTile()
    }
}
