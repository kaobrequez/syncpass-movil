package cl.kcorp.syncpass.data.auth

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

enum class Role(val label: String) {
    ADMINISTRADOR("Administrador"),
    DOCENTE("Docente"),
    VOLUNTARIO("Voluntario"),
    BENEFICIARIO("Beneficiario"),
    DESCONOCIDO("Desconocido"),
}

data class UserSession(
    val tokens: AuthTokens,
    val role: Role,
    val email: String,
    val name: String,
)

object JwtUtils {

    private val roleMap = mapOf(
        "administrador" to Role.ADMINISTRADOR,
        "admin" to Role.ADMINISTRADOR,
        "docente" to Role.DOCENTE,
        "profesor" to Role.DOCENTE,
        "voluntario" to Role.VOLUNTARIO,
        "colaborador" to Role.VOLUNTARIO,
        "beneficiario" to Role.BENEFICIARIO,
        "cliente" to Role.BENEFICIARIO,
        "estudiante" to Role.BENEFICIARIO,
    )

    fun decodePayload(token: String?): JSONObject? {
        if (token.isNullOrBlank()) return null
        return try {
            val parts = token.split('.')
            if (parts.size != 3) return null
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(decoded, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun groups(idToken: String?, accessToken: String?): List<String> {
        val accessGroups = decodePayload(accessToken)
            ?.optJSONArray("cognito:groups")
            ?.takeIf { it.length() > 0 }
        val idGroups = decodePayload(idToken)
            ?.optJSONArray("cognito:groups")
            ?.takeIf { it.length() > 0 }
        val array: JSONArray? = accessGroups ?: idGroups
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    fun role(idToken: String?, accessToken: String?): Role {
        val first = groups(idToken, accessToken).firstOrNull()?.lowercase() ?: return Role.DESCONOCIDO
        return roleMap[first] ?: Role.DESCONOCIDO
    }

    fun email(idToken: String?, accessToken: String?): String {
        val id = decodePayload(idToken)
        val access = decodePayload(accessToken)
        return id?.optString("email").orEmpty().ifBlank {
            access?.optString("email").orEmpty()
        }
    }

    fun name(idToken: String?, accessToken: String?): String {
        val id = decodePayload(idToken)
        val access = decodePayload(accessToken)
        val candidates = listOf(
            id?.optString("given_name"),
            id?.optString("name"),
            id?.optString("preferred_username"),
            id?.optString("nickname"),
            access?.optString("username"),
            email(idToken, accessToken).substringBefore("@"),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() } ?: "Usuario"
    }

    fun session(tokens: AuthTokens): UserSession = UserSession(
        tokens = tokens,
        role = role(tokens.idToken, tokens.accessToken),
        email = email(tokens.idToken, tokens.accessToken),
        name = name(tokens.idToken, tokens.accessToken),
    )
}
