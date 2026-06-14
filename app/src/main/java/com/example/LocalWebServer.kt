package com.example

import java.net.ServerSocket
import java.net.Socket
import java.io.OutputStream
import java.io.PrintWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.thread

object LocalWebServer {
    var serverHtmlContent = MutableStateFlow<String>("<h1>Wait for update...</h1>")
    private var isRunning = false
    private var serverSocket: ServerSocket? = null

    fun startServer(port: Int = 3000) {
        if (isRunning) return
        isRunning = true
        
        thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        thread {
            try {
                // Read request but we ignore it and just serve the html
                val input = socket.getInputStream().bufferedReader()
                val requestLine = input.readLine()
                
                val output: OutputStream = socket.getOutputStream()
                val writer = PrintWriter(output, true)
                
                val content = serverHtmlContent.value
                
                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: text/html; charset=UTF-8")
                writer.println("Access-Control-Allow-Origin: *")
                writer.println("Content-Length: ${content.toByteArray().size}")
                writer.println("Connection: close")
                writer.println()
                writer.println(content)
                
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
    }
}
