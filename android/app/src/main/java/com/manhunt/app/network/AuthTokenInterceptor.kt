package com.manhunt.app.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds a Firebase Authentication ID token to every Realtime Database REST request.
 *
 * FirebaseAuth caches valid tokens and refreshes them when needed, so getIdToken(false)
 * avoids manually maintaining a timer or storing raw tokens in preferences.
 */
class AuthTokenInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
                ?: error("Firebase anonymous sign-in did not return a user")
            user.getIdToken(false).await().token
                ?: error("Firebase did not return an ID token")
        }

        // Firebase Realtime Database REST accepts ID tokens in the Authorization header.
        // Keep credentials out of URLs, where they can leak through logs and diagnostics.
        val authenticatedUrl = chain.request().url.newBuilder()
            .removeAllQueryParameters("auth")
            .build()

        val request = chain.request().newBuilder()
            .url(authenticatedUrl)
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(request)
    }
}
