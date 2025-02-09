package com.xperiencelabs.arapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatApiService {
    @POST("chat") // Adjust the path according to your FastAPI route
    fun sendMessage(@Body request: MessageRequest): Call<MessageResponse>
}
