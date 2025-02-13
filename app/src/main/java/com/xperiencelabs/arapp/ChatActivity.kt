package com.xperiencelabs.arapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var visualize3DButton: Button
    private var latestImageUrl: String? = null
    private var sessionId: String? = null

    val imageUrlList = mutableListOf<String>()
    val imageUrlList2d = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        visualize3DButton = findViewById(R.id.btn_visualize_3d)

        visualize3DButton.isEnabled = false
        sendButton.isEnabled = false

        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.adapter = chatAdapter

        // Initialize session when activity starts
        activityScope.launch {
            try {
                val startResponse = makeStartRequest()
                handleStartResponse(startResponse)
                sendButton.isEnabled = true
            } catch (e: Exception) {
                handleError(e)
                finish()
            }
        }

        sendButton.setOnClickListener {
            val userMessage = messageInput.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                if (sessionId == null) {
                    Toast.makeText(this@ChatActivity, "Session not initialized", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                addUserMessage(userMessage)
                activityScope.launch {
                    try {
                        val serverResponse = makeChatRequest(userMessage, sessionId!!)
                        handleServerResponse(serverResponse)
                    } catch (e: Exception) {
                        handleError(e)
                    }
                }
            }
        }

        visualize3DButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            if (imageUrlList.isNotEmpty()) {
                intent.putStringArrayListExtra("IMAGE_URL_LIST", ArrayList(imageUrlList))
                intent.putStringArrayListExtra("IMAGE_URL_LIST_2D", ArrayList(imageUrlList2d))
                startActivity(intent)
            } else {
                latestImageUrl?.let {
                    val imageUrlConverted = it.replace(".jpg", ".glb")
                    intent.putExtra("IMAGE_URL", imageUrlConverted)
                    startActivity(intent)
                }
            }
        }
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(message = text, isSent = true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        messageInput.text.clear()
        chatRecyclerView.smoothScrollToPosition(messages.size - 1)
    }

    private suspend fun makeStartRequest(): String = withContext(Dispatchers.IO) {
        val url = URL("http://13.92.86.232/start")
        var result = ""

        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Accept", "application/json")

            try {
                result = if (responseCode in 200..299) {
                    inputStream.bufferedReader().use(BufferedReader::readText)
                } else {
                    "HTTP Error: $responseCode"
                }
                Log.d("ChatActivity", "Start Response: $result")
            } finally {
                disconnect()
            }
        }
        return@withContext result
    }

    private fun handleStartResponse(response: String) {
        try {
            val jsonObject = JSONObject(response)
            sessionId = jsonObject.optString("session_id", "").also {
                if (it.isEmpty()) throw IllegalStateException("Empty session_id")
            }
            Log.d("ChatActivity", "New session ID: $sessionId")
        } catch (e: Exception) {
            Log.e("ChatActivity", "Session initialization failed", e)
            throw e
        }
    }

    private suspend fun makeChatRequest(userInput: String, sessionId: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("http://13.92.86.232/chat")
            var result = ""

            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val jsonInput = JSONObject().apply {
                    put("session_id", sessionId)
                    put("message", userInput)
                }.toString()

                outputStream.use { os ->
                    OutputStreamWriter(os, "UTF-8").use { writer ->
                        writer.write(jsonInput)
                        writer.flush()
                    }
                }

                try {
                    result = if (responseCode in 200..299) {
                        inputStream.bufferedReader().use(BufferedReader::readText)
                    } else {
                        errorStream?.bufferedReader()?.use(BufferedReader::readText)
                            ?: "HTTP Error: $responseCode"
                    }
                    Log.d("ChatActivity", "Chat Response: $result")
                } finally {
                    disconnect()
                }
            }
            return@withContext result
        }

    private fun handleServerResponse(response: String) {
        try {
            Log.d("ChatActivity", "Parsing response: $response")
            val jsonObject = JSONObject(response)

            // Update session ID if present in response
            jsonObject.optString("session_id", "")?.takeIf { it.isNotEmpty() }?.let {
                sessionId = it
                Log.d("ChatActivity", "Updated session ID: $it")
            }

            val assistantResponse = jsonObject.optString("assistant_response", "")
            if (assistantResponse.isNotEmpty()) {
                messages.add(ChatMessage(message = assistantResponse, isSent = false, isReply = true))
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.smoothScrollToPosition(messages.size - 1)
            }

            val apiResponse = Gson().fromJson(response, ApiResponse::class.java)

            if (!apiResponse.content.isNullOrEmpty()) {
                apiResponse.content.forEach { item ->
                    val imageUrl = item.image_2d
                    val imageUrl2 = item.image_3d

                    latestImageUrl = imageUrl
                    imageUrlList2d.add(imageUrl)
                    imageUrlList.add(imageUrl2)

                    visualize3DButton.isEnabled = true

                    messages.add(ChatMessage(imageUrl = imageUrl, isSent = false, isReply = true))
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            } else if (apiResponse.content_scrapped != null) {
                messages.add(ChatMessage(message = apiResponse.content_scrapped, isSent = false, isReply = true))
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.smoothScrollToPosition(messages.size - 1)
            }
        } catch (e: Exception) {
            Log.e("ChatActivity", "Response handling failed", e)
            handleError(e)
        }
    }

    private fun handleError(error: Exception) {
        Toast.makeText(this, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}