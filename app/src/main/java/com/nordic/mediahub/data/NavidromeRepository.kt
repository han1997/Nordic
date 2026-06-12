package com.nordic.mediahub.data

import com.nordic.mediahub.api.NavidromeApi
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NavidromeRepository(private val config: NavidromeConfig) {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", Credentials.basic(config.username, config.password))
                .build()
            chain.proceed(request)
        }
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(config.serverUrl.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NavidromeApi::class.java)

    suspend fun getRecentAlbums() = api.getAlbums(sort = "recently_added")
    suspend fun getRecentSongs() = api.getSongs()
    suspend fun getArtists() = api.getArtists()
}
