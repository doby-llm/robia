# Robia — ronda 3: resumen para Manu

## Qué cambia
- **Recorte preciso:** desde ya vuelve a estar desactivado por defecto (marcado "Experimental") porque destroza prendas. En Python, con tu foto de los shorts rosas, el mismo algoritmo funciona bien, así que el fallo está en la versión del móvil. Primero sacamos las máscaras de cada paso en tu móvil por ADB, las comparamos con la referencia y arreglamos exactamente lo que diverja. Además arreglamos el borde borroso donde estaba la percha (shorts rojos).
- **Borrado inteligente:** el modelo selecciona objetos enteros. Si tocas la prenda, selecciona la prenda entera, y por eso desaparecían partes lejanas. Ahora:
  - se ve el trazo al arrastrar;
  - solo se borra la zona que tocas;
  - si la selección iba a borrar casi toda la prenda, no se aplica y sale un aviso;
  - en modo developer se ve la máscara.
- **Historial completo** en el editor: deshacer y rehacer todo, incluido "Volver a recortar" (nuevo nombre de "Repetir recorte").
- **Foto original:** se guarda la misma imagen que procesó el recorte: 1024 px en Experimental y hasta 2048 px en Standard, en JPEG, unos 0,15–0,7 MB. Nunca más grande que lo que Robia ya usa. Se guarda en el móvil y en Drive. Con ella:
  - Keep recupera fondo borrado;
  - "Volver a recortar" funciona en prendas guardadas;
  - Export puede usar la original.
  
  Solo aplica a fotos nuevas.
- **Batch:**
  - botón ✕ para cancelar y salir; al descartar se borra todo lo temporal;
  - ETA desde la primera foto, siempre visible arriba;
  - una sola espera de 60 s por lote si falta el módulo de ML Kit;
  - la descarga del paquete de recorte tiene Cancelar y se reanuda sola con Wi-Fi.
- **Browse:**
  - scroll más fluido a 4 columnas (lo mido en tu móvil antes y después);
  - ropa blanca con fondo gris cálido;
  - a 4 columnas, solo el icono de estado;
  - sin "Sign in" repetido en cada foto;
  - arrastrar para seleccionar un rango;
  - filtro Fit;
  - botón de Analytics.
- **Sin brillos animados:** ✦ fijo y un destello corto al terminar una función inteligente.
- **Ficha:** "Last synced" solo en modo developer. El botón de compartir abre **Export** (foto, formato y campos).
- **Ajustes › Google Drive:** "Descargar todos mis datos (.zip)".
- **Duplicados:** primero solo las fotos idénticas, más una pantalla de calibración en modo developer donde marcas 30 pares. Con tus marcas fijo el umbral de "muy parecidas". "No son duplicados" se recuerda en el móvil y en Drive.
- **Outfit lab** (modo developer): propone 3 outfits con tus prendas y los valoras 👍/👎 durante una semana.
- **GPU:** una prueba en modo developer para medir si EmbeddingGemma va más rápido en la GPU de tu móvil.
- **Fuera de esta ronda:** paleta de colores (pendiente) y los modelos d3 (no merecen la pena, ver `artifacts/inv-r3-models.md`).

## Qué tienes que hacer tú
1. Escribir **APROBADO** en el PR de planificación.
2. **ADB 1** (unos 30 min), tras las primeras tareas: conectar el móvil y desbloquearlo; el resto lo hago yo.
3. **Calibración de duplicados:** marcar 30 pares (Igual / Distinta).
4. **ADB 2** (unos 30 min) tras los arreglos.
5. **G-CUT:** rehacer al menos 10 prendas en Experimental y en Standard y decir OK o NOK de cada una. Con eso decides si el recorte preciso vuelve a estar por defecto.
6. Al final, la checklist del APK y una semana de Outfit lab.

## Modelos (tu decisión)
Coordinador, workers y revisores: **Haiku 5.5 high**. **Sonnet 5.5 high** solo en 3 casos, como consulta puntual de solo lectura: analizar las sesiones ADB, revisión final antes de fusionar OR1/OR2/OR3/CLN/DUP2 (riesgo de perder datos) y tareas bloqueadas. Unas 9 consultas en total; si fueran más de 12, el coordinador te pregunta antes.
