package cl.kcorp.syncpass.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "proyecto_cache")
data class ProyectoEntity(
    @PrimaryKey val idProyecto: Int,
    val idCoordinador: Int,
    val idUnidad: Int,
    val idArea: Int,
    val titulo: String,
    val descripcion: String?,
    val fechaInicio: String,
    val fechaTermino: String,
    val estado: String,
)

@Entity(tableName = "evento_cache")
data class EventoEntity(
    @PrimaryKey val idEvento: Int,
    val idProyecto: Int,
    val nombre: String,
    val descripcion: String?,
    val fechaInicio: String,
    val fechaTermino: String,
    val cuposMaximos: Int,
    val recinto: String?,
    val direccion: String?,
    val latitud: Double?,
    val longitud: Double?,
    val radioValidacionMetros: Int,
    val estado: String,
    val proyectoTitulo: String?,
)

@Entity(tableName = "inscripcion_cache")
data class InscripcionEntity(
    @PrimaryKey val idInscripcion: Int,
    val idUsuario: Int,
    val idEvento: Int,
    val estado: String,
    val fechaInscripcion: String?,
    val asistenciaEstado: String?,
    val encuestaCalificacion: Int?,
    val encuestaUtilidad: String?,
    val encuestaComentarios: String?,
)

@Entity(tableName = "inscrito_cache")
data class InscritoEntity(
    @PrimaryKey val idInscripcion: Int,
    val idEvento: Int,
    val idUsuario: Int,
    val nombre: String,
    val correo: String,
    val estado: String,
    val asistio: Boolean,
)

@Entity(
    tableName = "usuario_cache",
    indices = [Index(value = ["correo"], unique = true)],
)
data class UsuarioEntity(
    @PrimaryKey val idUsuario: Int,
    val idRol: Int,
    val nombreUsuario: String,
    val nombreMostrar: String,
    val correo: String,
    val estado: String,
    val rolNombre: String?,
)

@Entity(
    tableName = "pending_action",
    indices = [Index(value = ["clave"], unique = true)],
)
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val clave: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val intentos: Int = 0,
)

const val PENDING_ASISTENCIA = "ASISTENCIA"
const val PENDING_ENCUESTA = "ENCUESTA"
