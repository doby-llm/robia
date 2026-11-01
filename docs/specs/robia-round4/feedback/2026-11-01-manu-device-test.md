# Manu, Telegram, 2026-11-01 (device test of build 0345639; verbatim, Spanish)

Todo funciona pero tengo unos fine tuning tips:

- Cuando elinino una prenda de una coleccion, se puede ver el chip rojo removed. Sin embargo, la palabra no cabe y por lo tanto veo remove en la primera línea y luego una d suelta
- cuando le doy a provacy policy desde settings crashea: android.util.AndroidRuntimeException: Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag. Is this really what you want?
- el menú de open source licenses me gustaría si fuera posible no tenerlo tan accesible. Si tiene que ser algo público, supongo que no me queda de otra. Si no preferiría ocultarlo
- Me gustaria tener Collections dentro de la pantalla stats. Así sé cuantas prendas tiene cada colección
- Para magic touch, averigua el tema de la licencia. Quiero que lo confirmes para asegurarnos

Earlier question: "para que se usa magic_touch? Si es el smart eraser, ese se supone que lo quitamos."
Answer given: MediaPipe tap-to-select model used by the Precise cutout (PreciseCutoutEngine.kt:316); the smart-eraser gesture was removed (R3-DEC-067), the selector was kept on purpose.
