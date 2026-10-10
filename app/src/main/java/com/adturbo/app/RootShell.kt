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
