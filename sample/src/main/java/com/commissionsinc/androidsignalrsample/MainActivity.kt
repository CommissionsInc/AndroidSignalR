package com.commissionsinc.androidsignalrsample

import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.commissionsinc.androidsignalr.Hub
import com.commissionsinc.androidsignalr.SignalR
import com.google.gson.JsonPrimitive

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var textView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var webView: WebView

    lateinit var chatHub: Hub
    lateinit var connection: SignalR
    var name: String = "Anonymous"

    var started = false
    private var menu: Menu? = null

    val logTag = "AndroidSignalRSample"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById<Toolbar>(R.id.toolbar)
        messageEditText = findViewById<EditText>(R.id.messageEditText)
        sendButton = findViewById<Button>(R.id.sendButton)
        textView = findViewById<TextView>(R.id.messagesTextView)
        statusTextView = findViewById<TextView>(R.id.statusTextView)
        webView = findViewById<WebView>(R.id.webView)

        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    override fun onResume() {
        super.onResume()

        connection = SignalR(this, "http://swiftr.azurewebsites.net", SignalR.ConnectionType.HUB, webView)
        connection.signalRVersion = SignalR.SignalRVersion.v2_2_2

        chatHub = Hub("chatHub")
        chatHub.on("broadcastMessage") { args ->

            val name = (args?.get(0) as JsonPrimitive).asString
            val message = (args.get(1) as JsonPrimitive).asString

            addSignalRText("${name}: ${message}")
        }

        connection.addHub(chatHub)

        //SignalR events
        connection.starting = {
            statusTextView.text = getString(R.string.starting)
            sendButton.isEnabled = false
        }

        connection.reconnecting = {
            statusTextView.text = getString(R.string.reconnecting)
            sendButton.isEnabled = false
        }

        connection.connected = {
            Log.d(logTag, "Connection ID: ${connection.connectionID}")
            statusTextView.text = getString(R.string.connected)
            sendButton.isEnabled = true
            started = true
            updateMenu()
        }

        connection.reconnected = {
            statusTextView.text = getString(R.string.reconnected)
            sendButton.isEnabled = true
            started = true
            updateMenu()
        }

        connection.disconnected = {
            statusTextView.text = getString(R.string.disconnected)
            sendButton.isEnabled = false
            started = false
            updateMenu()
        }

        connection.connectionSlow = {
            Log.d(logTag, "Connection Slow")
        }

        connection.error = { error ->
            Log.d(logTag, "Error: ${error.toString()}")
        }

        connection.start()

        promptForName()
    }

    fun promptForName(){
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Name")
        builder.setMessage("Please enter your name")

        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton("OK", object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface, which: Int) {
                name = input.text.toString()
            }
        })

        builder.show()
    }

    fun startStopPressed() {
        Log.d(logTag, "startStopPressed")

        if (started) {
            connection.stop()
        } else {
            connection.start()
        }
    }

    fun sendPressed(view: View) {
        Log.d(logTag, "sendPressed")

        val message = listOf(name, messageEditText.text.toString())

        chatHub.invoke("send", arguments = message)
    }

    fun addSignalRText(text: String){
        val currentText = textView.text

        textView.text = "${currentText}\n${text}"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.start_menu_item) {
            startStopPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateMenu() {
        val menuItem = menu?.findItem(R.id.start_menu_item)

        menuItem?.setTitle(R.string.start)
        if (started) {
            menuItem?.setTitle(R.string.stop)
        }
    }
}
