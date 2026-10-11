package com.adturbo.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var view: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(1, notif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(1, notif())
        } catch (e: Exception) {}

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val pad = dp(12)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F20B0F14")); cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#2EE6C6"))
            }
            setPadding(pad, pad, pad, pad)
        }
        col.addView(TextView(this).apply {
            text = "AdTurbo"; setTextColor(Color.parseColor("#2EE6C6")); textSize = 14f
        })
        val rateLabel = TextView(this).apply { setTextColor(Color.WHITE); text = "Ads: 600 / min"; textSize = 12f }
        col.addView(rateLabel)
        col.addView(SeekBar(this).apply {
            max = 9900; progress = 500
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                    val v = p + 100
                    rateLabel.text = "Ads: $v / min"
                    Frida.post("{\"cmd\":\"rate\",\"v\":$v}")
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        col.addView(Button(this).apply {
            text = "START"; setOnClickListener { Frida.post("{\"cmd\":\"start\"}") }
        })
        col.addView(Button(this).apply {
            text = "STOP"; setOnClickListener { Frida.post("{\"cmd\":\"stop\"}") }
        })
        view = col
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            dp(150), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 20; y = 220 }
        try { wm.addView(view, lp) } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { if (::view.isInitialized) wm.removeView(view) } catch (e: Exception) {}
    }

    private fun notif(): Notification {
        val ch = "adturbo"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(ch, "AdTurbo", NotificationManager.IMPORTANCE_LOW))
        }
        return Notification.Builder(this, ch)
            .setContentTitle("AdTurbo running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }
}
