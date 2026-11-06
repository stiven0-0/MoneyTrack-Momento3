# Migración a Firebase Firestore — Momento 2

> **Documento histórico.** Describe la migración de SQLite a Firestore del Momento 2. Las reglas de
> seguridad vigentes (con roles) y la configuración actual están en el `README.md`.

Este documento explica qué cambió respecto al Momento 1 (SQLite) y los
pasos para dejar el proyecto corriendo contra Firestore.

## 1. Qué cambió en el código

| Antes (Momento 1)                          | Ahora (Momento 2)                                                |
|---------------------------------------------|--------------------------------------------------------------------|
| `db/DatabaseHelper.java` (SQLite)           | `data/FirestoreHelper.java` (Firestore) — DatabaseHelper se eliminó porque dependía del `id` numérico que ya no existe; si quieres conservar el código de referencia, revisa el historial de Git antes de esta migración |
| `Transaccion.id` (`int`, autoincremental)   | `Transaccion.documentId` (`String`, ID del documento en Firestore, anotado `@Exclude`) |
| `MainActivity` recarga la lista en `onResume()` | `MainActivity` se suscribe una vez en `onCreate()` con `addSnapshotListener` y remueve el listener en `onDestroy()` |
| `TransactionFormActivity` recibe un `int transaccion_id` | Recibe un `String transaccion_id` (Document ID) |
| Sin validación mientras se escribe          | `TextWatcher` en concepto y monto (feedback en tiempo real)       |
| Botón Guardar siempre habilitado            | Se deshabilita mientras la operación está en curso (previene doble envío) |

La colección en Firestore se llama **`transacciones`**. Cada documento
tiene los campos `concepto` (string), `monto` (number), `tipo` (number:
0 = Gasto, 1 = Ingreso) y `creadoEn` (timestamp del servidor, usado para
ordenar la lista de más reciente a más antigua — reemplaza al `ORDER BY id
DESC` que tenías en SQLite).

## 2. Configurar tu proyecto de Firebase

1. Entra a [Firebase Console](https://console.firebase.google.com) y crea
   un proyecto nuevo (o usa uno existente).
2. Dentro del proyecto, agrega una app Android con el `applicationId`
   `com.example.moneytrack` (es el mismo `namespace`/`applicationId` que
   ya tiene tu `app/build.gradle.kts`).
3. Descarga el archivo `google-services.json` que te ofrece la consola.
4. Copia ese archivo dentro de la carpeta `app/` de este proyecto, junto a
   `build.gradle.kts` (queda como `app/google-services.json`).
5. En el menú lateral de Firebase Console, entra a **Firestore Database**
   → **Crear base de datos** → elige **modo de prueba** (test mode) para
   desarrollo. Esto te da reglas abiertas por 30 días, suficiente para el
   entregable.
6. Seguridad: en el Momento 2 se usó el *modo de prueba*. Para el Momento 3 **ya no**: usa las
   reglas con roles que están en el paso 5 del `README.md` (Firestore y Storage).

7. Sincroniza Gradle en Android Studio (`File → Sync Project with Gradle
   Files`). El plugin `com.google.gms.google-services` (ya agregado a
   `build.gradle.kts` y `app/build.gradle.kts`) leerá el
   `google-services.json` automáticamente en cada build.

## 3. Verificar que todo funciona

- Corre la app en un emulador o dispositivo físico.
- Crea una transacción → debe aparecer en la lista sin recargar nada
  manualmente.
- Abre la consola de Firebase → Firestore Database → deberías ver la
  colección `transacciones` con el documento recién creado.
- (Opcional, para la evidencia de sincronización en tiempo real) Corre la
  app en dos emuladores/dispositivos a la vez: un cambio hecho en uno
  debe reflejarse en el otro sin recargar.

## 4. google-services.json

Desde el Momento 3 `app/google-services.json` **sí está versionado** para que cualquiera que clone el
repositorio pueda compilar. La clave que contiene es de cliente y no da acceso a los datos: la
protección real son las Security Rules. Lo que nunca se sube es `MAPS_API_KEY` (va en `local.properties`).
