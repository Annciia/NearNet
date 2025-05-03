package com.nearnet.sessionlayer.network

import com.google.gson.Gson
import com.nearnet.sessionlayer.data.PackageCommand
import java.net.Socket

object SocketClient {
    private const val HOST = "192.168.0.16"
    private const val PORT = 8080

    fun sendAndReceive(pkg: PackageCommand): String? {
        try {
            Socket(HOST, PORT).use { socket ->
                val gson = Gson()
                val json = gson.toJson(pkg)


                val output = socket.getOutputStream()
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
                socket.shutdownOutput()

                val input = socket.getInputStream()
                val response = input.bufferedReader().readText()

                return response
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
