package com.xperiencelabs.arapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Initialize UI elements
        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)

        // Set up RecyclerView with a LinearLayoutManager and ChatAdapter
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        // Handle send button click to send a new message
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                // Add the message locally (assuming it's a sent message)
                val chatMessage = ChatMessage(text, isSent = true)
                messages.add(chatMessage)
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.scrollToPosition(messages.size - 1)
                messageInput.text.clear()

                // Create a MessageRequest and call the API
                val request = MessageRequest(text)
                ApiClient.chatApiService.sendMessage(request).enqueue(object : Callback<MessageResponse> {
                    override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                        if (response.isSuccessful) {
                            val apiResponse = response.body()
                            // You might want to add the response message to your chat view
                            if (apiResponse != null) {
                                // For example, add the API's response as a received message
                                val receivedMessage = ChatMessage(apiResponse.response, isSent = false)
                                messages.add(receivedMessage)
                                chatAdapter.notifyItemInserted(messages.size - 1)
                                chatRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        } else {
                            Log.e("ChatActivity", "API error: ${response.errorBody()?.string()}")
                        }
                    }

                    override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                        Log.e("ChatActivity", "Failed to send message", t)
                    }
                })
            }
        }
    }
}
