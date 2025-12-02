# Roadmap de Cambios y Nuevas Funcionalidades

Este documento detalla los pasos técnicos necesarios para refactorizar la aplicación, solucionar los problemas de reproducción de video de forma definitiva e implementar una nueva funcionalidad de edición de video.

---

## Parte 1: Solución Definitiva al Problema de Reproducción de Video

**Objetivo:** Reemplazar la API `MediaPlayer`, que ha demostrado ser inestable y la causa raíz de los errores `IllegalStateException`, por **ExoPlayer**, la biblioteca de reproducción de medios recomendada por Google. Esto garantizará una reproducción estable y fiable en todos los escenarios.

### Paso 1.1: Agregar Dependencias de ExoPlayer

En el archivo `build.gradle` de la aplicación (el de la carpeta `app`), añade las dependencias de ExoPlayer.

```groovy
// build.gradle (app)

dependencies {
    // ... otras dependencias
    implementation 'androidx.media3:media3-exoplayer:1.3.1'
    implementation 'androidx.media3:media3-ui:1.3.1'
}
```

### Paso 1.2: Refactorizar `CurrentMediaHandler.java`

Esta clase seguirá siendo el cerebro de la reproducción, pero usará `ExoPlayer` en lugar de `MediaPlayer`.

1.  **Reemplazar `MediaPlayer` por `ExoPlayer`:**
    *   Elimina el campo `private MediaPlayer mediaPlayer;`.
    *   Añade un campo para ExoPlayer: `private ExoPlayer exoPlayer;`.

2.  **Modificar el método de inicialización:**
    *   Renombra o reemplaza `initializeMediaPlayer` por `initializeExoPlayer`.
    *   La inicialización de ExoPlayer es diferente:
        ```java
        private void initializeExoPlayer() {
            if (exoPlayer == null) {
                exoPlayer = new ExoPlayer.Builder(context).build();
                Log.d(TAG, "ExoPlayer created");

                // Añadir un listener para eventos de reproducción
                exoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_ENDED) {
                            Log.d(TAG, "Video completed");
                            isVideoPlaying = false;
                            if (runnable && !isPaused) {
                                forceNextMedia(context);
                            }
                        }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        Log.e(TAG, "ExoPlayer error", error);
                        isVideoPlaying = false;
                        if (runnable && !isPaused) {
                            forceNextMedia(context);
                        }
                    }
                });
            }
        }
        ```

3.  **Modificar el método de preparación de video:**
    *   Reemplaza la lógica de `prepareVideo` para usar ExoPlayer.
    *   ExoPlayer utiliza `MediaItem` en lugar de `setDataSource`.
        ```java
        private void prepareVideo(Uri uri) {
            if (surfaceHolder == null || !surfaceHolder.getSurface().isValid()) {
                Log.e(TAG, "Surface is not valid, cannot prepare video.");
                return;
            }
            initializeExoPlayer();

            // Aplicar mute
            if (manager.getMuteVideos()) {
                exoPlayer.setVolume(0f);
            } else {
                exoPlayer.setVolume(1f);
            }

            // Conectar ExoPlayer con la Surface
            exoPlayer.setVideoSurfaceHolder(surfaceHolder);

            // Crear el item y preparar el reproductor
            MediaItem mediaItem = MediaItem.fromUri(uri);
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play(); // Opcional: o esperar a que el estado sea "preparado"
            isVideoPlaying = true;
        }
        ```

4.  **Actualizar la gestión del ciclo de vida:**
    *   **`pauseVideo()`**: Debe llamar a `exoPlayer.pause();`.
    *   **`startVideo()`**: Debe llamar a `exoPlayer.play();`.
    *   **`stop()`**: Debe llamar a `exoPlayer.release();` para liberar todos los recursos.

---

## Parte 2: Implementación de un Editor de Video

**Objetivo:** Crear una nueva pantalla que permita a los usuarios recortar la duración y las dimensiones (cropping) de un video.

**Advertencia:** La manipulación y re-codificación de video es una de las tareas más complejas en el desarrollo móvil. Se **recomienda encarecidamente** no implementarlo desde cero con las APIs de bajo nivel de Android (`MediaCodec`, `MediaMuxer`), ya que es un proceso propenso a errores y muy difícil de depurar.

**Recomendación Principal:** Utilizar **FFmpeg**, la navaja suiza del procesamiento de video. Para integrarlo en Android, se recomienda usar un wrapper moderno como **`ffmpeg-kit`**.

### Paso 2.1: Agregar Dependencia de `ffmpeg-kit`

En el archivo `build.gradle` de la aplicación:

```groovy
// build.gradle (app)

dependencies {
    // ...
    // Añade el paquete completo de ffmpeg-kit. Aumentará el tamaño del APK.
    implementation 'com.arthenica:ffmpeg-kit-full-gpl:6.0-2'
}
```

### Paso 2.2: Crear la Interfaz de Usuario (`VideoEditorActivity.java`)

1.  **Lanzamiento:** La actividad se iniciará con la URI del video a editar.
2.  **Componentes de la UI:**
    *   **Vista Previa:** Un `PlayerView` de ExoPlayer para mostrar el video.
    *   **Línea de Tiempo para Recorte (Trim):** Un `RangeSeekBar` o una vista similar que muestre fotogramas del video y permita al usuario seleccionar un punto de inicio y fin. Esto probablemente requerirá una librería externa o la creación de una vista personalizada.
    *   **Controles de Recorte (Crop):** Una vista superpuesta sobre el video que permita al usuario seleccionar un área rectangular.
    *   **Botones:** "Guardar", "Cancelar", "Girar".

### Paso 2.3: Lógica de Procesamiento con FFmpeg

Cuando el usuario presiona "Guardar", la aplicación debe:

1.  **Construir un Comando FFmpeg:** Basado en la selección del usuario, se genera un comando de texto.
    *   **Ejemplo - Recortar duración (trim):**
        ```
        // Recorta desde el segundo 5 hasta el segundo 15
        -i input.mp4 -ss 00:00:05 -to 00:00:15 -c copy output.mp4
        ```
    *   **Ejemplo - Recortar dimensiones (crop):**
        ```
        // Recorta un área de 480x480 desde la esquina superior izquierda (0,0)
        -i input.mp4 -filter:v "crop=480:480:0:0" output.mp4
        ```

2.  **Ejecutar el Comando en Segundo Plano:**
    *   El comando FFmpeg debe ejecutarse en un servicio (`WorkManager` es la opción moderna recomendada) para no bloquear la UI.
    *   El servicio debe mostrar una notificación con una barra de progreso.
    *   `ffmpeg-kit` permite ejecutar estos comandos y recibir callbacks de progreso.

    ```java
    // Lógica conceptual dentro de un Worker de WorkManager
    String command = buildFFmpegCommand(...);
    FFmpegKit.executeAsync(command, session -> {
        ReturnCode returnCode = session.getReturnCode();
        if (ReturnCode.isSuccess(returnCode)) {
            // El video se procesó correctamente
            // Notificar que el nuevo archivo en "outputPath" está listo
        } else {
            // Hubo un error
        }
    }, log -> {
        // Actualizar notificación de progreso con los logs
    });
    ```

### Paso 2.4: Integración con la Aplicación

1.  La `VideoEditorActivity` iniciará el `WorkManager` y se cerrará, mostrando la notificación de progreso.
2.  Una vez que el `WorkManager` termina, enviará la URI del nuevo archivo de video procesado a la `ImageListActivity` o al `SharedPreferencesManager`.
3.  La lista de reproducción de la aplicación se actualizará para usar la URI del nuevo video editado en lugar de la original.

Este roadmap es de alto nivel y cada paso, especialmente en la parte de edición de video, es un proyecto en sí mismo. Sin embargo, seguir esta guía utilizando las herramientas recomendadas (`ExoPlayer`, `ffmpeg-kit`) es el camino profesional y correcto para lograr los objetivos de forma estable y robusta.
