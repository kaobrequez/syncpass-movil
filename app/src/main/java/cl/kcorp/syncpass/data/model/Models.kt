package cl.kcorp.syncpass.data.model

data class Usuario(
    val idUsuario: Int = 0,
    val idRol: Int = 0,
    val nombreUsuario: String = "",
    val nombreMostrar: String = "",
    val correo: String = "",
    val estado: String = "",
)

data class Proyecto(
    val idProyecto: Int = 0,
    val idCoordinador: Int = 0,
    val idUnidad: Int = 0,
    val idArea: Int = 0,
    val titulo: String = "",
    val descripcion: String? = null,
    val fechaInicio: String = "",
    val fechaTermino: String = "",
    val estado: String = "",
)

data class ProyectoRef(
    val titulo: String = "",
)

data class Evento(
    val idEvento: Int = 0,
    val idProyecto: Int = 0,
    val nombre: String = "",
    val descripcion: String? = null,
    val fechaInicio: String = "",
    val fechaTermino: String = "",
    val cuposMaximos: Int = 0,
    val recinto: String? = null,
    val direccion: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val radioValidacionMetros: Int = 100,
    val estado: String = "",
    val proyecto: ProyectoRef? = null,
)

data class EventoInput(
    val nombre: String,
    val descripcion: String? = null,
    val fechaInicio: String,
    val fechaTermino: String,
    val cuposMaximos: Int = 25,
    val recinto: String? = null,
    val direccion: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val radioValidacionMetros: Int = 100,
    val estado: String = "PROGRAMADO",
)

data class AsistenciaRef(
    val estadoValidacion: String = "",
)

data class Encuesta(
    val calificacion: Int = 0,
    val utilidad: String = "",
    val comentarios: String? = null,
)

data class Inscripcion(
    val idInscripcion: Int = 0,
    val idUsuario: Int = 0,
    val idEvento: Int = 0,
    val estado: String = "",
    val fechaInscripcion: String? = null,
    val asistencia: AsistenciaRef? = null,
    val encuesta: Encuesta? = null,
)

data class Inscrito(
    val idInscripcion: Int = 0,
    val idUsuario: Int = 0,
    val idEvento: Int = 0,
    val nombre: String = "",
    val correo: String = "",
    val estado: String = "",
    val asistio: Boolean = false,
)

data class MetricasEvento(
    val totalInscritos: Int = 0,
    val totalAsistencias: Int = 0,
    val porcentajeAsistencia: Int = 0,
    val encuestasRespondidas: Int = 0,
    val porcentajeEncuestas: Int = 0,
    val satisfaccionPromedio: Double = 0.0,
    val comentarios: List<String> = emptyList(),
)

data class UsuarioItem(
    val idUsuario: Int = 0,
    val nombreUsuario: String = "",
    val nombreMostrar: String = "",
    val correo: String = "",
    val idRol: Int = 0,
    val rol: String = "",
    val estado: String = "",
)

data class Rol(
    val idRol: Int = 0,
    val nombre: String = "",
    val descripcion: String? = null,
)
