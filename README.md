# Notes — app de notas para Android

Kotlin + Jetpack Compose + Room (SQLite). Sin dependencias pesadas: nada de
Flutter/React Native, solo el SDK nativo de Android. Es lo más liviano y
optimizado que existe para Android, y queda fácil de expandir (etiquetas,
recordatorios, sincronización, etc. son la evolución natural de este código).

## Estructura
```
app/src/main/java/com/example/notes/
  data/        Entity, DAO, Database, Repository (Room)
  ui/          ViewModel + pantallas Compose (lista y edición)
  MainActivity.kt   Navegación (Navigation-Compose)
.github/workflows/build-release.yml   CI: compila, firma y publica el APK
```

## 1. Subir esto a tu repo

Sube todo el contenido tal cual a la raíz de tu repositorio de GitHub
(incluye la carpeta `.github/`, que es donde vive el workflow).

Antes de subirlo, si quieres tu propio identificador de app, cambia
`com.example.notes` por el tuyo en:
- `app/build.gradle.kts` (`namespace` y `applicationId`)
- las rutas de `app/src/main/java/com/example/notes/...`
- `app/src/main/AndroidManifest.xml` (no tiene el paquete explícito, así que no requiere cambio ahí)

Si no te importa, puedes dejarlo así para probar.

## 2. Generar el keystore de firma (una sola vez)

**Esto es lo que te permite actualizar la app sin desinstalar**: Android exige
que cada actualización esté firmada con la misma clave. Si generas un
keystore distinto en cada build, tendrás que desinstalar cada vez.

En tu máquina (necesitas tener el JDK instalado):
```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias notes -keyalg RSA -keysize 2048 -validity 10000
```
Te va a pedir una contraseña del keystore y una de la clave (pueden ser
iguales). **Guarda ese archivo y esas contraseñas en un lugar seguro**: si
los pierdes, no podrás volver a publicar actualizaciones de esta app, solo
podrás lanzarla como una app "nueva".

Conviértelo a base64 para poder pegarlo como secreto de GitHub:
```bash
base64 -w0 release.keystore > release.keystore.b64
```
(en macOS: `base64 -i release.keystore -o release.keystore.b64`)

## 3. Configurar los Secrets en GitHub

En tu repo: **Settings → Secrets and variables → Actions → New repository secret**.
Crea estos 4:

| Nombre | Valor |
|---|---|
| `KEYSTORE_BASE64` | contenido de `release.keystore.b64` |
| `KEYSTORE_PASSWORD` | la contraseña del keystore |
| `KEY_ALIAS` | `notes` (o el alias que hayas usado) |
| `KEY_PASSWORD` | la contraseña de la clave |

## 4. Compilar

Cada `push` a `main` dispara el workflow (también puedes lanzarlo manualmente
desde la pestaña **Actions → Build & Release APK → Run workflow**).

El workflow:
1. Compila y firma `app-release.apk` con tu keystore.
2. Sube el APK como **artifact** de esa ejecución.
3. Crea automáticamente un **Release** en GitHub (`v1.0.<número de build>`)
   con el APK adjunto para descargar directo desde el celular.

## 5. Instalar y actualizar en el teléfono

- Primera vez: descarga el APK desde la sección **Releases** del repo,
  ábrelo e instálalo (activa "orígenes desconocidos" si te lo pide).
- Siguientes veces: descarga el nuevo APK y ábrelo igual — como está firmado
  con la misma clave, Android lo reconoce como una actualización y **no pide
  desinstalar**.

## Notas técnicas

- `versionCode` se genera automáticamente a partir del número de ejecución
  de GitHub Actions (`github.run_number`), así que cada build tiene un código
  más alto que el anterior automáticamente — requisito para que Android
  detecte la actualización.
- Room guarda las notas en SQLite local (`notes.db`), sin backend ni permisos
  de red.
- Para expandir: agrega nuevas columnas a `Note.kt` + una migración de Room,
  o nuevas pantallas Compose registrándolas en el `NavHost` de `MainActivity.kt`.
