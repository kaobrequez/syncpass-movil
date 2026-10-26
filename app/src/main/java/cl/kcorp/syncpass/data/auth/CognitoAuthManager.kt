package cl.kcorp.syncpass.data.auth

import android.content.Context
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserPool
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.NewPasswordContinuation
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler
import com.amazonaws.regions.Regions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AuthTokens(
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val username: String
)

class NewPasswordRequiredException(
    message: String,
    val requiredAttributes: List<String> = emptyList()
) : Exception(message)

class CognitoAuthManager(context: Context) {

    companion object {
        const val USER_POOL_ID = "us-east-1_Z0HYVjbrw"
        const val CLIENT_ID = "1p47ikalvv22ajhmvr3ssf5mv9"
        val REGION: Regions = Regions.US_EAST_1
    }

    private val userPool = CognitoUserPool(context, USER_POOL_ID, CLIENT_ID, null, REGION)

    suspend fun login(
        username: String,
        password: String,
        newPassword: String? = null,
        fullName: String? = null,
        phoneNumber: String? = null
    ): Result<AuthTokens> =
        suspendCancellableCoroutine { continuation ->
            val cognitoUser = userPool.getUser(username)
            val authHandler = object : AuthenticationHandler {
                override fun onSuccess(userSession: CognitoUserSession?, newDevice: CognitoDevice?) {
                    if (userSession != null) {
                        val tokens = AuthTokens(
                            idToken = userSession.idToken.jwtToken,
                            accessToken = userSession.accessToken.jwtToken,
                            refreshToken = userSession.refreshToken.token,
                            username = username
                        )
                        if (continuation.isActive) {
                            continuation.resume(Result.success(tokens))
                        }
                    } else {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("Sesión nula devuelta por AWS Cognito")))
                        }
                    }
                }

                override fun getAuthenticationDetails(
                    continuationAuth: AuthenticationContinuation?,
                    userId: String?
                ) {
                    val authDetails = AuthenticationDetails(userId ?: username, password, null)
                    continuationAuth?.setAuthenticationDetails(authDetails)
                    continuationAuth?.continueTask()
                }

                override fun getMFACode(continuationMfa: MultiFactorAuthenticationContinuation?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("MFA (Doble factor) requerido en Cognito")))
                    }
                }

                override fun authenticationChallenge(continuationChallenge: ChallengeContinuation?) {
                    if (continuationChallenge is NewPasswordContinuation) {
                        val requiredAttrs = continuationChallenge.requiredAttributes ?: emptyList()

                        if (!newPassword.isNullOrBlank()) {
                            try {
                                continuationChallenge.setPassword(newPassword)

                                // Completar cualquier atributo obligatorio configurado en Cognito
                                for (attr in requiredAttrs) {
                                    when (attr) {
                                        "name" -> {
                                            val nameToUse = if (!fullName.isNullOrBlank()) fullName else username
                                            continuationChallenge.setUserAttribute("name", nameToUse)
                                        }
                                        "phone_number" -> {
                                            val phoneToUse = if (!phoneNumber.isNullOrBlank()) phoneNumber else "+56912345678"
                                            continuationChallenge.setUserAttribute("phone_number", phoneToUse)
                                        }
                                        "email" -> {
                                            if (username.contains("@")) {
                                                continuationChallenge.setUserAttribute("email", username)
                                            }
                                        }
                                        else -> {
                                            val valueToUse = if (!fullName.isNullOrBlank()) fullName else username
                                            continuationChallenge.setUserAttribute(attr, valueToUse)
                                        }
                                    }
                                }

                                continuationChallenge.continueTask()
                            } catch (ex: Exception) {
                                if (continuation.isActive) {
                                    continuation.resume(
                                        Result.failure(
                                            Exception("Error al actualizar contraseña en Cognito: ${ex.localizedMessage}", ex)
                                        )
                                    )
                                }
                            }
                        } else {
                            if (continuation.isActive) {
                                continuation.resume(
                                    Result.failure(
                                        NewPasswordRequiredException(
                                            message = "Tu cuenta requiere cambiar la contraseña obligatoriamente antes de continuar.",
                                            requiredAttributes = requiredAttrs
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(
                                    Exception("Desafío de autenticación de Cognito: ${continuationChallenge?.challengeName}")
                                )
                            )
                        }
                    }
                }

                override fun onFailure(exception: Exception?) {
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(exception ?: Exception("Error en la autenticación con Cognito"))
                        )
                    }
                }
            }

            cognitoUser.getSessionInBackground(authHandler)
        }
}