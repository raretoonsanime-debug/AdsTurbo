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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private val root = RootShell()

    private var adsPerMin = 600
    private var injectDelay = 3
    private var armMode = false

    private val TEAL = Color.parseColor("#2EE6C6")
    private val BG = Color.parseColor("#0B0F14")
    private val PANEL = Color.parseColor("#0F141B")
    private val CARD = Color.parseColor("#161D26")
    private val TEXT = Color.parseColor("#EAF2F0")
    private val MUTED = Color.parseColor("#7C8A97")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Frida.logListener = { line -> runOnUiThread {
            logView.append("$line\n")
            statusView.text = line
        } }
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
            setPadding(dp(6), dp(22), dp(6), dp(22))
        }
        side.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        side.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(22)) })
        listOf("Home", "Games", "Mods", "Logs", "Settings").forEach { side.addView(navItem(it)) }
        row.addView(side, LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = FrameLayout(this)
        col.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        statusView = TextView(this).apply {
            setTextColor(TEAL); typeface = Typeface.MONOSPACE; textSize = 10f
            setBackgroundColor(Color.parseColor("#05070A"))
            setPadding(dp(12), dp(7), dp(12), dp(7))
            maxLines = 1; ellipsize = TextUtils.TruncateAt.START
            text = "ready"
        }
        col.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return row
    }

    private fun navItem(name: String) = TextView(this).apply {
        text = name; setTextColor(MUTED); textSize = 13f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(13), dp(12), dp(13))
        setOnClickListener { screen(name) }
    }

    private fun screen(name: String) {
        content.removeAllViews()
        val v = when (name) {
            "Home" -> homeScreen()
            "Games" -> gamesScreen()
            "Mods" -> modsScreen()
            "Logs" -> logsScreen()
            else -> settingsScreen()
        }
        content.addView(v)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; setTextColor(TEXT); textSize = 22f
        setPadding(0, 0, 0, dp(12))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 16)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    private fun pill(label: String, bg: Int, fg: Int, onTap: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(fg); textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(9), dp(16), dp(9))
        background = rounded(bg, 20)
        isClickable = true
        setOnClickListener { onTap() }
    }

    private fun homeScreen(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        v.addView(TextView(this).apply { text = "Welcome back,"; setTextColor(MUTED); textSize = 14f })
        v.addView(TextView(this).apply {
            text = "Player"; setTextColor(TEXT); textSize = 26f
            setPadding(0, 0, 0, dp(18))
        })

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("Ads / min", "$adsPerMin"), weight())
        stats.addView(statCard("Mode", if (armMode) "ARM" else "x86"), weight())
        stats.addView(statCard("Root", "OK"), weight())
        v.addView(stats)

        v.addView(space(16))
        v.addView(linkCard("Games", "Pick a game and inject", "Games"))
        v.addView(linkCard("Mods", "Ads Bypass #1 / #2", "Mods"))
        v.addView(linkCard("Logs", "Live injection console", "Logs"))
        return wrap(v)
    }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .apply { rightMargin = dp(10) }

    private fun statCard(label: String, value: String): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 14)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        c.addView(TextView(this).apply { text = label; setTextColor(MUTED); textSize = 11f })
        c.addView(TextView(this).apply { text = value; setTextColor(TEAL); textSize = 20f })
        return c
    }

    private fun linkCard(t: String, sub: String, target: String): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(CARD, 16)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            isClickable = true
            setOnClickListener { screen(target) }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = t; setTextColor(TEXT); textSize = 16f })
        col.addView(TextView(this).apply { text = sub; setTextColor(MUTED); textSize = 12f })
        c.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(TextView(this).apply { text = ">"; setTextColor(TEAL); textSize = 18f })
        return c
    }

    private fun gamesScreen(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        v.addView(title("Games"))

        val search = EditText(this).apply { hint = "Search apps" }
        v.addView(search)

        val manual = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val pkgField = EditText(this).apply { hint = "or type package name" }
        manual.addView(pkgField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        manual.addView(pill("Inject", TEAL, BG) { inject(pkgField.text.toString().trim()) })
        v.addView(manual)

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

    private fun appRow(ai: ApplicationInfo): View {
        val pm = packageManager
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(CARD, 14)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(pm.getApplicationIcon(ai))
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        })
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        mid.addView(TextView(this).apply {
            text = pm.getApplicationLabel(ai); setTextColor(TEXT); textSize = 14f
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        mid.addView(TextView(this).apply {
            text = ai.packageName; setTextColor(MUTED); textSize = 10f
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pill("Inject", TEAL, BG) { inject(ai.packageName) })
        return row
    }

    private fun modsScreen(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        v.addView(title("Mods"))

        val rateCard = card()
        rateCard.addView(TextView(this).apply { text = "Ads / min"; setTextColor(TEXT); textSize = 15f })
        val valLbl = TextView(this).apply { text = "$adsPerMin"; setTextColor(TEAL); textSize = 20f }
        rateCard.addView(valLbl)
        rateCard.addView(SeekBar(this).apply {
            max = 9900; progress = adsPerMin - 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                    adsPerMin = p + 100
                    valLbl.text = "$adsPerMin"
                    Frida.post("{\"cmd\":\"rate\",\"v\":$adsPerMin}")
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        v.addView(rateCard)

        listOf("Ads Bypass #1", "Ads Bypass #2").forEachIndexed { i, t ->
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(CARD, 16)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }
            c.addView(TextView(this).apply { text = t; setTextColor(TEXT); textSize = 16f },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(pill("Send", TEAL, BG) {
                Frida.post("{\"cmd\":\"rate\",\"v\":$adsPerMin}")
                Frida.post("{\"cmd\":\"mod\",\"n\":${i + 1}}")
                toast("Sent $t")
            })
            v.addView(c)
        }
        return v
    }

    private fun logsScreen(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        v.addView(title("Logs"))
        logView = TextView(this).apply {
            setTextColor(TEAL); typeface = Typeface.MONOSPACE; textSize = 11f
            setBackgroundColor(Color.parseColor("#05070A"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        v.addView(ScrollView(this).apply { addView(logView) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return v
    }

    private fun settingsScreen(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        v.addView(title("Settings"))

        val d = card()
        d.addView(TextView(this).apply { text = "Injection delay"; setTextColor(MUTED); textSize = 12f })
        val dl = TextView(this).apply { text = "${injectDelay}s"; setTextColor(TEAL); textSize = 22f }
        d.addView(dl)
        val dRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dRow.addView(pill("-", CARD, TEXT) {
            if (injectDelay > 0) injectDelay--; dl.text = "${injectDelay}s"
        })
        dRow.addView(space(10))
        dRow.addView(pill("+", CARD, TEXT) {
            if (injectDelay < 30) injectDelay++; dl.text = "${injectDelay}s"
        })
        d.addView(dRow)
        v.addView(d)

        val a = card()
        a.addView(TextView(this).apply { text = "ARM translator (emulators)"; setTextColor(TEXT); textSize = 15f })
        val sw = Switch(this).apply { isChecked = armMode }
        sw.setOnCheckedChangeListener { _, b -> armMode = b }
        a.addView(sw)
        v.addView(a)

        val i = card()
        i.addView(TextView(this).apply { text = "AdTurbo"; setTextColor(TEXT); textSize = 15f })
        i.addView(TextView(this).apply {
            text = "Frida-based ad bypass. Root required."; setTextColor(MUTED); textSize = 12f
        })
        v.addView(i)
        return v
    }

    private fun wrap(v: View) = ScrollView(this).apply { addView(v) }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun abiOf(ai: ApplicationInfo): String {
        return try {
            (ApplicationInfo::class.java.getField("primaryCpuAbi").get(ai) as? String) ?: ""
        } catch (e: Exception) { "" }
    }

    private fun inject(pkg: String) {
        if (pkg.isEmpty()) { toast("enter a package"); return }
        if (::logView.isInitialized) logView.append(">> inject $pkg\n")
        val arm = armMode
        val abi = if (arm) "arm64-v8a" else "x86_64"
        try {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
            }
        } catch (e: Exception) {}
        content.postDelayed({
            try {
                val server = installServer(abi)
                root.run("$server &")
                val agent = assets.open("agent.js").bufferedReader().readText()
                Frida.attach(pkg, agent)
                Frida.post("{\"cmd\":\"rate\",\"v\":$adsPerMin}")
                startService(Intent(this, OverlayService::class.java))
                toast("injected $pkg")
            } catch (e: Exception) { toast("ERROR: ${e.message}") }
        }, (injectDelay * 1000).toLong())
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
