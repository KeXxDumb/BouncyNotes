# Reglas mínimas; Room y Compose ya incluyen sus propias reglas de consumo.
-keepattributes *Annotation*

# Media3/ExoPlayer: la librería ya trae sus propias consumer-rules dentro
# del AAR, así que esto en teoría es redundante — pero como esta app SOLO se
# prueba con el APK de assembleRelease (minificado, ver proyecto), se agrega
# como red de seguridad explícita por si algo de la detección de
# extractores/formatos se resuelve por reflexión y se pierde con la
# ofuscación en algún dispositivo puntual. Costo real: casi nulo (poco
# código, ya extraído igual por las consumer-rules).
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
