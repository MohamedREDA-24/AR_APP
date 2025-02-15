package com.xperiencelabs.arapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import java.io.InputStream
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
    private lateinit var uploadImageButton: Button
    private var latestImageUrl: String? = null
    private var sessionId: String? = null

    val imageUrlList = mutableListOf<String>()
    val imageUrlList2d = mutableListOf<String>()

    private companion object {
        const val IMAGE_PICK_REQUEST_CODE = 1001
        const val RECOMMEND_URL = "http://13.92.86.232/recommend/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Initialize views
        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        visualize3DButton = findViewById(R.id.btn_visualize_3d)
        uploadImageButton = findViewById(R.id.btn_upload_image)

        // Setup initial states
        visualize3DButton.isEnabled = false
        sendButton.isEnabled = false

        // Configure RecyclerView
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.adapter = chatAdapter

        // Initialize session
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

        // Set up button click listeners
        sendButton.setOnClickListener { handleSendMessage() }
        visualize3DButton.setOnClickListener { handle3DVisualization() }
        uploadImageButton.setOnClickListener { openImagePicker() }
    }

    private fun handleSendMessage() {
        val userMessage = messageInput.text.toString().trim()
        if (userMessage.isEmpty()) return

        if (sessionId == null) {
            Toast.makeText(this, "Session not initialized", Toast.LENGTH_SHORT).show()
            return
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

    private fun handle3DVisualization() {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (imageUrlList.isNotEmpty()) {
                putStringArrayListExtra("IMAGE_URL_LIST", ArrayList(imageUrlList))
                putStringArrayListExtra("IMAGE_URL_LIST_2D", ArrayList(imageUrlList2d))
            } else {
                latestImageUrl?.let {
                    putExtra("IMAGE_URL", it.replace(".jpg", ".glb"))
                }
            }
        }
        startActivity(intent)
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(message = text, isSent = true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        messageInput.text.clear()
        chatRecyclerView.smoothScrollToPosition(messages.size - 1)
    }

    // Region: Network Operations
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

    // Region: Image Upload Handling
    private fun openImagePicker() {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(this, IMAGE_PICK_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Display the selected image in the chat immediately
                addUserImageMessage(uri)

                // Continue with uploading the image
                activityScope.launch {
                    try {
                        val response = uploadImage(uri)
                        handleRecommendationResponse(response)
                    } catch (e: Exception) {
                        handleError(e)
                    }
                }
            }
        }
    }
    private fun addUserImageMessage(uri: Uri) {
        // Create a chat message using the image URI.
        // Make sure your ChatAdapter and ChatMessage model can handle image messages.
        messages.add(ChatMessage(imageUrl = uri.toString(), isSent = true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        chatRecyclerView.smoothScrollToPosition(messages.size - 1)
    }

    private suspend fun uploadImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val url = URL(RECOMMEND_URL)
        val connection = url.openConnection() as HttpURLConnection
        val boundary = "Boundary-${System.currentTimeMillis()}"

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("accept", "application/json")

            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getFileNameFromUri(uri) ?: "uploaded_image.jpg"
                writer.append("--$boundary\r\n")
                    .append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                    .append("Content-Type: image/jpeg\r\n\r\n")
                    .flush()

                inputStream.copyTo(outputStream)
                outputStream.flush()
            }

            writer.append("\r\n--$boundary--\r\n").flush()

            return@withContext if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "Error: ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                ) else null
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }

    // Region: Response Handling
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

    private fun handleServerResponse(response: String) {
        try {
            Log.d("ChatActivity", "Parsing response: $response")
            val jsonObject = JSONObject(response)

            // Update session ID if present
            jsonObject.optString("session_id", "")?.takeIf { it.isNotEmpty() }?.let {
                sessionId = it
                Log.d("ChatActivity", "Updated session ID: $it")
            }

            // Handle the assistant's text response
            val assistantResponse = jsonObject.optString("assistant_response", "")
            if (assistantResponse.isNotEmpty()) {
                runOnUiThread {
                    messages.add(
                        ChatMessage(
                            message = assistantResponse,
                            isSent = false,
                            isReply = true
                        )
                    )
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }

            // New: Handle internal_data images if available
            if (jsonObject.has("internal_data")) {
                val internalData = jsonObject.getJSONObject("internal_data")
                Log.d("---------------ChatActivity", "---------------------------Recommendation response: $internalData")

                if (internalData.has("images")) {
                    val imagesArray = internalData.getJSONArray("images")
                    for (i in 0 until imagesArray.length()) {
                        val imageUrl = imagesArray.getString(i)
                        // Update your image lists. Here, we assume these images are 2D.
                        latestImageUrl = imageUrl
                        imageUrlList2d.add(imageUrl)
                        // Optionally, if you want to use these for 3D visualization, add to imageUrlList as needed.
                         imageUrlList.add(imageUrl.replace(".jpg",".glb"))

                        runOnUiThread {
                            visualize3DButton.isEnabled = true // modified: enable 3D visualize button after retrieving image
                            messages.add(
                                ChatMessage(
                                    imageUrl = imageUrl,
                                    isSent = false,
                                    isReply = true
                                )
                            )
                            chatAdapter.notifyItemInserted(messages.size - 1)
                            chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                }
            }

            // Optionally, if you have additional data (e.g., "content" or "content_scrapped")
            // you can continue handling it using your existing method.
            val apiResponse = Gson().fromJson(response, ApiResponse::class.java)
            handleApiImages(apiResponse)
        } catch (e: Exception) {
            Log.e("ChatActivity", "Response handling failed", e)
            handleError(e)
        }
    }

    private fun handleApiImages(apiResponse: ApiResponse) {
        apiResponse.content?.let { items ->
            items.forEach { item ->
                item.image_2d?.let {
                    latestImageUrl = it
                    imageUrlList2d.add(it)
                    visualize3DButton.isEnabled = true // modified: enable 3D visualize button after retrieving image
                }
                item.image_3d?.let { imageUrlList.add(it) }

                runOnUiThread {

                    messages.add(ChatMessage(
                        imageUrl = item.image_2d,
                        isSent = false,
                        isReply = true
                    ))

                    chatAdapter.notifyItemInserted(messages.size - 1)
                    chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        } ?: run {
            apiResponse.content_scrapped?.let {
                runOnUiThread {
                    messages.add(ChatMessage(
                        message = it,
                        isSent = false,
                        isReply = true
                    ))
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun handleRecommendationResponse(response: String) {
        try {
            Log.d("ChatActivity", "Recommendation response: $response")
            val json = JSONObject(response)
            val recommendation = json.optString("recommendation", "")

            if (recommendation.isNotEmpty()) {
                runOnUiThread {
                    messages.add(ChatMessage(
                        message = recommendation,
                        isSent = false,
                        isReply = true
                    ))
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatActivity", "Error handling recommendation", e)
            handleError(e)
        }
    }

    private fun handleError(error: Exception) {
        runOnUiThread {
            Toast.makeText(this, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}
