package com.commissionsinc.androidsignalr

import android.content.Context
import android.util.Log
import android.webkit.*
import com.commissionsinc.androidsignalr.AndroidSignalR.stringify
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject


/**
 * Created by John Dugan on 12/6/17.
 */


open class SignalR{

    enum class SignalRVersion(val value: String) {
        v2_2_2("2.2.2"), v2_2_1("2.2.1"), v2_2_0("2.2.0"), v2_1_2("2.1.2"), v2_1_1("2.1.1"),
        v2_1_0("2.1.0"), v2_0_3("2.0.3"), v2_0_2("2.0.2"), v2_0_1("2.0.1"), v2_0_0("2.0.0");

        override fun toString() = value
    }

    enum class ConnectionType {
        HUB, PERSISTENT
    }

    enum class State {
        CONNECTING, CONNECTED, DISCONNECTED
    }

    enum class Transport(val value: String) {
        AUTO("auto"), WEBSOCKETS("webSockets"), FOREVERFRAME("foreverFrame"), SERVERSENTEVENTS("serverSentEvents"), LONGPOLLING("longPolling");

        override fun toString() = value
    }

    var context: Context? = null
    var connections = ArrayList<SignalR>()
    var ready = false
    var signalRVersion = SignalRVersion.v2_2_2
    var transport = Transport.AUTO
    var webView: WebView
    var baseUrl: String = ""
    var connectionType: ConnectionType = ConnectionType.HUB
    var loadByInlineJS = false

    lateinit var readyHandler: ((SignalR) -> (Unit))
    var hubs: MutableMap<String, Hub> = mutableMapOf()

    open var state: State = State.DISCONNECTED
    open var connectionID: String? = null
    open var received: ((Any?) -> (Unit))? = null
    open var starting: (() -> (Unit))? = null
    open var connected: (() -> (Unit))? = null
    open var disconnected: (() -> (Unit))? = null
    open var connectionSlow: (() -> (Unit))? = null
    open var connectionFailed: (() -> (Unit))? = null
    open var reconnecting: (() -> (Unit))? = null
    open var reconnected: (() -> (Unit))? = null
    open var error: ((JsonObject?) -> (Unit))? = null

    open var customUserAgent: String? = null

    open var queryString: Any
        get() = ""
        set(value) {

            val gson = GsonBuilder().create()
            val json = gson.toJson(value)
            if (json != null){
                runJavaScript("swiftR.connection.qs = ${json}")
            }else{
                runJavaScript("swiftR.connection.qs = {}")
            }
        }

    open var headers: MutableMap<String, String>
        get() = mutableMapOf()
        set(value) {

            val gson = GsonBuilder().create()
            var json = gson.toJson(value)
            if (json != null){
                runJavaScript("swiftR.headers.qs = {${json}}")
            }else{
                runJavaScript("swiftR.headers.qs = {}")
            }
        }

    constructor(context: Context?, baseUrl: String, connectionType: ConnectionType, webView: WebView) {
        this.context = context
        this.baseUrl = baseUrl
        this.connectionType = connectionType
        this.webView = webView
    }

    fun connect(callback: (() -> (Unit))? = null) {
        readyHandler = { _ ->

            val hubs = this.hubs
            hubs.forEach { it.value.initialize() }
            this.ready = true

            callback?.invoke()
        }
        initialize()
    }

    fun initialize() {

        //Load resources
        val jqueryURL = "file:///android_asset/jquery-2.1.3.min.js"
        val signalRURL = "file:///android_asset/jquery.signalr-${signalRVersion}.min.js"
        val swiftRURL = "file:///android_asset/SwiftR.js"

        val scriptAsSource: (String?) -> String = { rawScript ->
            "<script src='${rawScript}'></script>"
        }

        val jquerySourceInclude = scriptAsSource(jqueryURL)
        val signalRSourceInclude = scriptAsSource(signalRURL)
        val swiftRSourceInclude = scriptAsSource(swiftRURL)


        var html = "<!doctype html><html><head></head><body>" + "${jquerySourceInclude}${signalRSourceInclude}${swiftRSourceInclude}" + "</body></html>"

        if (loadByInlineJS){

            val script: (String?) -> String = { rawScript ->
                "<script>${rawScript}</script>"
            }

            val jqueryFilename = "jquery-2.1.3.min.js"
            val signalRFilename = "jquery.signalr-${signalRVersion}.min.js"
            val swiftRFilename = "SwiftR.js"

            val jquery = context?.assets?.open(jqueryFilename)?.bufferedReader().use { it?.readText() }  // defaults to UTF-8
            val signalRJS = context?.assets?.open(signalRFilename)?.bufferedReader().use { it?.readText() }  // defaults to UTF-8
            val swiftRJS = context?.assets?.open(swiftRFilename)?.bufferedReader().use { it?.readText() }  // defaults to UTF-8

            val jqueryInclude = script(jquery)
            val signalRInclude = script(signalRJS)
            val swiftRInclude = script(swiftRJS)

            html = "<!doctype html><html><head></head><body>" + "${jqueryInclude}${signalRInclude}${swiftRInclude}" + "</body></html>"
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.webChromeClient = SignalRWebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.d("AndroidSignalR", "loading web view: request: $request error: $error")
                super.onReceivedError(view, request, error)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request?.url.toString().startsWith("swiftr://")) {
                    shouldHandleRequest(request)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.url.toString().startsWith("swiftr://")) {
                    shouldHandleRequest(request)
                    return true
                }

                return false
            }
        }

        webView.post {
            webView.loadDataWithBaseURL("file://android_asset/", html, "text/html", "UTF-8", null)
        }

        val ua = customUserAgent
        if (ua != null) {
            applyUserAgent(ua)
        }

        AndroidSignalR.connections.add(this)
    }

    open fun createHubProxy(name: String): Hub {
        val hub = Hub(name = name, connection = this)
        hubs[name.toLowerCase()] = hub
        return hub
    }

    open fun addHub(hub: Hub) {
        hub.connection = this
        hubs[hub.name.toLowerCase()] = hub
    }

    open fun send(data: Any?) {
        var json = "null"
        val d = data
        if (d != null) {
            val value = stringify(d)
            if (value != null) {
                json = value
            }
        }
        runJavaScript("swiftR.connection.send(${json})")
    }

    open fun start() {
        if (ready) {
            runJavaScript("start()")
        } else {
            connect()
        }
    }

    open fun stop() {
        runJavaScript("swiftR.connection.stop()")
    }

    fun shouldHandleRequest(request: WebResourceRequest?): Boolean {
        if (request?.url.toString().startsWith("swiftr://")) {
            val id = (request?.url!!.toString()).substring(9)
            webView.post {
                webView.evaluateJavascript("readMessage('${id}')") {

                    val gson = Gson()
                    val data = gson.fromJson(it, JsonElement::class.java)
                    val jsonString = data.asJsonPrimitive.asString
                    val parameters = gson.fromJson(jsonString, JsonObject::class.java)

                    if (parameters != null) {
                        processMessage(parameters)
                    }
                }
            }
        }
        return true
    }

    fun processMessage(json: JsonObject) {
        val message = json.get("message")
        if (message != null) {
            when (message.asString) {
                "ready" -> {
                    val isHub = if (connectionType == ConnectionType.HUB) "true" else "false"
                    runJavaScript("swiftR.transport = '${transport.toString()}'")
                    runJavaScript("initialize('${baseUrl}', ${isHub})")
                    readyHandler(this)
                    runJavaScript("start()")
                }
                "starting" -> {
                    state = State.CONNECTING
                    starting?.invoke()
                }
                "connected" -> {
                    state = State.CONNECTED
                    connectionID = json.get("connectionId").asString
                    connected?.invoke()
                }
                "disconnected" -> {
                    state = State.DISCONNECTED
                    disconnected?.invoke()
                }
                "connectionSlow" -> connectionSlow?.invoke()
                "connectionFailed" -> connectionFailed?.invoke()
                "reconnecting" -> {
                    state = State.CONNECTING
                    reconnecting?.invoke()
                }
                "reconnected" -> {
                    state = State.CONNECTED
                    reconnected?.invoke()
                }
                "invokeHandler" -> {
                    val hubName = json.get("hub").asString
                    val hub = hubs[hubName]
                    if (hub != null) {
                        val uuid = json.get("id").asString
                        val result = json.get("result")
                        val error = json.get("error")
                        val callback = hub.invokeHandlers[uuid]
                        if (callback != null) {
                            callback(result as Any?, error)
                            hub.invokeHandlers.remove(uuid)
                        } else {
                            val e = error
                            if (e != null) {
                                print("SwiftR invoke error: ${e}")
                            }
                        }
                    }
                }
                "error" -> {
                    val err = json.get("error").asJsonObject
                    if (err != null) {
                        error?.invoke(err)
                    } else {
                        error?.invoke(null)
                    }
                }
            }
        } else {
            val data: Any? = json.get("data")
            if (data != null) {
                received?.invoke(data)
            } else {

                val hubName: String? = json.get("hub").asString

                val callbackID = json.get("id").asString
                val method = json.get("method").asString
                val hub = hubs[hubName]

                val arguments = json.get("arguments").asJsonArray
                var argsList = mutableListOf<Any>()

                for (i in 0 until arguments.size()){
                    val jsonObj = arguments.get(i)
                    argsList.add(jsonObj)
                }

                val handlers = hub?.handlers?.get(method)
                val handler = handlers?.get(callbackID)
                if (method != null && callbackID != null && handlers != null && handler != null) {
                    handler(argsList)
                }
            }
        }
    }


    fun runJavaScript(script: String, callback: ((Any?) -> (Unit))? = null) {
        webView.post {
            webView.evaluateJavascript(script, { msg ->
                callback?.invoke(msg)
            })
        }
    }

    fun applyUserAgent(userAgent: String) {
        webView.settings.userAgentString = userAgent
    }

    open class SignalRWebChromeClient : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
//            Log.d("AndroidSignalR", "JSConsoleMessage: ${consoleMessage.toString()}")
            return super.onConsoleMessage(consoleMessage)
        }

    }
}
