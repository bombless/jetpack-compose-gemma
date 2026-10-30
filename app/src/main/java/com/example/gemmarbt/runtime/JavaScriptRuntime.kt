package com.example.gemmarbt.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight, high-speed JavaScript runtime using Android's native V8-powered WebView engine.
 * Supports console.log redirection, async evaluation, and capturing result outputs.
 */
class JavaScriptRuntime(private val context: Context) {

    private var webView: WebView? = null
    private val callIdCounter = AtomicLong(0)
    private val pendingCalls = mutableMapOf<String, CompletableDeferred<String>>()
    private val capturedLogs = StringBuilder()

    init {
        Handler(Looper.getMainLooper()).post {
            try {
                val wv = WebView(context)
                wv.settings.javaScriptEnabled = true
                wv.addJavascriptInterface(JsBridge(), "AndroidBridge")
                webView = wv
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun log(msg: String) {
            synchronized(capturedLogs) {
                capturedLogs.append(msg).append("\n")
            }
        }

        @JavascriptInterface
        fun onResult(callId: String, result: String, error: String?) {
            val deferred = synchronized(pendingCalls) { pendingCalls.remove(callId) }
            if (deferred != null) {
                if (error.isNullOrEmpty()) {
                    deferred.complete(result)
                } else {
                    deferred.complete("Error: $error")
                }
            }
        }
    }

    suspend fun execute(code: String): String = withContext(Dispatchers.Main) {
        val callId = "call_${callIdCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<String>()
        synchronized(pendingCalls) {
            pendingCalls[callId] = deferred
        }
        synchronized(capturedLogs) {
            capturedLogs.clear()
        }

        val escapedCode = JSONObject.quote(code)

        val wrapper = """
            (function() {
                var outputLog = [];
                var origLog = console.log;
                var origError = console.error;
                console.log = function() {
                    var args = Array.prototype.slice.call(arguments);
                    var str = args.map(function(x) { 
                        return typeof x === 'object' ? JSON.stringify(x) : String(x); 
                    }).join(' ');
                    outputLog.push(str);
                    AndroidBridge.log(str);
                };
                console.error = function() {
                    var args = Array.prototype.slice.call(arguments);
                    var str = 'ERROR: ' + args.join(' ');
                    outputLog.push(str);
                    AndroidBridge.log(str);
                };

                try {
                    var codeToRun = $escapedCode;
                    var evalResult = eval(codeToRun);
                    var finalRes = (evalResult !== undefined ? String(evalResult) : "");
                    var allOutput = outputLog.join("\n");
                    if (finalRes && allOutput) {
                        allOutput = allOutput + "\nResult: " + finalRes;
                    } else if (finalRes) {
                        allOutput = finalRes;
                    }
                    AndroidBridge.onResult('$callId', allOutput || "Execution finished (no output)", "");
                } catch(e) {
                    AndroidBridge.onResult('$callId', outputLog.join("\n"), e.stack || e.toString());
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(wrapper, null)

        try {
            deferred.await()
        } catch (e: Exception) {
            "Execution exception: ${e.message}"
        }
    }
}
