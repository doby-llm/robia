# Robia — ronda UX 2: qué se va a hacer (resumen para Manu)

Origen: tu email «Prompt robia» del 16-10-2026 (`feedback/2026-10-16-manu-email.md`) y tus respuestas del 17-10-2026 (`feedback/2026-10-17-manu-rev1-feedback.md`). Esta es la **revisión 3** (tus respuestas del 17-10 a la revisión 2: `feedback/2026-10-17-manu-rev2-feedback.md`).
Mockups: `mockups/index.html` (ábrelos en el navegador; las animaciones se mueven).

## Lo que cambia respecto a la revisión 1
- **Vibración:** fuera por completo.
- **Browse:** por defecto, solo fotos en 2 columnas. La tarjeta con nombre y ocasiones pasa a ser una opción en Settings.
- **Filtros:** dentro de una misma sección basta con una opción (Summer + Winter muestra las de verano y las de invierno); entre secciones deben cumplirse todas.
- **Batch más rápido:** mientras una prenda calcula sus sugerencias, las siguientes ya se recortan.
- **Batch en lista:** una fila por prenda con su categoría y ocasiones, sin textos repetidos y con tiempo estimado.
- **Editor de foto:** sustituye al diálogo «Quick edit». Tiene dos pestañas:
  - Recorte: toque o trazo inteligente, más pincel.
  - Luz: brillo y temperatura.
- **Recorte preciso:** ahora quita también el fondo que queda encerrado bajo la percha («percha primero»).
- **Pantalla al terminar una revisión:** check, unos pocos destellos y «Tags updated on N garments».
- **PDF:** se adapta a prendas sin nombre ni notas; el título pasa a ser la categoría y la foto crece.
- **Accessories:** su descripción incluye ahora los cinturones.

## Lo que verás en la app

**Browse**
- Por defecto, todas las fotos del mismo tamaño en 2 columnas y sin texto.
- Se mantienen el corazón y el aviso de nube, que solo aparece si la prenda NO está sincronizada.
- Desde Settings › General › Browse layout puedes pasar a «Photos with details»: debajo de la foto, el nombre (o la categoría) y las ocasiones/temporada en una línea con «…».
- En los dos modos se acaba el hueco blanco en las esquinas.
- Con filtros activos aparecen un botón **Reset** junto a «Filters» y una fila con cada filtro, que se quita con su ✕.

**Filtros**
- Cada sección (Favoritos, Categoría, Temporada, Ocasión, Ubicación, Color) va en su propia tarjeta.
- Los colores se eligen en una cuadrícula de muestras.
- Hay opción «Not set» y una barra fija con «Show N garments».

**Manage**: puedes deslizar a izquierda o derecha para cambiar de pestaña.

**Alta y ficha de prenda**
- La foto va arriba y debajo una lista de filas: Categoría, Ocasiones, Temporada, Colores, Ubicación, Fit y Notas.
- Sigue sin campo Nombre.
- Al tocar una fila se abre una hoja inferior para elegir.
- ✦ marca lo que sugirió la IA.
- El botón «Edit photo» abre el nuevo editor.

**Editor de foto** (sustituye a «Quick edit photo»)
- **Recorte:**
  - Eliges Keep o Remove y tocas, o pasas el dedo, sobre una parte: la percha que quedó, un tirante que se borró…
  - MediaPipe selecciona la pieza entera.
  - El pincel sirve para retoques pequeños exactos.
  - Tienes zoom, deshacer y volver al resultado automático.
- **Luz:** el brillo y la temperatura de hoy, con la vista previa más grande.
- **Guardar:** Done guarda y vuelve a detectar colores y sugerencias; ✕ no guarda nada.

**Añadir varias prendas (batch)**
- **Velocidad:**
  - Hoy se procesan de una en una.
  - Ahora, mientras una prenda calcula sus sugerencias, las 2 siguientes ya se recortan (1 si usas el recorte preciso).
  - Terminan en el orden de la pantalla.
  - Estimo ~40 % menos de tiempo, pero lo mediremos en tu móvil.
  - No uso varios procesos: el modelo de sugerencias solo puede atender una foto a la vez, y un segundo modelo gastaría ~0,8 GB más de memoria.
- **Pantalla:**
  - Una fila por prenda: miniatura, posición, chip de estado y, debajo, la categoría y las ocasiones detectadas.
  - El estado ya no se repite como texto.
  - Arriba, una sola cabecera con la barra y el tiempo restante («About 20 s left»). Aparece tras 2 prendas y nunca salta hacia arriba más de un 20 %.
  - Abajo, solo Save y, si hace falta, una línea corta del tipo «1 needs review».

**Procesado de la foto**
- En vez del spinner, una luz recorre la foto.
- Después el fondo se desvanece y un brillo recorre solo la silueta de la prenda.
- Al final, los colores aparecen con un pequeño «pop».

**Recorte de fondo**
- **Fallo aleatorio arreglado:** la app ya no falla con «Waiting for the subject segmentation optional module…». Ahora pide el módulo a Google Play y espera a que llegue.
- **Bordes más suaves.**
- **«Recorte preciso», opcional y descargable (~91 MB):**
  - Separa mejor una prenda blanca de una puerta blanca.
  - Quita perchas.
  - Con «percha primero» elimina también el fondo encerrado entre la pinza y la cinturilla.
  - ML Kit sigue siendo el recorte por defecto, y lo que se escape lo arreglas con el editor.

**Revisiones**
- Las tarjetas salen hacia un lado al aceptarlas o descartarlas.
- Los botones de IA llevan un brillo cálido.
- Al terminar aparece 1,6 s una pantalla con un check, unos destellos y cuántas prendas se actualizaron. Un toque la salta.

**Settings**
- Secciones con una fila por tema.
- Idioma y Google Drive tienen su propia subpantalla.
- Nueva opción «Browse layout».

**PDF**
- A4 con tipografía Inter, la foto sin tinte encima y tu logo en el pie.
- Si no hay nombre, el título es la categoría.
- Si no hay notas, la foto crece y no queda una banda vacía.
- Sigue sin Ubicación y sin texto cortado con «…».

**Categorías**
- Nuevas: Socks y Underwear.
- La descripción de Accessories incluye ahora los cinturones.
- Medido con tus 142 fotos y la misma calidad de imagen que usa la app:
  - Aciertos totales: de 114 a 115.
  - Accesorios: de 9 a 11 de 12, sin que ninguna otra prenda pase a Accessories.

## Lo que comprobé en tus fotos (en la Raspberry Pi, nada sale de casa)
- **Fondo encerrado por la percha (tu duda del punto 1):**
  - El problema era que el fondo atrapado entre la pinza y la prenda ya se había unido a la prenda antes de quitar la percha.
  - La solución es tu idea: primero se borra la percha (se rellena con el color de la pared), después se vuelve a seleccionar la prenda, y solo cambia la franja de la percha.
  - En las fotos difíciles se va la franja blanca bajo la pinza.
  - Las cinturillas claras se mantienen.
  - La falda que antes perdía la parte de abajo (#129) sale completa.
  - Cuesta ~1,5–2,5 s más por foto con percha en la Pi.
  - Borrar el fondo por color lo descarté, porque se comía las cinturillas blancas.
- **Velocidad en el móvil:** sigue pendiente de medir (G-SEG-1). Por eso el recorte preciso es opcional y ML Kit sigue siendo el de por defecto.

## Qué tienes que hacer tú
1. ~~Aprobar esta revisión~~ — **APROBADO el 18-10-2026.** La implementación la hace un coordinador (Sonnet 5.5 high) con workers Haiku 5.5 high.
2. **G-SEG-1** (~10 min): instalar el APK de prueba, pulsar «Run cutout benchmark» en Developer y pegarme el resultado.
3. **G-SEG-2** (~15 min): añadir 10 prendas con y sin recorte preciso y decirme si te convence.
4. **Z02**: repasar la lista final de comprobación en el móvil (incluye medir el tiempo de un batch de 10 fotos).

## Novedades de la revisión 3
- **Zoom en Browse (U10):** pellizcar cambia el número de columnas (2–4 solo fotos, 2–3 con detalles). Las prendas se deslizan a su sitio y se mantiene la posición. Sin librerías.
- **Color gris (C01):** nuevo color por defecto «Gray». Los grises y vaqueros ya no salen marrón/beige. Tus prendas actuales no cambian hasta que repases los colores.
- **Espacio en Drive (D01):** Settings › Google Drive muestra cuánto ocupa Robia y cuántas fotos tiene, sin permisos nuevos.
- **Integridad de fotos (D02):**
  - Al restaurar y al subir se comprueba que la foto es exactamente la misma.
  - No se conserva una referencia a una foto que ya no existe en Drive.
  - El sync queda ligado a tu cuenta de Google: con otra cuenta no sube ni borra nada hasta que elijas.
  - «Check photos» revisa todo sin borrar nada.
- **Sin cambios:** una foto por prenda (el original de la cámara no se guarda) y Ricardo descartado.
- **Recorte (benchmark de modelos nuevos):**
  - Ni los modelos de recorte dedicados ni LaMa mejoran el pipeline propuesto. Se mantiene v11.
  - El límite real es detectar la percha (pinzas pequeñas, perchas de madera). Queda como siguiente paso.

## Ajustes de recorte (cambio de plan 1, 18-10)
- En Settings › Cutout podrás elegir:
  - Método: Automático o Estándar.
  - Quitar perchas: sí / no.
  - Detección de percha: Normal o Alta. Alta quita perchas de madera y más pinzas; en las pruebas no se comió ninguna prenda.
  - Bordes: suaves o nítidos.
  - Velocidad del batch: equilibrada o ahorro de memoria.
- En el editor de foto, «Rehacer recorte» repite el recorte de una foto nueva con Estándar, Preciso o Preciso sin quitar percha.
- Los valores por defecto dan el mismo resultado que el plan aprobado.
