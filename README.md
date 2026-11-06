# MoneyTrack – Mis Finanzas (Momento 3)

Aplicación Android (Java) para registrar ingresos y gastos personales. Los datos viven en la nube
(**Firebase Firestore**), cada transacción puede llevar **foto** (Firebase Storage) y **ubicación GPS**,
y el acceso está protegido con **Firebase Authentication** y control de acceso por **roles**.

## Problema que soluciona

Mucha gente (y los pequeños negocios) lleva sus ingresos y gastos de memoria, en cuadernos o en chats, y
termina sin saber en qué se va el dinero, sin comprobantes y sin poder compartir el control con otra persona.
**MoneyTrack** centraliza el registro financiero en la nube: cada movimiento queda guardado con su
**foto del recibo** y el **lugar exacto donde ocurrió** (GPS + mapa), el balance se actualiza en tiempo real y
hay **dos perfiles**: el *administrador* gestiona (edita y elimina) y el usuario *estándar* solo registra y
consulta, de modo que los datos no se alteran por accidente. Además avisa con notificaciones cada vez que
se guarda o actualiza un movimiento.

## Funcionalidades

| Módulo | Qué hace |
|---|---|
| **Autenticación y sesión** | Registro e inicio de sesión con correo/contraseña (`FirebaseAuth`). Auto‑login si hay sesión activa y cierre de sesión que limpia `SharedPreferences` y el stack de activities. |
| **Roles (RBAC)** | Colección `usuarios/{uid}` con el campo `rol` (`admin` o `estandar`). El **admin** crea, ve, edita y elimina. El **estándar** solo crea y ve (los botones de editar/eliminar se ocultan). |
| **Multimedia** | Imagen desde **galería** o **cámara** con `ActivityResultLauncher` y permisos en tiempo de ejecución. Se comprime y sube a Storage (`imagenes_transacciones/{id}.jpg`) con barra de progreso; la URL de descarga se guarda en `imagenUrl`. Se muestra con **Glide** (`placeholder` + `error` + caché en disco). |
| **Geolocalización y mapa** | `FusedLocationProviderClient` captura latitud/longitud al crear una transacción y `Geocoder` obtiene la dirección. La pantalla **Mapa** (Google Maps SDK) pinta un marcador por cada transacción que tenga ubicación guardada, con `InfoWindow` personalizada (concepto, monto, dirección), controles de zoom y alternancia Normal/Satélite. |
| **Notificaciones** | Canal para Android 8+, permiso `POST_NOTIFICATIONS` para Android 13+, notificación local al crear/editar (con `PendingIntent` que reabre la app) y `FirebaseMessagingService` que recibe push desde la consola de Firebase. |
| **Diseño** | `TextInputLayout` con `setError()` en tiempo real, formularios dentro de `ScrollView` y variante `layout-land` para la pantalla principal. |

## Estado actual

Todos los módulos funcionan: autenticación con roles, CRUD en tiempo real, GPS con dirección,
mapa con pines y notificaciones. El código de subida de fotos a **Firebase Storage** está
implementado, pero no se pudo activar en la nube porque Google exige el plan de pago *Blaze*
(cuenta de facturación). Mientras tanto, las transacciones se crean y se guardan con su ubicación,
pero **sin foto**.

## Credenciales de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| **Administrador** | `admin@moneytrack.com` | `Admin123` |
| **Estándar** | `estandar@moneytrack.com` | `Estandar123` |

> Estas cuentas deben existir en el proyecto de Firebase (ver paso 4 de la configuración).
> Cualquier cuenta creada desde la pantalla **Registrarse** nace con rol `estandar`; el rol `admin`
> solo se asigna manualmente en Firestore, para que nadie pueda auto‑promoverse desde la app.

## Configuración para ejecutar el proyecto

1. **Abrir** la carpeta en Android Studio y esperar el *Gradle Sync* (JDK 17).
2. **Firebase**: el archivo `app/google-services.json` ya viene incluido en el repositorio y debe corresponder al proyecto de Firebase con el
   paquete `com.example.moneytrack`. En la consola de Firebase habilitar:
   - **Authentication → Método de acceso → Correo electrónico/contraseña**
   - **Firestore Database**
   - **Storage** (Google lo exige ahora con el plan *Blaze*, que pide una cuenta de facturación; el uso de una app de clase suele quedar dentro de la cuota gratuita)
   - **Cloud Messaging** (viene activo por defecto)
3. **API Key de Google Maps**: crear una clave con *Maps SDK for Android* habilitado en Google Cloud
   (el proyecto de Firebase es también un proyecto de Google Cloud) y agregarla en `local.properties`:
   ```properties
   MAPS_API_KEY=TU_API_KEY_AQUI
   ```
   Gradle la inyecta en el `AndroidManifest.xml` (`com.google.android.geo.API_KEY`).
   `local.properties` no se sube a Git. Sin la clave la app compila y funciona, pero el mapa se ve en blanco.
   Google Maps Platform también requiere una cuenta de facturación vinculada al proyecto para que la clave funcione.
4. **Crear los usuarios de prueba**
   1. Authentication → Usuarios → *Agregar usuario*: `admin@moneytrack.com` / `Admin123` y
      `estandar@moneytrack.com` / `Estandar123`.
   2. Firestore → colección `usuarios` → un documento por usuario, con **ID igual al UID** de Authentication:
      - UID del admin → campo `rol` (string) = `admin`, y `email`.
      - UID del estándar → campo `rol` (string) = `estandar`, y `email`.

      (Si el documento del estándar no existe, la app lo crea sola con rol `estandar` en su primer inicio de sesión.)
5. **Reglas de seguridad** (recomendado: así los roles se aplican también en el servidor, no solo en la interfaz).

   *Firestore → Reglas*
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {

       function esAdmin() {
         return get(/databases/$(database)/documents/usuarios/$(request.auth.uid)).data.rol == 'admin';
       }

       match /usuarios/{uid} {
         allow read: if request.auth != null && request.auth.uid == uid;
         allow create: if request.auth != null && request.auth.uid == uid
                       && request.resource.data.rol == 'estandar';
         allow update: if request.auth != null && request.auth.uid == uid
                       && request.resource.data.rol == resource.data.rol;
       }

       match /transacciones/{id} {
         allow read, create: if request.auth != null;
         allow update, delete: if request.auth != null && esAdmin();
       }
     }
   }
   ```

   *Storage → Reglas*
   ```
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /imagenes_transacciones/{archivo} {
         allow read: if request.auth != null;
         allow write: if request.auth != null
                      && request.resource.size < 5 * 1024 * 1024
                      && request.resource.contentType.matches('image/.*');
       }
     }
   }
   ```
6. **Ejecutar** en un emulador (con *Google Play* y la ubicación configurada en *Extended controls → Location*)
   o en un dispositivo físico con Android 7.0 o superior.

## Guion de sustentación

1. **Login** con `estandar@moneytrack.com`: la barra superior muestra el rol y el correo; en la lista no aparecen
   los botones de editar/eliminar.
2. **Crear una transacción**: concedido el permiso de ubicación se muestra la dirección; guardar →
   aparece la **notificación** en la barra de estado (tocarla reabre la app). La captura de foto
   con cámara o galería está implementada, pero la subida a Storage requiere el plan Blaze.
3. **Lista con Glide**: la tarjeta muestra la miniatura y la dirección. Girar el teléfono para mostrar el
   modo horizontal.
4. **Mapa** (ícono del mapa en la barra): pin de la transacción, tocarlo para ver la `InfoWindow`,
   usar el zoom y alternar **Satélite**.
5. **Cerrar sesión** y entrar como `admin@moneytrack.com`: ahora sí se ven editar y eliminar.
6. **Validación en Firebase Console**: documento en `transacciones` (con `latitud`, `longitud`,
   `direccion`, `creadoPor`) y usuarios en `usuarios`.
7. **Push FCM**: Firebase Console → *Messaging → Nueva campaña → Mensaje de notificación de Firebase* →
   *Enviar mensaje de prueba* pegando el token FCM (se imprime en Logcat con la etiqueta `MainActivity` y se
   guarda en `usuarios/{uid}.fcmToken`).

## Estructura principal

```
app/src/main/java/com/example/moneytrack/
├── LoginActivity / RegisterActivity     # autenticación y auto-login
├── MainActivity                         # lista, logout, acceso al mapa (RBAC en la UI)
├── TransactionFormActivity              # formulario: cámara/galería, Storage, GPS, Geocoder
├── MapaActivity                         # Google Maps + InfoWindow + capas
├── MoneyTrackApp                        # crea el canal de notificaciones
├── MoneyTrackMessagingService           # FCM
├── adapter/TransaccionAdapter           # RecyclerView + Glide
├── data/ (FirestoreHelper, UsuarioRepository, SessionManager)
├── modelo/Transaccion
└── util/ (NotificationHelper, AuthMensajes, SimpleTextWatcher)
```