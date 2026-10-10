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
