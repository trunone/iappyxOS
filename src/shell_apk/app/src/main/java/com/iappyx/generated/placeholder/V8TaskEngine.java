/*
 * MIT License
 *
 * Copyright (c) 2026 iappyx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.iappyx.generated.placeholder;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.javascriptengine.JavaScriptConsoleCallback;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Executes JavaScript using V8 engine with console log capture and iappyx bridge methods.
 */
public class V8TaskEngine {

    private static final String TAG = "iappyxOS-V8";

    public interface LogListener {
        void onLog(String type, String message);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor executor = Executors.newSingleThreadExecutor();

    private JavaScriptSandbox sandbox;
    private JavaScriptIsolate isolate;
    private WebView fallbackWebView; // V8 runtime with Java interfaces for full bridge access
    private boolean isRunning = false;

    public V8TaskEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    public synchronized void runScript(String script, LogListener listener, Runnable onComplete) {
        if (isRunning) {
            stop();
        }
        isRunning = true;

        if (listener != null) {
            listener.onLog("sys", "Initializing V8 Engine...");
        }

        // Attempt JavaScriptSandbox (V8 Isolate) if supported
        if (JavaScriptSandbox.isSupported()) {
            ListenableFuture<JavaScriptSandbox> future = JavaScriptSandbox.createConnectedInstanceAsync(context);
            future.addListener(() -> {
                synchronized (V8TaskEngine.this) {
                    if (!isRunning) return;
                    try {
                        sandbox = future.get();
                        isolate = sandbox.createIsolate();

                        // Register console callback
                        isolate.addJavaScriptConsoleCallback(executor, (iso, message) -> {
                            String type = "log";
                            int level = message.getLevels();
                            if ((level & JavaScriptConsoleCallback.ConsoleMessage.LEVEL_ERROR) != 0) type = "error";
                            else if ((level & JavaScriptConsoleCallback.ConsoleMessage.LEVEL_WARN) != 0) type = "warn";
                            else if ((level & JavaScriptConsoleCallback.ConsoleMessage.LEVEL_INFO) != 0) type = "info";

                            final String finalType = type;
                            final String msgText = message.getMessage();
                            mainHandler.post(() -> {
                                if (listener != null) listener.onLog(finalType, msgText);
                            });
                        });

                        String wrappedScript = buildWrappedScript(script);

                        if (listener != null) {
                            listener.onLog("sys", "Executing script in V8 Isolate...");
                        }

                        ListenableFuture<String> evalFuture = isolate.evaluateJavaScriptAsync(wrappedScript);
                        evalFuture.addListener(() -> {
                            synchronized (V8TaskEngine.this) {
                                if (!isRunning) return;
                                try {
                                    String res = evalFuture.get();
                                    if (res != null && !res.equals("undefined") && !res.equals("null") && listener != null) {
                                        listener.onLog("info", "Result: " + res);
                                    }
                                    listener.onLog("sys", "Execution finished.");
                                } catch (Exception e) {
                                    if (listener != null) {
                                        listener.onLog("error", "V8 Execution error: " + e.getMessage());
                                    }
                                } finally {
                                    isRunning = false;
                                    if (onComplete != null) mainHandler.post(onComplete);
                                }
                            }
                        }, executor);

                    } catch (Exception e) {
                        Log.w(TAG, "JavaScriptSandbox init failed, using fallback V8: " + e.getMessage());
                        runFallbackV8(script, listener, onComplete);
                    }
                }
            }, executor);
        } else {
            runFallbackV8(script, listener, onComplete);
        }
    }

    private void runFallbackV8(String script, LogListener listener, Runnable onComplete) {
        mainHandler.post(() -> {
            synchronized (V8TaskEngine.this) {
                if (!isRunning) return;
                try {
                    fallbackWebView = new WebView(context);
                    fallbackWebView.getSettings().setJavaScriptEnabled(true);

                    fallbackWebView.addJavascriptInterface(new Object() {
                        @JavascriptInterface
                        public void log(String type, String msg) {
                            mainHandler.post(() -> {
                                if (listener != null) listener.onLog(type, msg);
                            });
                        }
                        @JavascriptInterface public void save(String k, String v) { BridgeUtils.save(context, k, v); }
                        @JavascriptInterface public String load(String k) { return BridgeUtils.load(context, k); }
                        @JavascriptInterface public void remove(String k) { BridgeUtils.remove(context, k); }
                        @JavascriptInterface public void sendNotif(String title, String body) { BridgeUtils.sendNotification(context, title, body); }
                        @JavascriptInterface public void vibrate(String ms) { BridgeUtils.vibrate(context, ms); }
                        @JavascriptInterface public void ttsSpeak(String text) { BridgeUtils.ttsSpeak(context, text); }
                        @JavascriptInterface public String getDeviceInfo() { return BridgeUtils.getDeviceInfo(context); }
                    }, "_v8Native");

                    String html = "<!DOCTYPE html><html><head><script>" +
                        "console.log=function(){_v8Native.log('log',Array.from(arguments).map(String).join(' '))};" +
                        "console.error=function(){_v8Native.log('error',Array.from(arguments).map(String).join(' '))};" +
                        "console.warn=function(){_v8Native.log('warn',Array.from(arguments).map(String).join(' '))};" +
                        "console.info=function(){_v8Native.log('info',Array.from(arguments).map(String).join(' '))};" +
                        "window.onerror=function(m,u,l){_v8Native.log('error','['+l+'] '+m)};" +
                        "window.iappyx={" +
                        "save:function(k,v){_v8Native.save(k,v)}," +
                        "load:function(k){return _v8Native.load(k)}," +
                        "remove:function(k){_v8Native.remove(k)}," +
                        "notification:{send:function(t,b){_v8Native.sendNotif(t,b)}}," +
                        "vibration:{click:function(){_v8Native.vibrate('50')},vibrate:function(ms){_v8Native.vibrate(ms)}}," +
                        "device:{getDeviceInfo:function(){return _v8Native.getDeviceInfo()}}," +
                        "tts:{speak:function(t){_v8Native.ttsSpeak(t)}}" +
                        "};" +
                        "try{" + script + "}catch(e){console.error('Error: '+e.message);}" +
                        "</script></head><body></body></html>";

                    if (listener != null) listener.onLog("sys", "Executing script in V8 runtime...");
                    fallbackWebView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);

                    mainHandler.postDelayed(() -> {
                        synchronized (V8TaskEngine.this) {
                            if (isRunning) {
                                isRunning = false;
                                if (listener != null) listener.onLog("sys", "Execution finished.");
                                if (onComplete != null) onComplete.run();
                            }
                        }
                    }, 1000);

                } catch (Exception e) {
                    isRunning = false;
                    if (listener != null) listener.onLog("error", "Execution failed: " + e.getMessage());
                    if (onComplete != null) onComplete.run();
                }
            }
        });
    }

    private String buildWrappedScript(String userScript) {
        return "(function(){\n" +
            "  var _store = {};\n" +
            "  globalThis.iappyx = globalThis.iappyx || {\n" +
            "    save: function(k, v) { _store[k] = String(v); },\n" +
            "    load: function(k) { return _store.hasOwnProperty(k) ? _store[k] : null; },\n" +
            "    remove: function(k) { delete _store[k]; },\n" +
            "    notification: { send: function(t, b) { console.log('[Notification] ' + t + ': ' + b); } },\n" +
            "    vibration: { click: function(){}, vibrate: function(){} },\n" +
            "    device: { getDeviceInfo: function(){ return JSON.stringify({model:'V8 Engine', sdk:35}); } },\n" +
            "    tts: { speak: function(t){ console.log('[TTS] ' + t); } }\n" +
            "  };\n" +
            "  try {\n" +
            userScript + "\n" +
            "  } catch(e) {\n" +
            "    console.error('Runtime Error: ' + e.message + (e.stack ? '\\n' + e.stack : ''));\n" +
            "  }\n" +
            "})();";
    }

    public synchronized void stop() {
        isRunning = false;
        if (isolate != null) {
            try { isolate.close(); } catch (Exception ignored) {}
            isolate = null;
        }
        if (sandbox != null) {
            try { sandbox.close(); } catch (Exception ignored) {}
            sandbox = null;
        }
        if (fallbackWebView != null) {
            try {
                fallbackWebView.stopLoading();
                fallbackWebView.destroy();
            } catch (Exception ignored) {}
            fallbackWebView = null;
        }
    }
}
