package com.nordic.mediahub.api

import java.io.File
import kotlin.coroutines.Continuation
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitReleaseContractTest {
    @Test
    fun releaseRules_keepContinuationGenericDefinition() {
        assertGenericTypeKept(Continuation::class.java)
    }

    @Test
    fun releaseRules_keepResponseGenericDefinition() {
        assertGenericTypeKept(Response::class.java)
    }

    @Test
    fun releaseRules_keepCallGenericDefinition() {
        assertGenericTypeKept(Call::class.java)
    }

    @Test
    fun allServerApis_canBeParsedEagerlyWithoutNetworkRequests() {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1/")
            .addConverterFactory(GsonConverterFactory.create())
            .validateEagerly(true)
            .build()

        retrofit.create(NavidromeApi::class.java)
        retrofit.create(AudiobookShelfApi::class.java)
        retrofit.create(EmbyApi::class.java)
    }

    // JVM tests do not run R8. Guard the app-owned rules here, and also inspect
    // the minified APK signatures when changing Retrofit or release tooling.
    private fun assertGenericTypeKept(type: Class<*>) {
        val rules = File("proguard-rules.pro")
            .readLines(Charsets.UTF_8)
            .map { it.substringBefore('#').trim() }
        val keepDefinition = Regex(
            """^-keep(?:,[a-z]+)*\s+(?:class|interface)\s+${Regex.escape(type.name)}(?:\s*\{.*)?$"""
        )

        assertTrue(
            "R8 full mode needs a keep rule for the generic definition of ${type.name}",
            rules.any { keepDefinition.matches(it) }
        )
        assertTrue(
            "Retrofit needs the Signature attribute as well as kept generic definitions",
            rules.any { rule ->
                rule.startsWith("-keepattributes ") &&
                    rule.substringAfter(' ').split(',').any { it.trim() == "Signature" }
            }
        )
    }
}
