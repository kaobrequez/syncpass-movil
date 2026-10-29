package cl.kcorp.syncpass.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncPassDao {

    @Query("SELECT * FROM proyecto_cache WHERE idCoordinador = :idCoordinador ORDER BY fechaInicio DESC")
    suspend fun proyectosByCoordinador(idCoordinador: Int): List<ProyectoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProyectos(items: List<ProyectoEntity>)

    @Query("DELETE FROM proyecto_cache WHERE idCoordinador = :idCoordinador")
    suspend fun clearProyectos(idCoordinador: Int)

    @Query("DELETE FROM proyecto_cache WHERE idCoordinador = :idCoordinador AND idProyecto NOT IN (:ids)")
    suspend fun deleteStaleProyectos(idCoordinador: Int, ids: List<Int>)

    @Query("DELETE FROM proyecto_cache WHERE idProyecto = :idProyecto")
    suspend fun deleteProyecto(idProyecto: Int)

    @Query("SELECT * FROM evento_cache ORDER BY fechaInicio ASC")
    suspend fun eventos(): List<EventoEntity>

    @Query("SELECT * FROM evento_cache WHERE idProyecto = :idProyecto ORDER BY fechaInicio ASC")
    suspend fun eventosByProyecto(idProyecto: Int): List<EventoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEventos(items: List<EventoEntity>)

    @Query("DELETE FROM evento_cache")
    suspend fun clearEventos()

    @Query("DELETE FROM evento_cache WHERE idEvento NOT IN (:ids)")
    suspend fun deleteStaleEventos(ids: List<Int>)

    @Query("DELETE FROM evento_cache WHERE idProyecto = :idProyecto")
    suspend fun clearEventosByProyecto(idProyecto: Int)

    @Query("DELETE FROM evento_cache WHERE idEvento = :idEvento")
    suspend fun deleteEvento(idEvento: Int)

    @Query("SELECT * FROM inscripcion_cache WHERE idUsuario = :idUsuario")
    suspend fun inscripcionesByUsuario(idUsuario: Int): List<InscripcionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInscripciones(items: List<InscripcionEntity>)

    @Query("DELETE FROM inscripcion_cache WHERE idUsuario = :idUsuario")
    suspend fun clearInscripciones(idUsuario: Int)

    @Query("DELETE FROM inscripcion_cache WHERE idUsuario = :idUsuario AND idInscripcion NOT IN (:ids)")
    suspend fun deleteStaleInscripciones(idUsuario: Int, ids: List<Int>)

    @Query("UPDATE inscripcion_cache SET asistenciaEstado = 'VALIDADA' WHERE idInscripcion = :idInscripcion")
    suspend fun markAsistencia(idInscripcion: Int)

    @Query("UPDATE inscripcion_cache SET estado = 'CANCELADO' WHERE idUsuario = :idUsuario AND idEvento = :idEvento")
    suspend fun markInscripcionCancelada(idUsuario: Int, idEvento: Int)

    @Query(
        "UPDATE inscripcion_cache SET encuestaCalificacion = :calificacion, " +
            "encuestaUtilidad = :utilidad, encuestaComentarios = :comentarios " +
            "WHERE idInscripcion = :idInscripcion",
    )
    suspend fun saveEncuestaLocal(
        idInscripcion: Int,
        calificacion: Int,
        utilidad: String,
        comentarios: String?,
    )

    @Query("SELECT * FROM inscrito_cache WHERE idEvento = :idEvento")
    suspend fun inscritosByEvento(idEvento: Int): List<InscritoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInscritos(items: List<InscritoEntity>)

    @Query("DELETE FROM inscrito_cache WHERE idEvento = :idEvento")
    suspend fun clearInscritos(idEvento: Int)

    @Query("DELETE FROM inscrito_cache WHERE idEvento = :idEvento AND idInscripcion NOT IN (:ids)")
    suspend fun deleteStaleInscritos(idEvento: Int, ids: List<Int>)

    @Query("UPDATE inscrito_cache SET asistio = 1 WHERE idInscripcion = :idInscripcion")
    suspend fun markInscritoAsistio(idInscripcion: Int)

    @Query("SELECT * FROM usuario_cache WHERE correo = :correo LIMIT 1")
    suspend fun usuarioByCorreo(correo: String): UsuarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsuario(item: UsuarioEntity)

    @Query("SELECT * FROM usuario_cache ORDER BY nombreMostrar ASC")
    suspend fun usuarios(): List<UsuarioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsuarios(items: List<UsuarioEntity>)

    @Query("DELETE FROM usuario_cache")
    suspend fun clearUsuarios()

    @Query("DELETE FROM usuario_cache WHERE idUsuario NOT IN (:ids)")
    suspend fun deleteStaleUsuarios(ids: List<Int>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(action: PendingActionEntity): Long

    @Query("SELECT * FROM pending_action ORDER BY createdAt ASC")
    suspend fun pendingActions(): List<PendingActionEntity>

    @Delete
    suspend fun deletePending(action: PendingActionEntity)

    @Query("UPDATE pending_action SET intentos = intentos + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)

    @Query("SELECT COUNT(*) FROM pending_action")
    suspend fun pendingCount(): Int
}
