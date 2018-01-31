package com.commissionsinc.androidsignalr

import com.commissionsinc.androidsignalr.AndroidSignalR.stringify
import java.util.*

/**
 * Created by John Dugan on 12/7/17.
 */

class Hub {

    internal var name = ""
    internal val callbackID = "androidSignalRCallbackId"
    var handlers: MutableMap<String, MutableMap<String, (List<Any>?) -> (Unit)>> = mutableMapOf()
    var invokeHandlers: MutableMap<String, (result: Any?, error: Any?) -> (Unit)> = mutableMapOf()
    lateinit var connection: SignalR

    constructor(name: String) {
        this.name = name
    }

    constructor(name: String, connection: SignalR) {
        this.name = name
        this.connection = connection
    }

    fun on(method: String, callback: (List<Any>?) -> (Unit)) {

        if (handlers[method] == null) {
            handlers[method] = mutableMapOf()
        }

        handlers[method]?.set(callbackID, callback)
    }


    fun initialize() {
        for ((method, callbacks) in handlers) {
            callbacks.forEach {
                connection.runJavaScript("addHandler('${it.key}', '${name}', '${method}')")
            }
        }
    }

    fun invoke(method: String, arguments: List<Any>? = null, callback: ((_result: Any?, _error: Any?) -> (Unit))? = null) {

        val jsonArguments = ArrayList<String>()

        val rawArgs = arguments
        if (rawArgs != null) {
            for (arg in rawArgs) {
                val value = stringify(arg)
                if (value != null) {
                    jsonArguments.add(value)
                } else {
                    jsonArguments.add("null")
                }
            }
        }

        val args = jsonArguments.joinToString()

        val handler = callback
        if (handler != null) {
            invokeHandlers[callbackID] = handler
        }

        val doneJS = "function() { postMessage({ message: 'invokeHandler', hub: '${name.toLowerCase()}', id: '${callbackID}', result: arguments[0] }); }"
        val failJS = "function() { postMessage({ message: 'invokeHandler', hub: '${name.toLowerCase()}', id: '${callbackID}', error: processError(arguments[0]) }); }"
        val js = if (args.isEmpty()) "ensureHub('${name}').invoke('${method}').done(${doneJS}).fail(${failJS})" else "ensureHub('${name}').invoke('${method}', ${args}).done(${doneJS}).fail(${failJS})"
        connection.runJavaScript(js)
    }


}

