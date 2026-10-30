package cl.kcorp.syncpass.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import cl.kcorp.syncpass.data.auth.Role
import cl.kcorp.syncpass.data.local.PENDING_ASISTENCIA
import cl.kcorp.syncpass.data.local.PENDING_ENCUESTA
import cl.kcorp.syncpass.data.local.PendingActionEntity
import cl.kcorp.syncpass.data.local.SyncPassDao
import cl.kcorp.syncpass.data.local.SyncPassDatabase
import cl.kcorp.syncpass.data.local.toEntity
import cl.kcorp.syncpass.data.local.toItem
import cl.kcorp.syncpass.data.local.toModel
import cl.kcorp.syncpass.data.model.AsistenciaRef
import cl.kcorp.syncpass.data.model.Encuesta
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.Inscripcion
import cl.kcorp.syncpass.data.model.Inscrito
import cl.kcorp.syncpass.data.model.MetricasEvento
import cl.kcorp.syncpass.data.model.Proyecto
import cl.kcorp.syncpass.data.model.Rol
import cl.kcorp.syncpass.data.model.Usuario
import cl.kcorp.syncpass.data.model.UsuarioItem
import cl.kcorp.syncpass.data.remote.SupabaseClient
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.data.remote.SupabaseException
import cl.kcorp.syncpass.data.sync.SyncScheduler
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object SyncPassRepository {

    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val syncMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var database: SyncPassDatabase? = null

    fun init(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database != null) return
            appContext = context.applicationContext
            database = SyncPassDatabase.get(context)
        }
    }

    private val dao: SyncPassDao? get() = database?.dao()

    private fun isOnline(): Boolean {
        val context = appContext ?: return true
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun <T> parseList(array: JsonArray, clazz: Class<T>): List<T> =
        array.mapNotNull { runCatching { gson.fromJson(it, clazz) }.getOrNull() }

    private fun nowIso(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private fun Throwable.isConnectivityError(): Boolean =
        this is IOException ||
            (this is SupabaseException && (code == null || status >= 500))

    /**
     * Reintenta una escritura quitando columnas que no existan en el esquema remoto.
     * Así el cliente sigue funcionando aunque la base tenga una versión anterior del DDL.
     */
    private suspend fun <T> retryWithoutUnknownColumns(body: JsonObject, block: suspend () -> T): T {
        var attempts = 0
        while (true) {
            try {
                return block()
            } catch (error: SupabaseException) {
                val missing = Regex("'([^']+)' column")
                    .find(error.message.orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                if (error.status == 400 && attempts < 5 && missing != null && body.has(missing)) {
                    body.remove(missing)
                    attempts++
                    continue
                }
                throw error
            }
        }
    }

    // ---------------------------------------------------------------------
    // Caché local
    // ---------------------------------------------------------------------

    private suspend fun cacheProyectos(idCoordinador: Int, items: List<Proyecto>) {
        val d = dao ?: return
        if (items.isEmpty()) {
            d.clearProyectos(idCoordinador)
            return
        }
        d.upsertProyectos(items.map { it.toEntity() })
        d.deleteStaleProyectos(idCoordinador, items.map { it.idProyecto })
    }

    private suspend fun cacheProyecto(item: Proyecto) {
        dao?.upsertProyectos(listOf(item.toEntity()))
    }

    private suspend fun cacheEventos(items: List<Evento>) {
        val d = dao ?: return
        if (items.isEmpty()) {
            d.clearEventos()
            return
        }
        d.upsertEventos(items.map { it.toEntity() })
        d.deleteStaleEventos(items.map { it.idEvento })
    }

    private suspend fun cacheEventosByProyecto(idProyecto: Int, items: List<Evento>) {
        val d = dao ?: return
        d.clearEventosByProyecto(idProyecto)
        if (items.isNotEmpty()) {
            d.upsertEventos(items.map { it.toEntity() })
        }
    }

    private suspend fun cacheEvento(item: Evento) {
        dao?.upsertEventos(listOf(item.toEntity()))
    }

    private suspend fun cacheInscripciones(idUsuario: Int, items: List<Inscripcion>) {
        val d = dao ?: return
        if (items.isEmpty()) {
            d.clearInscripciones(idUsuario)
            return
        }
        d.upsertInscripciones(items.map { it.toEntity() })
        d.deleteStaleInscripciones(idUsuario, items.map { it.idInscripcion })
    }

    private suspend fun cacheInscripcion(item: Inscripcion) {
        dao?.upsertInscripciones(listOf(item.toEntity()))
    }

    private suspend fun cacheInscritos(idEvento: Int, items: List<Inscrito>) {
        val d = dao ?: return
        if (items.isEmpty()) {
            d.clearInscritos(idEvento)
            return
        }
        d.upsertInscritos(items.map { it.toEntity() })
        d.deleteStaleInscritos(idEvento, items.map { it.idInscripcion })
    }

    private suspend fun cacheUsuarios(items: List<UsuarioItem>) {
        val d = dao ?: return
        if (items.isEmpty()) {
            d.clearUsuarios()
            return
        }
        d.upsertUsuarios(items.map { it.toEntity() })
        d.deleteStaleUsuarios(items.map { it.idUsuario })
    }

    private suspend fun cacheUsuario(usuario: Usuario, rolNombre: String?) {
        dao?.upsertUsuario(usuario.toEntity(rolNombre))
    }

    private suspend fun markAsistenciaLocal(idInscripcion: Int) {
        val d = dao ?: return
        d.markAsistencia(idInscripcion)
        d.markInscritoAsistio(idInscripcion)
    }

    private suspend fun saveEncuestaLocal(
        idInscripcion: Int,
        calificacion: Int,
        utilidad: String,
        comentarios: String?,
    ) {
        dao?.saveEncuestaLocal(idInscripcion, calificacion, utilidad, comentarios)
    }

    // ---------------------------------------------------------------------
    // Usuarios
    // ---------------------------------------------------------------------

    suspend fun getOrCreateUsuario(email: String, nombre: String, role: Role): Usuario? {
        if (email.isBlank()) return null
        val cached = dao?.usuarioByCorreo(email)?.toModel()
        if (!SupabaseConfig.isConfigured || !isOnline()) return cached

        return runCatching {
            val found = SupabaseClient.select("usuario", "select=*&correo=eq.${enc(email)}")
            val existing = found.firstOrNull()?.let { gson.fromJson(it, Usuario::class.java) }
            if (existing != null) {
                cacheUsuario(existing, role.label)
                return@runCatching existing
            }

            val bdRoleName = when (role) {
                Role.ADMINISTRADOR -> "Administrador"
                Role.DOCENTE -> "Docente"
                Role.VOLUNTARIO -> "Voluntario"
                Role.BENEFICIARIO -> "Beneficiario"
                Role.DESCONOCIDO -> "Beneficiario"
            }
            val rol = SupabaseClient.select("rol", "select=id_rol&nombre=eq.${enc(bdRoleName)}")
                .firstOrNull()
                ?.asJsonObject
                ?.get("id_rol")
                ?.asInt
                ?: 1

            val body = JsonObject().apply {
                addProperty("id_rol", rol)
                addProperty("nombre_usuario", email.substringBefore("@") + "_" + (System.currentTimeMillis() % 10_000))
                addProperty("nombre_mostrar", nombre.ifBlank { email })
                addProperty("correo", email)
                addProperty("estado", "ACTIVO")
            }
            val created = retryWithoutUnknownColumns(body) {
                SupabaseClient.insert("usuario", body)
            }
            created.firstOrNull()
                ?.let { gson.fromJson(it, Usuario::class.java) }
                ?.also { cacheUsuario(it, bdRoleName) }
        }.getOrNull() ?: cached
    }

    suspend fun getUsuarios(): List<UsuarioItem> {
        val cached = dao?.usuarios()?.map { it.toItem() } ?: emptyList()
        if (!SupabaseConfig.isConfigured || !isOnline()) return cached

        return runCatching {
            val data = SupabaseClient.select(
                "usuario",
                "select=id_usuario,nombre_usuario,nombre_mostrar,correo,id_rol,estado,rol:rol(nombre)&order=nombre_mostrar.asc",
            )
            val items = data.mapNotNull { element ->
                val obj = element.asJsonObject
                val rol = when (val rolEl = obj.get("rol")) {
                    is JsonObject -> rolEl.get("nombre")?.asString.orEmpty()
                    is JsonArray -> rolEl.firstOrNull()?.asJsonObject?.get("nombre")?.asString.orEmpty()
                    else -> ""
                }
                UsuarioItem(
                    idUsuario = obj.get("id_usuario")?.asInt ?: 0,
                    nombreUsuario = obj.get("nombre_usuario")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    nombreMostrar = obj.get("nombre_mostrar")?.asString.orEmpty(),
                    correo = obj.get("correo")?.asString.orEmpty(),
                    idRol = obj.get("id_rol")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    rol = rol,
                    estado = obj.get("estado")?.asString.orEmpty(),
                )
            }
            cacheUsuarios(items)
            items
        }.getOrElse { cached }
    }

    suspend fun getRoles(): List<Rol> {
        if (!SupabaseConfig.isConfigured || !isOnline()) return emptyList()
        return runCatching {
            val data = SupabaseClient.select("rol", "select=id_rol,nombre,descripcion&order=id_rol.asc")
            parseList(data, Rol::class.java)
        }.getOrElse { emptyList() }
    }

    suspend fun createUsuario(
        idRol: Int,
        nombreUsuario: String,
        nombreMostrar: String,
        correo: String,
        estado: String = "ACTIVO",
        rolNombre: String? = null,
    ): Boolean {
        if (!SupabaseConfig.isConfigured || !isOnline()) return false
        return runCatching {
            val body = JsonObject().apply {
                addProperty("id_rol", idRol)
                addProperty("nombre_usuario", nombreUsuario)
                addProperty("nombre_mostrar", nombreMostrar)
                addProperty("correo", correo)
                addProperty("estado", estado)
            }
            val created = retryWithoutUnknownColumns(body) {
                SupabaseClient.insert("usuario", body)
            }.firstOrNull()?.let { gson.fromJson(it, Usuario::class.java) }
            if (created != null) {
                cacheUsuario(created, rolNombre)
                true
            } else {
                false
            }
        }.getOrElse { false }
    }

    suspend fun updateUsuario(
        idUsuario: Int,
        idRol: Int,
        nombreUsuario: String,
        nombreMostrar: String,
        correo: String,
        estado: String,
        rolNombre: String? = null,
    ): Boolean {
        if (!SupabaseConfig.isConfigured || !isOnline()) return false
        return runCatching {
            val body = JsonObject().apply {
                addProperty("id_rol", idRol)
                addProperty("nombre_usuario", nombreUsuario)
                addProperty("nombre_mostrar", nombreMostrar)
                addProperty("correo", correo)
                addProperty("estado", estado)
            }
            val updated = retryWithoutUnknownColumns(body) {
                SupabaseClient.update("usuario", "id_usuario=eq.$idUsuario", body)
            }.firstOrNull()?.let { gson.fromJson(it, Usuario::class.java) }
            if (updated != null) {
                cacheUsuario(updated, rolNombre)
                true
            } else {
                false
            }
        }.getOrElse { false }
    }

    suspend fun deleteUsuario(idUsuario: Int): Boolean {
        if (!SupabaseConfig.isConfigured || !isOnline()) return false
        return runCatching {
            SupabaseClient.delete("usuario", "id_usuario=eq.$idUsuario")
            dao?.deleteUsuario(idUsuario)
            true
        }.getOrElse { false }
    }

    // ---------------------------------------------------------------------
    // Proyectos
    // ---------------------------------------------------------------------

    suspend fun getProyectosByCoordinador(idUsuario: Int): List<Proyecto> {
        val cached = dao?.proyectosByCoordinador(idUsuario)?.map { it.toModel() } ?: emptyList()
        if (!SupabaseConfig.isConfigured || !isOnline()) return cached

        return runCatching {
            val data = SupabaseClient.select(
                "proyecto",
                "select=*&id_coordinador=eq.$idUsuario&order=fecha_inicio.desc",
            )
            val items = parseList(data, Proyecto::class.java)
            cacheProyectos(idUsuario, items)
            items
        }.getOrElse { cached }
    }

    suspend fun createProyecto(
        idCoordinador: Int,
        titulo: String,
        descripcion: String?,
        fechaInicio: String,
        fechaTermino: String,
        estado: String = "PLANIFICADO",
    ): Proyecto? {
        if (!SupabaseConfig.isConfigured || !isOnline()) return null
        return runCatching {
            val body = JsonObject().apply {
                addProperty("id_coordinador", idCoordinador)
                addProperty("id_unidad", 1)
                addProperty("id_area", 1)
                addProperty("titulo", titulo)
                if (descripcion.isNullOrBlank()) add("descripcion", JsonNull.INSTANCE) else addProperty("descripcion", descripcion)
                addProperty("fecha_inicio", fechaInicio)
                addProperty("fecha_termino", fechaTermino)
                addProperty("estado", estado)
            }
            retryWithoutUnknownColumns(body) {
                SupabaseClient.insert("proyecto", body)
            }.firstOrNull()
                ?.let { gson.fromJson(it, Proyecto::class.java) }
                ?.also { cacheProyecto(it) }
        }.getOrNull()
    }

    suspend fun updateProyecto(
        idProyecto: Int,
        titulo: String,
        descripcion: String?,
        fechaInicio: String,
        fechaTermino: String,
        estado: String,
    ): Proyecto? {
        if (!SupabaseConfig.isConfigured || !isOnline()) return null
        return runCatching {
            val body = JsonObject().apply {
                addProperty("titulo", titulo)
                if (descripcion.isNullOrBlank()) add("descripcion", JsonNull.INSTANCE) else addProperty("descripcion", descripcion)
                addProperty("fecha_inicio", fechaInicio)
                addProperty("fecha_termino", fechaTermino)
                addProperty("estado", estado)
            }
            retryWithoutUnknownColumns(body) {
                SupabaseClient.update("proyecto", "id_proyecto=eq.$idProyecto", body)
            }.firstOrNull()
                ?.let { gson.fromJson(it, Proyecto::class.java) }
                ?.also { cacheProyecto(it) }
        }.getOrNull()
    }

    suspend fun deleteProyecto(idProyecto: Int): Boolean {
        if (!SupabaseConfig.isConfigured || !isOnline()) return false
        return runCatching {
            SupabaseClient.delete("evento", "id_proyecto=eq.$idProyecto")
            SupabaseClient.delete("proyecto", "id_proyecto=eq.$idProyecto")
            dao?.deleteProyecto(idProyecto)
            dao?.clearEventosByProyecto(idProyecto)
            true
        }.getOrElse { false }
    }

    // ---------------------------------------------------------------------
    // Eventos
    // ---------------------------------------------------------------------

    suspend fun getEventos(): List<Evento> {
        val cached = dao?.eventos()?.map { it.toModel() } ?: emptyList()
        if (!SupabaseConfig.isConfigured || !isOnline()) return cached

        return runCatching {
            val data = SupabaseClient.select(
                "evento",
                "select=*,proyecto:proyecto(titulo)&order=fecha_inicio.asc",
            )
            val items = parseList(data, Evento::class.java)
            cacheEventos(items)
            items
        }.getOrElse { cached }
    }

    suspend fun getEventosByProyecto(idProyecto: Int): List<Evento> {
        val cached = dao?.eventosByProyecto(idProyecto)?.map { it.toModel() } ?: emptyList()
        if (!SupabaseConfig.isConfigured || !isOnline()) return cached

        return runCatching {
            val data = SupabaseClient.select(
                "evento",
                "select=*&id_proyecto=eq.$idProyecto&order=fecha_inicio.asc",
            )
            val items = parseList(data, Evento::class.java)
            cacheEventosByProyecto(idProyecto, items)
            items
        }.getOrElse { cached }
    }

    suspend fun createEvento(
        idProyecto: Int,
        nombre: String,
        descripcion: String?,
        fechaInicio: String,
        fechaTermino: String,
        cuposMaximos: Int,
        recinto: String?,
        direccion: String? = null,
        latitud: Double? = null,
        longitud: Double? = null,
        radioValidacionMetros: Int = 100,
        estado: String = "PROGRAMADO",
    ): Evento? {
        if (!SupabaseConfig.isConfigured || !isOnline()) return null
        return runCatching {
            val body = JsonObject().apply {
                addProperty("id_proyecto", idProyecto)
                addProperty("nombre", nombre)
                if (descripcion.isNullOrBlank()) add("descripcion", JsonNull.INSTANCE) else addProperty("descripcion", descripcion)
                addProperty("fecha_inicio", fechaInicio)
                addProperty("fecha_termino", fechaTermino)
                addProperty("cupos_maximos", cuposMaximos)
                if (recinto.isNullOrBlank()) add("recinto", JsonNull.INSTANCE) else addProperty("recinto", recinto)
                if (direccion.isNullOrBlank()) add("direccion", JsonNull.INSTANCE) else addProperty("direccion", direccion)
                if (latitud == null) add("latitud", JsonNull.INSTANCE) else addProperty("latitud", latitud)
                if (longitud == null) add("longitud", JsonNull.INSTANCE) else addProperty("longitud", longitud)
                addProperty("radio_validacion_metros", radioValidacionMetros)
                addProperty("estado", estado)
            }
            retryWithoutUnknownColumns(body) {
                SupabaseClient.insert("evento", body)
            }.firstOrNull()
                ?.let { gson.fromJson(it, Evento::class.java) }
                ?.also { cacheEvento(it) }
        }.getOrNull()
    }

    suspend fun updateEvento(
        idEvento: Int,
        nombre: String,
        descripcion: String?,
        fechaInicio: String,
        fechaTermino: String,
        cuposMaximos: Int,
        recinto: String?,
        direccion: String? = null,
        latitud: Double? = null,
        longitud: Double? = null,
        radioValidacionMetros: Int = 100,
        estado: String,
    ): Evento? {
        if (!SupabaseConfig.isConfigured || !isOnline()) return null
        return runCatching {
            val body = JsonObject().apply {
                addProperty("nombre", nombre)
                if (descripcion.isNullOrBlank()) add("descripcion", JsonNull.INSTANCE) else addProperty("descripcion", descripcion)
                addProperty("fecha_inicio", fechaInicio)
                addProperty("fecha_termino", fechaTermino)
                addProperty("cupos_maximos", cuposMaximos)
                if (recinto.isNullOrBlank()) add("recinto", JsonNull.INSTANCE) else addProperty("recinto", recinto)
                if (direccion.isNullOrBlank()) add("direccion", JsonNull.INSTANCE) else addProperty("direccion", direccion)
                if (latitud == null) add("latitud", JsonNull.INSTANCE) else addProperty("latitud", latitud)
                if (longitud == null) add("longitud", JsonNull.INSTANCE) else addProperty("longitud", longitud)
                addProperty("radio_validacion_metros", radioValidacionMetros)
                addProperty("estado", estado)
            }
            retryWithoutUnknownColumns(body) {
                SupabaseClient.update("evento", "id_evento=eq.$idEvento", body)
            }.firstOrNull()
                ?.let { gson.fromJson(it, Evento::class.java) }
                ?.also { cacheEvento(it) }
        }.getOrNull()
    }

    suspend fun deleteEvento(idEvento: Int): Boolean {
        if (!SupabaseConfig.isConfigured || !isOnline()) return false
        return runCatching {
            SupabaseClient.delete("evento", "id_evento=eq.$idEvento")
            dao?.deleteEvento(idEvento)
            true
        }.getOrElse { false }
    }

    // ---------------------------------------------------------------------
    // Inscripciones
    // ---------------------------------------------------------------------

    suspend fun getInscripciones(idUsuario: Int): List<Inscripcion> {
        val cached = dao?.inscripcionesByUsuario(idUsuario)?.map { it.toModel() } ?: emptyList()
        if (!SupabaseConfig.isConfigured || !isOnline()) return cached

        return runCatching {
            val data = SupabaseClient.select(
                "inscripcion",
                "select=*,asistencia:asistencia(estado_validacion),encuesta:encuesta(calificacion,utilidad,comentarios)&id_usuario=eq.$idUsuario",
            )
            val items = data.mapNotNull { parseInscripcion(it.asJsonObject) }
            cacheInscripciones(idUsuario, items)
            items
        }.getOrElse { cached }
    }

    private fun parseInscripcion(obj: JsonObject): Inscripcion? {
        val id = obj.get("id_inscripcion")?.takeIf { !it.isJsonNull }?.asInt ?: return null
        val asistencia = when (val el = obj.get("asistencia")) {
            is JsonObject -> el.get("estado_validacion")?.takeIf { !it.isJsonNull }?.asString
                ?.let { AsistenciaRef(it) }
            is JsonArray -> el.firstOrNull()?.asJsonObject?.get("estado_validacion")
                ?.takeIf { !it.isJsonNull }?.asString?.let { AsistenciaRef(it) }
            else -> null
        }
        val encuesta = when (val el = obj.get("encuesta")) {
            is JsonObject -> parseEncuesta(el)
            is JsonArray -> el.firstOrNull()?.asJsonObject?.let { parseEncuesta(it) }
            else -> null
        }
        return Inscripcion(
            idInscripcion = id,
            idUsuario = obj.get("id_usuario")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            idEvento = obj.get("id_evento")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            estado = obj.get("estado")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            fechaInscripcion = obj.get("fecha_inscripcion")?.takeIf { !it.isJsonNull }?.asString,
            asistencia = asistencia,
            encuesta = encuesta,
        )
    }

    private fun parseEncuesta(obj: JsonObject): Encuesta? {
        val calificacion = obj.get("calificacion")?.takeIf { !it.isJsonNull }?.asInt ?: return null
        return Encuesta(
            calificacion = calificacion,
            utilidad = obj.get("utilidad")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            comentarios = obj.get("comentarios")?.takeIf { !it.isJsonNull }?.asString,
        )
    }

    suspend fun inscribirse(idUsuario: Int, idEvento: Int): Inscripcion? {
        if (!SupabaseConfig.isConfigured || !isOnline()) return null
        return runCatching {
            val body = JsonObject().apply {
                addProperty("id_usuario", idUsuario)
                addProperty("id_evento", idEvento)
                addProperty("estado", "INSCRITO")
            }
            retryWithoutUnknownColumns(body) {
                SupabaseClient.upsert("inscripcion", body, "id_usuario,id_evento")
            }.firstOrNull()?.asJsonObject?.let { parseInscripcion(it) }
                ?.also { cacheInscripcion(it) }
        }.getOrNull()
    }

    suspend fun desinscribirse(idUsuario: Int, idEvento: Int): Boolean {
        if (!SupabaseConfig.isConfigured || !isOnline()) return false
        return runCatching {
            val body = JsonObject().apply { addProperty("estado", "CANCELADO") }
            retryWithoutUnknownColumns(body) {
                SupabaseClient.update(
                    "inscripcion",
                    "id_usuario=eq.$idUsuario&id_evento=eq.$idEvento",
                    body,
                )
            }
            dao?.markInscripcionCancelada(idUsuario, idEvento)
            true
        }.getOrElse { false }
    }

    suspend fun getInscritosByEvento(idEvento: Int): List<Inscrito> {
        val cached = dao?.inscritosByEvento(idEvento)?.map { it.toModel() } ?: emptyList()
        if (!SupabaseConfig.isConfigured || !isOnline()) return cached

        return runCatching {
            val data = SupabaseClient.select(
                "inscripcion",
                "select=id_inscripcion,id_usuario,estado,usuario:usuario(nombre_mostrar,correo),asistencia:asistencia(estado_validacion)&id_evento=eq.$idEvento",
            )
            val items = data.mapNotNull { element ->
                val obj = element.asJsonObject
                val usuario = when (val el = obj.get("usuario")) {
                    is JsonObject -> el
                    is JsonArray -> el.firstOrNull()?.asJsonObject
                    else -> null
                }
                val asistio = when (val el = obj.get("asistencia")) {
                    is JsonObject -> el.get("estado_validacion")?.asString == "VALIDADA"
                    is JsonArray -> el.any {
                        it.isJsonObject && it.asJsonObject.get("estado_validacion")?.asString == "VALIDADA"
                    }
                    else -> false
                }
                Inscrito(
                    idInscripcion = obj.get("id_inscripcion")?.asInt ?: 0,
                    idUsuario = obj.get("id_usuario")?.asInt ?: 0,
                    idEvento = idEvento,
                    nombre = usuario?.get("nombre_mostrar")?.takeIf { !it.isJsonNull }?.asString ?: "-",
                    correo = usuario?.get("correo")?.takeIf { !it.isJsonNull }?.asString ?: "-",
                    estado = obj.get("estado")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    asistio = asistio,
                )
            }
            cacheInscritos(idEvento, items)
            items
        }.getOrElse { cached }
    }

    // ---------------------------------------------------------------------
    // Asistencia y encuestas
    // ---------------------------------------------------------------------

    suspend fun registrarAsistencia(
        idInscripcion: Int,
        latitud: Double? = null,
        longitud: Double? = null,
    ): Result<Boolean> {
        val fechaMarcacion = nowIso()
        val identificador = UUID.randomUUID().toString()

        if (!SupabaseConfig.isConfigured || !isOnline()) {
            if (database == null) {
                return Result.failure(IllegalStateException("Sin conexión y sin almacenamiento local disponible"))
            }
            enqueueAsistencia(idInscripcion, latitud, longitud, fechaMarcacion, identificador)
            markAsistenciaLocal(idInscripcion)
            return Result.success(false)
        }

        return registrarAsistenciaRemote(
            idInscripcion = idInscripcion,
            latitud = latitud,
            longitud = longitud,
            fechaMarcacion = fechaMarcacion,
            identificador = identificador,
        ).fold(
            onSuccess = {
                markAsistenciaLocal(idInscripcion)
                Result.success(true)
            },
            onFailure = { error ->
                if (error.isConnectivityError()) {
                    enqueueAsistencia(idInscripcion, latitud, longitud, fechaMarcacion, identificador)
                    markAsistenciaLocal(idInscripcion)
                    Result.success(false)
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    private suspend fun registrarAsistenciaRemote(
        idInscripcion: Int,
        latitud: Double?,
        longitud: Double?,
        fechaMarcacion: String,
        identificador: String,
    ): Result<Unit> = runCatching {
        val body = JsonObject().apply {
            addProperty("id_inscripcion", idInscripcion)
            addProperty("fecha_marcacion", fechaMarcacion)
            if (latitud == null) add("latitud", JsonNull.INSTANCE) else addProperty("latitud", latitud)
            if (longitud == null) add("longitud", JsonNull.INSTANCE) else addProperty("longitud", longitud)
            addProperty("estado_validacion", "VALIDADA")
            addProperty("estado_sincronizacion", "SINCRONIZADO")
            addProperty("identificador_operacion", identificador)
        }
        retryWithoutUnknownColumns(body) {
            SupabaseClient.insert("asistencia", body)
        }
        Unit
    }.recoverCatching { error ->
        val duplicate = error is SupabaseException &&
            (error.code == "23505" || error.message?.contains("duplicate", ignoreCase = true) == true)
        if (duplicate) {
            Unit
        } else {
            throw error
        }
    }

    private suspend fun enqueueAsistencia(
        idInscripcion: Int,
        latitud: Double?,
        longitud: Double?,
        fechaMarcacion: String,
        identificador: String,
    ) {
        val payload = AsistenciaPayload(
            idInscripcion = idInscripcion,
            latitud = latitud,
            longitud = longitud,
            fechaMarcacion = fechaMarcacion,
            identificadorOperacion = identificador,
        )
        dao?.enqueue(
            PendingActionEntity(
                tipo = PENDING_ASISTENCIA,
                clave = "$PENDING_ASISTENCIA:$idInscripcion",
                payload = gson.toJson(payload),
            ),
        )
        appContext?.let { SyncScheduler.syncNow(it) }
    }

    suspend fun registrarEncuesta(
        idInscripcion: Int,
        calificacion: Int,
        utilidad: String = "MUY_UTIL",
        comentarios: String? = null,
    ): Result<Boolean> {
        val identificador = UUID.randomUUID().toString()

        if (!SupabaseConfig.isConfigured || !isOnline()) {
            if (database == null) {
                return Result.failure(IllegalStateException("Sin conexión y sin almacenamiento local disponible"))
            }
            enqueueEncuesta(idInscripcion, calificacion, utilidad, comentarios, identificador)
            saveEncuestaLocal(idInscripcion, calificacion, utilidad, comentarios)
            return Result.success(false)
        }

        return registrarEncuestaRemote(
            idInscripcion = idInscripcion,
            calificacion = calificacion,
            utilidad = utilidad,
            comentarios = comentarios,
            identificador = identificador,
        ).fold(
            onSuccess = {
                saveEncuestaLocal(idInscripcion, calificacion, utilidad, comentarios)
                Result.success(true)
            },
            onFailure = { error ->
                if (error.isConnectivityError()) {
                    enqueueEncuesta(idInscripcion, calificacion, utilidad, comentarios, identificador)
                    saveEncuestaLocal(idInscripcion, calificacion, utilidad, comentarios)
                    Result.success(false)
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    private suspend fun registrarEncuestaRemote(
        idInscripcion: Int,
        calificacion: Int,
        utilidad: String,
        comentarios: String?,
        identificador: String,
    ): Result<Unit> = runCatching {
        val body = JsonObject().apply {
            addProperty("id_inscripcion", idInscripcion)
            addProperty("calificacion", calificacion)
            addProperty("utilidad", utilidad)
            if (comentarios.isNullOrBlank()) add("comentarios", JsonNull.INSTANCE) else addProperty("comentarios", comentarios)
            addProperty("identificador_operacion", identificador)
        }
        retryWithoutUnknownColumns(body) {
            SupabaseClient.upsert("encuesta", body, "id_inscripcion")
        }
        Unit
    }

    private suspend fun enqueueEncuesta(
        idInscripcion: Int,
        calificacion: Int,
        utilidad: String,
        comentarios: String?,
        identificador: String,
    ) {
        val payload = EncuestaPayload(
            idInscripcion = idInscripcion,
            calificacion = calificacion,
            utilidad = utilidad,
            comentarios = comentarios,
            identificadorOperacion = identificador,
        )
        dao?.enqueue(
            PendingActionEntity(
                tipo = PENDING_ENCUESTA,
                clave = "$PENDING_ENCUESTA:$idInscripcion",
                payload = gson.toJson(payload),
            ),
        )
        appContext?.let { SyncScheduler.syncNow(it) }
    }

    suspend fun syncPending(): Int = syncMutex.withLock {
        val d = dao ?: return@withLock 0
        if (!SupabaseConfig.isConfigured || !isOnline()) return@withLock 0

        var synced = 0
        d.pendingActions().forEach { action ->
            val result: Result<Unit> = when (action.tipo) {
                PENDING_ASISTENCIA -> {
                    val payload = runCatching {
                        gson.fromJson(action.payload, AsistenciaPayload::class.java)
                    }.getOrNull()
                    if (payload == null) {
                        Result.success(Unit)
                    } else {
                        registrarAsistenciaRemote(
                            idInscripcion = payload.idInscripcion,
                            latitud = payload.latitud,
                            longitud = payload.longitud,
                            fechaMarcacion = payload.fechaMarcacion,
                            identificador = payload.identificadorOperacion,
                        )
                    }
                }

                PENDING_ENCUESTA -> {
                    val payload = runCatching {
                        gson.fromJson(action.payload, EncuestaPayload::class.java)
                    }.getOrNull()
                    if (payload == null) {
                        Result.success(Unit)
                    } else {
                        registrarEncuestaRemote(
                            idInscripcion = payload.idInscripcion,
                            calificacion = payload.calificacion,
                            utilidad = payload.utilidad,
                            comentarios = payload.comentarios,
                            identificador = payload.identificadorOperacion,
                        )
                    }
                }

                else -> Result.success(Unit)
            }

            if (result.isSuccess) {
                d.deletePending(action)
                synced++
            } else {
                val error = result.exceptionOrNull()
                val permanent = error is SupabaseException &&
                    error.status in 400..499 &&
                    error.status != 408 &&
                    error.status != 429
                if (permanent) {
                    d.deletePending(action)
                } else {
                    d.incrementAttempts(action.id)
                }
            }
        }
        synced
    }

    suspend fun pendingCount(): Int = dao?.pendingCount() ?: 0

    suspend fun getMetricasEvento(idEvento: Int): MetricasEvento {
        val empty = MetricasEvento()
        if (!SupabaseConfig.isConfigured || !isOnline()) return empty
        return runCatching {
            val inscripciones = SupabaseClient.select(
                "inscripcion",
                "select=id_inscripcion&id_evento=eq.$idEvento",
            )
            val totalInscritos = inscripciones.size()
            val ids = inscripciones.mapNotNull {
                it.asJsonObject.get("id_inscripcion")?.takeIf { id -> !id.isJsonNull }?.asInt
            }
            var totalAsistencias = 0
            var encuestasRespondidas = 0
            var suma = 0
            val comentarios = mutableListOf<String>()

            if (ids.isNotEmpty()) {
                val inFilter = ids.joinToString(",")
                val asistencias = SupabaseClient.select(
                    "asistencia",
                    "select=id_asistencia&id_inscripcion=in.($inFilter)&estado_validacion=eq.VALIDADA",
                )
                totalAsistencias = asistencias.size()

                val encuestas = SupabaseClient.select(
                    "encuesta",
                    "select=calificacion,comentarios&id_inscripcion=in.($inFilter)",
                )
                encuestasRespondidas = encuestas.size()
                encuestas.forEach { element ->
                    val obj = element.asJsonObject
                    suma += obj.get("calificacion")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                    obj.get("comentarios")?.takeIf { !it.isJsonNull }?.asString
                        ?.takeIf { it.isNotBlank() }?.let { comentarios.add(it) }
                }
            }

            MetricasEvento(
                totalInscritos = totalInscritos,
                totalAsistencias = totalAsistencias,
                porcentajeAsistencia = if (totalInscritos > 0) (totalAsistencias * 100) / totalInscritos else 0,
                encuestasRespondidas = encuestasRespondidas,
                porcentajeEncuestas = if (totalInscritos > 0) (encuestasRespondidas * 100) / totalInscritos else 0,
                satisfaccionPromedio = if (encuestasRespondidas > 0) {
                    Math.round(suma.toDouble() / encuestasRespondidas * 100.0) / 100.0
                } else {
                    0.0
                },
                comentarios = comentarios,
            )
        }.getOrElse { empty }
    }

    private data class AsistenciaPayload(
        val idInscripcion: Int,
        val latitud: Double?,
        val longitud: Double?,
        val fechaMarcacion: String,
        val identificadorOperacion: String,
    )

    private data class EncuestaPayload(
        val idInscripcion: Int,
        val calificacion: Int,
        val utilidad: String,
        val comentarios: String?,
        val identificadorOperacion: String,
    )
}
