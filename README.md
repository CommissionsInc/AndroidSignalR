# AndroidSignalR

A Kotlin fork of the Swift client for SignalR. Supports hubs and persistent connections.

### How does it work?

It's a wrapper around the SignalR JavaScript client running in a hidden web view. As such, it's subject to the same limitations of that client -- namely, no support for custom headers when using WebSockets. This is because the browser's WebSocket client does not support custom headers.

### Installation

jcenter

### Simple Example

Add a hidden webview to your layout

```xml
    <WebView
        android:id="@+id/webView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:visibility="gone" />
```

Set up your SignalR connection and hub

```kotlin

        val webView = findViewById<WebView>(R.id.webView)
        val connection = SignalR(this, "http://localhost:5000", SignalR.ConnectionType.HUB, webView)
        val hub = Hub("hub")
        hub.on("message") { args ->
            val name = (args?.get(0) as JsonPrimitive).asString
            val message = (args.get(1) as JsonPrimitive).asString
            Log.d("AndroidSignalR", "${name}: ${message}")
        }
        connection.addHub(hub)
        connection.start()


```

### Demo

Adam Hartform published a sample SignalR server at http://swiftr.azurewebsites.net. The Kotlin demo application now uses this server, along with the iOS version. See [SwiftRChat](https://github.com/adamhartford/SwiftRChat) for the souce code. It's based on this, with some minor changes:

http://www.asp.net/signalr/overview/deployment/using-signalr-with-azure-web-sites

### What versions of SignalR are supported?

AndroidSignalR supports SignalR version 2.x. Version 2.2.2 is assumed by default. To change the SignalR version:

```kotlin
val connection = SignalR(this, "http://swiftr.azurewebsites.net", SignalR.ConnectionType.HUB, webView)
connection.signalRVersion = SignalR.SignalRVersion.v2_2_2
//connection.signalRVersion = SignalR.SignalRVersion.v2_2_1
//connection.signalRVersion = SignalR.SignalRVersion.v2_2_0
//connection.signalRVersion = SignalR.SignalRVersion.v2_1_2
//connection.signalRVersion = SignalR.SignalRVersion.v2_1_1
//connection.signalRVersion = SignalR.SignalRVersion.v2_1_0
//connection.signalRVersion = SignalR.SignalRVersion.v2_0_3
//connection.signalRVersion = SignalR.SignalRVersion.v2_0_2
//connection.signalRVersion = SignalR.SignalRVersion.v2_0_1
//connection.signalRVersion = SignalR.SignalRVersion.v2_0_0
```

### TODOS
- Test Coverage
- Potential Java compatibility
- Explore WindowManager support to allow running AndroidSignalR in a background service.  The largest concern is the android.permission.SYSTEM_ALERT_WINDOW requirement. 


### Untested Features
- Persistent Connections
- Custom headers
- Cookies

### License

AndroidSignalR is released under the MIT license. See LICENSE for details.

