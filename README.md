# SyncPass Móvil

Cliente Android en Kotlin + Jetpack Compose conectado a AWS Cognito (login) y Supabase
(mismos datos que usa SyncPass-Web).

## Configuración de Supabase

La app necesita `SUPABASE_URL` y `SUPABASE_ANON_KEY`. Puedes definirlas de cualquiera de estas formas:

1. En `SyncPass-Movil/local.properties`:

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=tu_anon_key
```

2. O en `SyncPass-Web/.env` (la app móvil lo lee automáticamente):

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=tu_anon_key
```

Luego compila de nuevo (`Build > Rebuild Project` o `./gradlew assembleDebug`).
`local.properties` está incluido en `.gitignore`, así que las credenciales no se suben al repositorio.

## Modo offline

- Proyectos, eventos, inscripciones, inscritos y usuarios se guardan en Room.
- Las asistencias y encuestas registradas sin conexión quedan en una cola local.
- Al recuperar la conexión, el Dashboard intenta sincronizar automáticamente cada 30 segundos.
- WorkManager reintenta la cola en segundo plano cada 15 minutos y al encolar un registro nuevo.
- El Dashboard muestra cuántos registros quedan pendientes.

## Ubicación y radio de asistencia

- Si el evento tiene coordenadas configuradas, al escanear un QR la app solicita permiso de ubicación y envía latitud/longitud al registrar la asistencia.
- Si el evento además tiene radio configurado, se valida que el voluntario esté dentro del radio.
- Las coordenadas y el radio se configuran al crear o editar un evento en el panel Docente.
- Si no hay permiso o ubicación disponible, la asistencia se registra igual y se indica que no hubo validación.

## Arquitectura

- Los paneles usan ViewModels (`DocenteViewModel`, `VoluntarioViewModel`, `BeneficiarioViewModel`, `AdminViewModel`) y `StateFlow`.
- `SyncPassRepository` centraliza el acceso a Supabase y la caché Room.
- `SyncWorker` + `SyncScheduler` se encargan de la sincronización en segundo plano.

## Perfiles

Después del login con Cognito, la app lee `cognito:groups` y muestra el panel correspondiente:

- **Administrador:** usuarios + acceso a las vistas Docente, Voluntario y Beneficiario.
- **Docente:** proyectos (crear/editar/eliminar), eventos (crear/editar/eliminar, QR y métricas) e informes.
- **Voluntario:** eventos, lista de inscritos y escáner QR de asistencia.
- **Beneficiario:** información personal, inscripción a eventos, pase QR y encuesta de satisfacción.

## Compilar

```bash
./gradlew assembleDebug
```
