package com.example.mydmucabapp_driver.utils

import android.content.Context
import android.os.StrictMode
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class FCMSend {
    fun pushNotification(context: Context, to: String, title: String, body: String, response:(m: String)->Unit){
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())

        val queue = Volley.newRequestQueue(context)

        try {
            val jsonObject = JSONObject()
            jsonObject.put("to",to)
            val notification = JSONObject()
            notification.put("title",title)
            notification.put("body",body)
            jsonObject.put("notification",notification)

            val request = object: JsonObjectRequest(Method.POST,Consts.FCM_BASE_URL,jsonObject,
                Response.Listener {
                    response("Success")
                },
                Response.ErrorListener {
                    response("${it.message}")
                })  {
                override fun getHeaders(): MutableMap<String, String> {
                    val headers: MutableMap<String, String> = HashMap()
                    headers["Content-Type"] = "application/json"
                    headers["Authorization"] = "key="+Consts.FCM_SERVER_KEY
                    return headers
                }
            }

            queue.add(request)

        }catch (e: Exception){
            response("Something went wrong")
        }

    }
}