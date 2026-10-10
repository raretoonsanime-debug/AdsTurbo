#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

mkdir -p app/src/main/java/com/adturbo/app app/src/main/cpp app/src/main/assets app/src/main/res/drawable .github/workflows

cat > settings.gradle <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "AdTurbo"
include ':app'
EOF

cat > build.gradle <<'EOF'
plugins {
    id 'com.android.application' version '8.5.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
}
EOF

cat > gradle.properties <<'EOF'
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m
EOF

cat > app/build.gradle <<'EOF'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
android {
    namespace 'com.adturbo.app'
    compileSdk 34
    defaultConfig {
        applicationId "com.adturbo.app"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
        ndk { abiFilters 'arm64-v8a', 'x86_64' }
        externalNativeBuild { cmake { cppFlags '-std=c++17' } }
    }
    externalNativeBuild { cmake { path file('src/main/cpp/CMakeLists.txt') } }
    compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = '17' }
    packaging { jniLibs { useLegacyPackaging true } }
}
dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
}
EOF

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

    <application
        android:label="AdTurbo"
        android:icon="@drawable/ic_logo"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar"
        android:usesCleartextTraffic="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <service android:name=".OverlayService" android:exported="false"/>
    </application>
</manifest>
EOF

cat > app/src/main/res/drawable/ic_logo.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#2EE6C6"
        android:pathData="M64,14 L34,60 L52,60 L44,94 L74,48 L56,48 Z"/>
</vector>
EOF

cat > app/src/main/java/com/adturbo/app/Frida.kt <<'EOF'
package com.adturbo.app

object Frida {
    var logListener: ((String) -> Unit)? = null

    init { System.loadLibrary("adturbo") }

    external fun attach(pkg: String, source: String)
    external fun post(json: String)
    external fun detach()

    @JvmStatic
    fun onMessage(s: String) { logListener?.invoke(s) }
}
EOF

cat > app/src/main/java/com/adturbo/app/RootShell.kt <<'EOF'
package com.adturbo.app

import java.io.BufferedReader
import java.io.InputStreamReader

class RootShell {
    fun run(cmd: String): String {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(p.inputStream)).forEachLine { sb.appendLine(it) }
        p.waitFor()
        return sb.toString()
    }
}
EOF

cat > app/src/main/java/com/adturbo/app/MainActivity.kt <<'EOF'
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
        val abi = ai.primaryCpuAbi ?: "?"
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
EOF

cat > app/src/main/java/com/adturbo/app/OverlayService.kt <<'EOF'
package com.adturbo.app

import android.app.*
import android.content.Context
import android.content.Intent
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
        startForeground(1, notif())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val pad = dp(10)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60B0F14")); cornerRadius = dp(14).toFloat()
            }
            setPadding(pad, pad, pad, pad)
        }
        col.addView(TextView(this).apply {
            text = "AdTurbo"; setTextColor(Color.parseColor("#2EE6C6"))
        })
        val rateLabel = TextView(this).apply { setTextColor(Color.WHITE); text = "Ads: 600 / min" }
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
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 200 }
        wm.addView(view, lp)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::view.isInitialized) wm.removeView(view)
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
EOF

cat > app/src/main/assets/agent.js <<'EOF'
/*
 * AdTurbo agent. Commands from app:
 *   {cmd:"start"} {cmd:"stop"} {cmd:"rate",v:600} {cmd:"mod",n:1}
 */
let ADSPM = 600, SPEED = 6, ACTIVE = false, timer = null;
const adObjects = [];
const log = (m) => send({ t: 'log', m: m });

const SDK = {
  'AdMob':'com.google.android.gms.ads', 'UnityAds':'com.unity3d',
  'AppLovin':'com.applovin', 'ironSource':'com.ironsource', 'Vungle':'com.vungle',
  'Mintegral':'com.mbridge', 'Pangle':'com.bytedance.sdk', 'Chartboost':'com.chartboost',
  'Tapjoy':'com.tapjoy', 'MetaAds':'com.facebook.ads', 'InMobi':'com.inmobi',
  'Fyber':'com.fyber', 'AdColony':'com.adcolony', 'Freecash':'com.freecash'
};

function remember(obj) {
  if (obj && adObjects.indexOf(obj) === -1 && adObjects.length < 40) adObjects.push(obj);
}

function hookOverloads(C, method, tag) {
  if (!C[method]) return;
  C[method].overloads.forEach(function (ov) {
    ov.implementation = function () {
      if (method === 'load' || method === 'loadAd' || method === 'show') remember(this);
      const r = ov.call(this, ...arguments);
      log(tag + '.' + method + '()');
      return r;
    };
  });
}

function scanSDKs() {
  Java.enumerateLoadedClasses({
    onMatch: function (name) {
      for (const tag in SDK) {
        if (name.indexOf(SDK[tag]) === 0 && name.indexOf('$') === -1) {
          try {
            const C = Java.use(name);
            ['show','loadAd','load','onAdLoaded','onUserEarnedReward']
              .forEach(m => hookOverloads(C, m, tag));
          } catch (e) {}
        }
      }
    },
    onComplete: function () { log('SDK scan complete'); }
  });
}

function speedEngine() {
  const T = Java.use('java.lang.Thread');
  T.sleep.overload('long').implementation = function (ms) {
    return this.sleep(ACTIVE ? Math.max(1, Math.round(ms / SPEED)) : ms);
  };
  const SC = Java.use('android.os.SystemClock');
  SC.sleep.overload('long').implementation = function (ms) {
    return this.sleep(ACTIVE ? Math.max(1, Math.round(ms / SPEED)) : ms);
  };
  try {
    const H = Java.use('android.os.Handler');
    H.postDelayed.overload('java.lang.Runnable', 'long').implementation = function (r, d) {
      return this.postDelayed(r, ACTIVE ? Math.max(1, Math.round(d / SPEED)) : d);
    };
  } catch (e) {}
  log('speed engine ready');
}

function webViewHook() {
  try {
    const WV = Java.use('android.webkit.WebView');
    WV.loadUrl.overload('java.lang.String').implementation = function (u) {
      const r = this.loadUrl(u);
      const self = this;
      setTimeout(function () {
        try {
          self.evaluateJavascript(
            "if(!window.__adt){window.__adt=1;" +
            "var _st=window.setTimeout;window.setTimeout=function(f,d){return _st(f,(d/" + SPEED + "));};" +
            "var _si=window.setInterval;window.setInterval=function(f,d){return _si(f,(d/" + SPEED + "));};}",
            null);
        } catch (e) {}
      }, 1500);
      return r;
    };
  } catch (e) {}
}

function startLoop() {
  stopLoop();
  const interval = Math.max(1, Math.round(60000 / ADSPM));
  timer = setInterval(function () {
    adObjects.forEach(function (a) { try { a.show(); } catch (e) {} });
  }, interval);
  log('loop @ ' + ADSPM + '/min (every ' + interval + 'ms)');
}
function stopLoop() { if (timer) { clearInterval(timer); timer = null; } }

Java.perform(function () {
  log('agent loaded');
  speedEngine();
  scanSDKs();
  webViewHook();
  recv(function (msg) {
    if (msg.cmd === 'start') { ACTIVE = true; startLoop(); log('STARTED'); }
    else if (msg.cmd === 'stop') { ACTIVE = false; stopLoop(); log('STOPPED'); }
    else if (msg.cmd === 'rate') { ADSPM = msg.v; SPEED = Math.max(1, ADSPM / 100); if (ACTIVE) startLoop(); log('rate=' + ADSPM); }
    else if (msg.cmd === 'mod') { log('mod #' + msg.n + ' sent'); }
  });
});
EOF

cat > app/src/main/cpp/CMakeLists.txt <<'EOF'
cmake_minimum_required(VERSION 3.22)
project(adturbo)
add_library(adturbo SHARED native-lib.cpp)
target_include_directories(adturbo PRIVATE
    ${CMAKE_SOURCE_DIR}
    /usr/include/glib-2.0
    /usr/lib/x86_64-linux-gnu/glib-2.0/include)
add_library(frida-core SHARED IMPORTED)
set_target_properties(frida-core PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libfrida-core.so)
target_link_libraries(adturbo frida-core log android)
EOF

cat > app/src/main/cpp/native-lib.cpp <<'EOF'
#include <jni.h>
#include <android/log.h>
#include "frida-core.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AdTurbo", __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static jobject g_bridge = nullptr;
static jmethodID g_onMsg = nullptr;
static FridaDeviceManager* g_mgr = nullptr;
static FridaDevice* g_dev = nullptr;
static FridaSession* g_sess = nullptr;
static FridaScript* g_script = nullptr;

static void on_message(FridaScript* s, const gchar* message,
                       const guint8* data, gint len, gpointer ud) {
    if (!g_vm || !g_bridge) return;
    JNIEnv* env = nullptr;
    g_vm->AttachCurrentThread(&env, nullptr);
    jstring js = env->NewStringUTF(message ? message : "");
    env->CallVoidMethod(g_bridge, g_onMsg, js);
    env->DeleteLocalRef(js);
}

extern "C" JNIEXPORT void JNICALL
Java_com_adturbo_app_Frida_attach(JNIEnv* env, jobject, jstring pkg, jstring source) {
    const char* cpkg = env->GetStringUTFChars(pkg, nullptr);
    const char* csrc = env->GetStringUTFChars(source, nullptr);
    if (!g_mgr) {
        g_mgr = frida_device_manager_new();
        g_dev = frida_device_manager_get_device_by_type(
                    g_mgr, FRIDA_DEVICE_TYPE_LOCAL, 5000, nullptr, nullptr);
    }
    FridaProcess* proc = frida_device_get_process_by_name(g_dev, cpkg, nullptr);
    if (!proc) { LOGI("no process %s", cpkg); return; }
    gint pid = frida_process_get_pid(proc);
    g_sess = frida_device_attach(g_dev, pid, nullptr, nullptr);
    g_script = frida_session_create_script(g_sess, csrc, nullptr, nullptr);
    frida_script_on(g_script, "message", (GCallback) on_message, nullptr);
    frida_script_load(g_script, nullptr);
    LOGI("attached %s pid=%d", cpkg, pid);
    env->ReleaseStringUTFChars(pkg, cpkg);
    env->ReleaseStringUTFChars(source, csrc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_adturbo_app_Frida_post(JNIEnv* env, jobject, jstring json) {
    if (!g_script) return;
    const char* c = env->GetStringUTFChars(json, nullptr);
    frida_script_post(g_script, c, nullptr, 0);
    env->ReleaseStringUTFChars(json, c);
}

extern "C" JNIEXPORT void JNICALL
Java_com_adturbo_app_Frida_detach(JNIEnv*, jobject) {
    if (g_script) { frida_script_unload(g_script, nullptr); g_script = nullptr; }
    if (g_sess)   { frida_session_detach(g_sess, nullptr);  g_sess = nullptr; }
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    frida_init();
    JNIEnv* env = nullptr;
    vm->GetEnv((void**) &env, JNI_VERSION_1_6);
    jclass cls = env->FindClass("com/adturbo/app/Frida");
    jfieldID instF = env->GetStaticFieldID(cls, "INSTANCE", "Lcom/adturbo/app/Frida;");
    g_bridge = env->NewGlobalRef(env->GetStaticObjectField(cls, instF));
    g_onMsg = env->GetStaticMethodID(cls, "onMessage", "(Ljava/lang/String;)V");
    return JNI_VERSION_1_6;
}
EOF

cat > .github/workflows/build.yml <<'EOF'
name: build
on: [push, workflow_dispatch]

jobs:
  apk:
    runs-on: ubuntu-latest
    env:
      FRIDA_VERSION: 16.4.10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
        with: { gradle-version: '8.7' }
      - name: deps
        run: sudo apt-get update && sudo apt-get install -y libglib2.0-dev xz-utils
      - name: fetch frida-core + servers
        run: |
          set -e
          B=https://github.com/frida/frida/releases/download/$FRIDA_VERSION
          mkdir -p app/src/main/cpp app/src/main/jniLibs/arm64-v8a app/src/main/jniLibs/x86_64 app/src/main/assets
          for pair in "arm64:arm64-v8a" "x86_64:x86_64"; do
            k=${pair%%:*}; abi=${pair##*:}
            d=/tmp/dev-$k; mkdir -p $d
            curl -sL $B/frida-core-devkit-$FRIDA_VERSION-android-$k.so.tar.xz | tar xJ -C $d
            cp $d/libfrida-core.so app/src/main/jniLibs/$abi/
            cp $d/frida-core.h app/src/main/cpp/
          done
          curl -sL $B/frida-server-$FRIDA_VERSION-android-arm64.xz | xz -dc > app/src/main/assets/frida-server-arm64
          curl -sL $B/frida-server-$FRIDA_VERSION-android-x86_64.xz | xz -dc > app/src/main/assets/frida-server-x86_64
      - name: build
        run: gradle assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: AdTurbo-apk
          path: app/build/outputs/apk/debug/app-debug.apk
EOF

echo "AdTurbo project created. Files:"
find . -type f -not -path "./.git/*" | sort

