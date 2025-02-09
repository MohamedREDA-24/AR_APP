package com.xperiencelabs.arapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // If you're using the Android emulator, use 10.0.2.2 to refer to localhost
    private const val BASE_URL = "http://10.0.2.2:8000/" // Adjust port if necessary

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val chatApiService: ChatApiService = retrofit.create(ChatApiService::class.java)
}
