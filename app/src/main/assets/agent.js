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
