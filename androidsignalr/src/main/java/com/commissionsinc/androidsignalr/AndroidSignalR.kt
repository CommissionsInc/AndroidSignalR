package com.commissionsinc.androidsignalr

import com.google.gson.GsonBuilder
import java.util.*

/**
 * Created by John Dugan on 12/7/17.
 */

object AndroidSignalR {

    var connections = ArrayList<SignalR>()

    fun stringify(obj: Any): String?{

        val arr = listOf(obj)
        val gson = GsonBuilder().create()
        var json = gson.toJson(arr)

        json = json.substring(1, json.length-1)

        return json
    }
}


