package com.example.consumablelife.core.notify

import android.Manifest
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
import com.example.consumablelife.R
import com.example.consumablelife.app.MainActivity
import com.example.consumablelife.core.data.ConsumableState
import com.example.consumablelife.core.data.SettingsDataStore
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.ui.itemName
import com.example.consumablelife.ui.verb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-N01: GOOD→CAUTION, →DUE, →OVERDUE, 센서 경고 발생 시 1회만 알림. 같은 상태 반복 알림 금지.
 * 앱이 foreground에서 상태 변화를 감지했을 때만 호출됨 (백그라운드 상주 서비스 없음, NFR-04). 광고 금지·1줄 문구.
 */
@Singleton
class StatusNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val store: SettingsDataStore,
) {
    private val lock = Mutex()

    suspend fun onStates(states: List<ConsumableState>) = lock.withLock {
        val previous = store.notifiedStatuses()
        val next = mutableMapOf<String, String>()
        states.forEach { s ->
            val key = s.item.id
            val cur = encode(s)
            next[key] = cur
            val prev = previous[key] ?: return@forEach // 최초 관측은 기준점만 저장 (알림 없음)
            if (escalated(prev, cur)) notify(s)
        }
        if (next != previous) store.setNotifiedStatuses(next)
    }

    private fun encode(s: ConsumableState) = "${s.result.status.name}${if (s.sensorAlert != null) "+S" else ""}"

    private fun rank(status: String): Int = when (status.substringBefore('+')) {
        Status.CAUTION.name -> 1
        Status.DUE.name -> 2
        Status.OVERDUE.name -> 3
        else -> 0
    }

    private fun escalated(prev: String, cur: String): Boolean {
        val sensorNew = cur.endsWith("+S") && !prev.endsWith("+S")
        return sensorNew || rank(cur) > rank(prev)
    }

    private fun notify(s: ConsumableState) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val name = itemName(ctx, s.item)
        val v = verb(ctx, s.kind) // DG2-I1: INSPECT 품목은 "점검"
        val text = when {
            s.sensorAlert != null -> ctx.getString(R.string.notif_sensor, name)
            s.result.status == Status.OVERDUE -> ctx.getString(R.string.notif_overdue, name, v)
            s.result.status == Status.DUE -> ctx.getString(R.string.notif_due, name, v)
            else -> ctx.getString(R.string.notif_caution, name, v)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(s.item.id.hashCode(), n) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    companion object { const val CHANNEL = "status_change" }
}
