package cl.kcorp.syncpass.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor para Retrofit/OkHttp que adjunta automáticamente el token de Cognito
 * en la cabecera "Authorization: Bearer <token>" para hablar con tu backend en EC2.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        val token = tokenProvider()
        if (!token.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}