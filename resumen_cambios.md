# Resumen de Cambios: Depuración de `IllegalStateException` en SlideshowWallpaper

Este documento resume el proceso de depuración para resolver un error `IllegalStateException` relacionado con el `MediaPlayer` en la aplicación SlideshowWallpaper.

## 1. Situación Inicial (Provista por el Usuario)

La aplicación sufría de un `IllegalStateException` al reproducir videos en bucle, especialmente en el ciclo de vida de un Wallpaper Service. El usuario ya había intentado varias soluciones sin éxito:

*   **Cambio a `FileDescriptor`:** Se intentó usar `FileDescriptor` en lugar de `Uri` para `setDataSource`, pero el error persistió.
*   **Gestión de `ParcelFileDescriptor`:** Probar cerrar el descriptor en diferentes momentos (`onPrepared`, inmediatamente) no solucionó el problema.
*   **Orden de Inicialización:** Alternar el orden de `setSurface` y `setDataSource` no dio resultado (`IllegalStateException` o error -38).
*   **Limpieza de Recursos:** Se corrigió una fuga de memoria en `MediaLoader`, pero no afectó al error principal.
*   **Retraso (Delay):** Añadir un `delay` de 100ms entre videos pareció estabilizarlo temporalmente, apuntando a una condición de carrera.
*   **Reutilización de `MediaPlayer`:** El intento del usuario de reutilizar la instancia del `MediaPlayer` con `reset()` seguía arrojando el `IllegalStateException`.

## 2. Proceso de Depuración Asistida

Se realizaron los siguientes cambios de forma iterativa, analizando los logs después de cada paso.

### Intento #1: Prevenir `release()` y forzar recarga
*   **Cambio:** En `CurrentMediaHandler`, se reemplazó `oldMedia.release()` por `oldMedia.stopVideo()` y se modificó la condición `if` para que siempre recargara los videos (`|| currentMedia.isVideo()`).
*   **Efecto:** No solucionó el `IllegalStateException`. La causa raíz era más profunda que simplemente no liberar el reproductor.

### Intento #2: Eliminar `delay` para evitar condiciones de carrera
*   **Cambio:** Se eliminó el `postDelayed(..., 100)` en `CurrentMediaHandler`, reemplazándolo por un `post()` simple para hacer la secuencia de `stop`/`prepare` más determinista.
*   **Efecto:** ¡Éxito parcial! El primer video se reprodujo correctamente, pero el `IllegalStateException` regresó en la segunda reproducción del mismo video en la lista. Esto demostró que el `delay` estaba enmascarando un problema de estado.

### Intento #3: Volver a `release()` con la nueva lógica
*   **Cambio:** Se revirtió `stopVideo()` a `release()` pero manteniendo la lógica sin `delay`. La teoría era que `stop()` no liberaba todos los recursos nativos, mientras que `release()` sí lo hacía.
*   **Efecto:** El mismo fallo. El primer video funcionaba, el segundo no.

### Intento #4 (La Gran Refactorización): Centralizar el `MediaPlayer`
*   **Cambio:** Se reconoció que el problema era la gestión del estado del `MediaPlayer` y su relación con la `Surface`. La arquitectura se refactorizó por completo:
    1.  Se eliminó todo el código del `MediaPlayer` de `MediaInfo`, convirtiéndola en una clase de datos simple.
    2.  Toda la responsabilidad del `MediaPlayer` (creación, listeners, preparación, ciclo de vida) se movió a una única instancia persistente dentro de `CurrentMediaHandler`.
*   **Efecto:** Introdujo errores de compilación, que se solucionaron actualizando las llamadas en `SlideshowWallpaperService`.

### Intento #5: Ajustes Finales Post-Refactorización
*   **Cambio (Doble):**
    1.  Se invirtió el orden de `setSurface()` y `setDataSource()` en el nuevo método `prepareVideo`.
    2.  Se corrigió un nuevo bug introducido por la refactorización que causaba que los videos se cortaran, restaurando la lógica del timer para que solo se active con imágenes.
*   **Efecto:** **¡ÉXITO! El `IllegalStateException` se resolvió por completo.** La combinación de la nueva arquitectura centralizada y el orden correcto de inicialización (`setSurface()` primero) finalmente solucionó el conflicto con la `Surface` que ocurría después de que se dibujaba una imagen.

## 3. Estado Actual y Próximo Paso

*   **Problema Resuelto:** Los videos ahora se reproducen de forma fiable en cualquier combinación de lista de reproducción (solo videos, o videos mezclados con imágenes).
*   **Nuevo Bug Menor:** Al pasar de un video a una imagen, la imagen no se muestra; se queda el último fotograma del video.
*   **Causa:** El `MediaPlayer` no está siendo explícitamente desvinculado de la `Surface` cuando la aplicación cambia a una imagen, impidiendo que el sistema dibuje la nueva imagen.
*   **Solución Propuesta (Actual):** Realizar un ajuste final en `CurrentMediaHandler` para asegurarse de que el `MediaPlayer` se reinicie (`reset()`) siempre que se deja de reproducir un video, liberando así la `Surface` para que las imágenes puedan ser dibujadas.

Este proceso demuestra la naturaleza compleja de la API de `MediaPlayer` y cómo la refactorización hacia una arquitectura más robusta y centralizada fue clave para la solución final.
