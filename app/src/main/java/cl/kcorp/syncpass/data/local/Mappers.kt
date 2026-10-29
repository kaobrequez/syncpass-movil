package cl.kcorp.syncpass.data.local

import cl.kcorp.syncpass.data.model.AsistenciaRef
import cl.kcorp.syncpass.data.model.Encuesta
import cl.kcorp.syncpass.data.model.Evento
import cl.kcorp.syncpass.data.model.Inscripcion
import cl.kcorp.syncpass.data.model.Inscrito
import cl.kcorp.syncpass.data.model.Proyecto
import cl.kcorp.syncpass.data.model.ProyectoRef
import cl.kcorp.syncpass.data.model.Usuario
import cl.kcorp.syncpass.data.model.UsuarioItem

fun Proyecto.toEntity(): ProyectoEntity = ProyectoEntity(
    idProyecto = idProyecto,
    idCoordinador = idCoordinador,
    idUnidad = idUnidad,
    idArea = idArea,
    titulo = titulo,
    descripcion = descripcion,
    fechaInicio = fechaInicio,
    fechaTermino = fechaTermino,
    estado = estado,
)

fun ProyectoEntity.toModel(): Proyecto = Proyecto(
    idProyecto = idProyecto,
    idCoordinador = idCoordinador,
    idUnidad = idUnidad,
    idArea = idArea,
    titulo = titulo,
    descripcion = descripcion,
    fechaInicio = fechaInicio,
    fechaTermino = fechaTermino,
    estado = estado,
)

fun Evento.toEntity(): EventoEntity = EventoEntity(
    idEvento = idEvento,
    idProyecto = idProyecto,
    nombre = nombre,
    descripcion = descripcion,
    fechaInicio = fechaInicio,
    fechaTermino = fechaTermino,
    cuposMaximos = cuposMaximos,
    recinto = recinto,
    direccion = direccion,
    latitud = latitud,
    longitud = longitud,
    radioValidacionMetros = radioValidacionMetros,
    estado = estado,
    proyectoTitulo = proyecto?.titulo,
)

fun EventoEntity.toModel(): Evento = Evento(
    idEvento = idEvento,
    idProyecto = idProyecto,
    nombre = nombre,
    descripcion = descripcion,
    fechaInicio = fechaInicio,
    fechaTermino = fechaTermino,
    cuposMaximos = cuposMaximos,
    recinto = recinto,
    direccion = direccion,
    latitud = latitud,
    longitud = longitud,
    radioValidacionMetros = radioValidacionMetros,
    estado = estado,
    proyecto = proyectoTitulo?.let { ProyectoRef(it) },
)

fun Inscripcion.toEntity(): InscripcionEntity = InscripcionEntity(
    idInscripcion = idInscripcion,
    idUsuario = idUsuario,
    idEvento = idEvento,
    estado = estado,
    fechaInscripcion = fechaInscripcion,
    asistenciaEstado = asistencia?.estadoValidacion,
    encuestaCalificacion = encuesta?.calificacion,
    encuestaUtilidad = encuesta?.utilidad,
    encuestaComentarios = encuesta?.comentarios,
)

fun InscripcionEntity.toModel(): Inscripcion = Inscripcion(
    idInscripcion = idInscripcion,
    idUsuario = idUsuario,
    idEvento = idEvento,
    estado = estado,
    fechaInscripcion = fechaInscripcion,
    asistencia = asistenciaEstado?.let { AsistenciaRef(it) },
    encuesta = encuestaCalificacion?.let {
        Encuesta(
            calificacion = it,
            utilidad = encuestaUtilidad.orEmpty(),
            comentarios = encuestaComentarios,
        )
    },
)

fun Inscrito.toEntity(): InscritoEntity = InscritoEntity(
    idInscripcion = idInscripcion,
    idEvento = idEvento,
    idUsuario = idUsuario,
    nombre = nombre,
    correo = correo,
    estado = estado,
    asistio = asistio,
)

fun InscritoEntity.toModel(): Inscrito = Inscrito(
    idInscripcion = idInscripcion,
    idUsuario = idUsuario,
    idEvento = idEvento,
    nombre = nombre,
    correo = correo,
    estado = estado,
    asistio = asistio,
)

fun Usuario.toEntity(rolNombre: String? = null): UsuarioEntity = UsuarioEntity(
    idUsuario = idUsuario,
    idRol = idRol,
    nombreUsuario = nombreUsuario,
    nombreMostrar = nombreMostrar,
    correo = correo,
    estado = estado,
    rolNombre = rolNombre,
)

fun UsuarioEntity.toModel(): Usuario = Usuario(
    idUsuario = idUsuario,
    idRol = idRol,
    nombreUsuario = nombreUsuario,
    nombreMostrar = nombreMostrar,
    correo = correo,
    estado = estado,
)

fun UsuarioEntity.toItem(): UsuarioItem = UsuarioItem(
    idUsuario = idUsuario,
    nombreMostrar = nombreMostrar,
    correo = correo,
    rol = rolNombre.orEmpty(),
    estado = estado,
)

fun UsuarioItem.toEntity(): UsuarioEntity = UsuarioEntity(
    idUsuario = idUsuario,
    idRol = 0,
    nombreUsuario = correo.substringBefore("@"),
    nombreMostrar = nombreMostrar,
    correo = correo,
    estado = estado,
    rolNombre = rol,
)
