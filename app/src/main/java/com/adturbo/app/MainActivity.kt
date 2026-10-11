package com.adturbo.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var logView: TextView
    private val root = RootShell()

    private var adsPerMin = 600
    private val TEAL = Color.parseColor("#2EE6C6")
    private val BG = Color.parseColor("#0B0F14")
    private val PANEL = Color.parseColor("#10161D")
    private val CARD = Color.parseColor("#1A222C")
    private val TEXT = Color.parseColor("#EAF2F0")
    private val MUTED = Color.parseColor("#8A97A3")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Frida.logListener = { line -> runOnUiThread { logView.append("$line\n") } }
        setContentView(build())
        askOverlay()
        screen("Home")
    }

    private fun build(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BG)
        }

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PANEL)
            setPadding(dp(6), dp(18), dp(6), dp(18))
        }
        side.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        side.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(16))
        })
        listOf("Home", "Hits", "Mods", "Done", "Spin", "Procs", "Come").forEach {
            side.addView(navItem(it))
        }
        row.addView(side, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = "Dashboard"; setTextColor(TEXT); textSize = 22f
            setPadding(dp(16), dp(16), dp(16), dp(6))
        })
        col.addView(rateRow())

        content = FrameLayout(this)
        col.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        logView = TextView(this).apply {
            setTextColor(TEAL); typeface = Typeface.MONOSPACE; textSize = 11f
            setBackgroundColor(Color.parseColor("#05070A"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        col.addView(logView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)))

        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return row
    }

    private fun navItem(name: String) = TextView(this).apply {
        text = name; setTextColor(MUTED); textSize = 12f
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
        setOnClickListener { screen(name) }
    }

    private fun rateRow(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        wrap.addView(TextView(this).apply {
            text = "Ads / min"; setTextColor(TEXT); textSize = 15f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val bar = SeekBar(this).apply {
            max = 9900; progress = adsPerMin - 100
        }
        val value = TextView(this).apply {
            text = "$adsPerMin"; setTextColor(BG); textSize = 13f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(TEAL, 20)
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                adsPerMin = p + 100
                value.text = "$adsPerMin"
                Frida.post("{\"cmd\":\"rate\",\"v\":$adsPerMin}")
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        wrap.addView(bar, LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT))
        wrap.addView(value)
        return wrap
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun screen(name: String) {
        content.removeAllViews()
        when (name) {
            "Home" -> content.addView(libraryScreen())
            "Mods", "Hits", "Spin", "Done" -> content.addView(modsScreen())
            else -> content.addView(placeholder(name))
        }
    }

    private fun libraryScreen(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        v.addView(TextView(this).apply {
            text = "App Library"; setTextColor(TEXT); textSize = 17f
            setPadding(0, dp(4), 0, dp(6))
        })
        val search = EditText(this).apply { hint = "Search apps or packages" }
        v.addView(search)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        v.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        fun render(q: String) {
            list.removeAllViews()
            apps.filter {
                q.isEmpty() ||
                    pm.getApplicationLabel(it).toString().lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q)
            }.forEach { list.addView(appRow(it)) }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { render(s.toString().lowercase()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        render("")
        return v
    }

    private fun abiOf(ai: ApplicationInfo): String {
        return try {
            (ApplicationInfo::class.java.getField("primaryCpuAbi").get(ai) as? String) ?: ""
        } catch (e: Exception) { "" }
    }

    private fun appRow(ai: ApplicationInfo): View {
        val pm = packageManager
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(CARD, 14)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(pm.getApplicationIcon(ai))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        mid.addView(TextView(this).apply {
            text = pm.getApplicationLabel(ai); setTextColor(TEXT); textSize = 14f
        })
        mid.addView(TextView(this).apply {
            text = ai.packageName; setTextColor(MUTED); textSize = 11f
        })
        val abi = abiOf(ai)
        mid.addView(TextView(this).apply {
            text = if (abi.contains("arm")) "arm64-v8a" else abi
            setTextColor(TEAL); textSize = 11f
        })
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Launch & Inject"
            setOnClickListener { launchAndInject(ai.packageName, abi.contains("arm")) }
        })
        return row
    }

    private fun modsScreen(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        v.addView(TextView(this).apply {
            text = "Mods, Profile"; setTextColor(TEXT); textSize = 17f
            setPadding(0, dp(4), 0, dp(8))
        })
        listOf("Ads Bypass #1", "Ads Bypass #2").forEachIndexed { i, title ->
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(CARD, 16)
                setPadding(dp(14), dp(16), dp(14), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }
            c.addView(TextView(this).apply {
                text = title; setTextColor(TEXT); textSize = 16f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(Button(this).apply {
                text = "Send"
                background = rounded(TEAL, 22)
                setTextColor(BG)
                setOnClickListener {
                    Frida.post("{\"cmd\":\"rate\",\"v\":$adsPerMin}")
                    Frida.post("{\"cmd\":\"mod\",\"n\":${i + 1}}")
                    toast("Sent $title @ $adsPerMin/min")
                }
            })
            v.addView(c)
        }
        return v
    }

    private fun placeholder(name: String) = TextView(this).apply {
        text = "$name - coming up"; setTextColor(MUTED)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun launchAndInject(pkg: String, arm: Boolean) {
        try {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
            }
        } catch (e: Exception) {}
        content.postDelayed({ inject(pkg, arm) }, 2500)
    }

    private fun inject(pkg: String, arm: Boolean) {
        val abi = if (arm) "arm64-v8a" else "x86_64"
        toast("Injecting $pkg ($abi)")
        try {
            val server = installServer(abi)
            root.run("$server &")
            val agent = assets.open("agent.js").bufferedReader().readText()
            Frida.attach(pkg, agent)
            Frida.post("{\"cmd\":\"rate\",\"v\":$adsPerMin}")
            startService(Intent(this, OverlayService::class.java))
        } catch (e: Exception) { toast("ERROR: ${e.message}") }
    }

    private fun installServer(abi: String): String {
        val name = if (abi == "arm64-v8a") "frida-server-arm64" else "frida-server-x86_64"
        val out = File(filesDir, name)
        if (!out.exists() || out.length() == 0L) {
            assets.open(name).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
        root.run("chmod 755 ${out.absolutePath}")
        return out.absolutePath
    }

    private fun askOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
    }

    private fun toast(m: String) = runOnUiThread {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    }
}
