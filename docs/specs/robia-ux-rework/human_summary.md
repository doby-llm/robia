# Robia — ronda UX 2: qué se va a hacer (resumen para Manu)

Origen: tu email «Prompt robia» del 16-10-2026 (copia literal en `feedback/2026-10-16-manu-email.md`).
Mockups: `mockups/index.html` (ábrelos en el navegador; las animaciones se mueven).

## Lo que verás en la app

**Browse**
- Todas las tarjetas tienen la misma altura. Foto 4:5 con el mismo redondeo que la tarjeta (se acaba el hueco blanco en las esquinas).
- Debajo de la foto, dos líneas fijas: el nombre (o la categoría si no tiene nombre) y, en texto, ocasiones/temporada con «…» si no caben. Se acaban los chips cortados tipo «Dressed-u».
- Desaparece el texto «Synced». El icono de nube solo sale cuando una prenda NO está sincronizada (subiendo, error…).
- Con filtros activos aparecen un botón **Reset** junto a «Filters» y una fila con cada filtro activo, que se puede quitar con su ✕.

**Filtros**: cada sección (Favoritos, Categoría, Temporada, Ocasión, Ubicación, Color) en su tarjeta con icono y contador. Los colores son una cuadrícula de muestras. Hay opción «Not set» y una barra fija con «Show N garments».

**Manage**: puedes deslizar a izquierda/derecha para cambiar de pestaña.

**Alta y ficha de prenda**: se acaban las cajas en 2 columnas. Foto grande y una lista de filas (Categoría, Ocasiones, Temporada, Colores, Ubicación, Fit) más Notas. Sigue sin campo Nombre, como se decidió antes; el título es la categoría. Al tocar una fila se abre una hoja inferior para elegir. ✦ marca lo que sugirió la IA.

**Procesado de la foto**: en vez del spinner, una luz recorre la foto; luego el fondo se desvanece y un brillo recorre solo la silueta de la prenda; al final los colores y las sugerencias aparecen con un pequeño «pop».

**Recorte de fondo**
- Arreglo del fallo aleatorio «Waiting for the subject segmentation optional module…»: la app pide a Google Play el módulo y espera a que llegue, en vez de fallar.
- Bordes más suaves: máscara de confianza con suavizado guiado por la imagen.
- Nuevo **«Recorte preciso»**, opcional y descargable (~90 MB, como el modelo de sugerencias): separa mejor una prenda blanca de una puerta blanca y detecta y quita perchas.
- Nuevo **«Retocar recorte»**: tocas una zona (por ejemplo la percha que quedó) y se quita o se recupera la pieza entera.

**Settings**: secciones con una fila por tema (Funciones inteligentes, Copia, General, Acerca de). El idioma pasa a una subpantalla, así que añadir idiomas ya no alarga Settings. Google Drive también tiene su subpantalla. Nuevo interruptor «Vibración».

**PDF**: ficha editorial A4, con tipografía Inter, foto grande sin tinte, datos en lista y tu logo en el pie. Siguen las decisiones de antes: sin Ubicación y sin texto cortado con «…».

**Feedback táctil**: al pulsar, las tarjetas y botones se encogen un poco (0,97). Accept/Discard vibran de forma distinta y la tarjeta sale hacia un lado. Los botones de IA llevan un brillo cálido que pasa cada pocos segundos. Todo es sutil y respeta «quitar animaciones» del sistema.

**Categorías nuevas**: Socks (calcetines y medias) y Underwear (ropa interior). Las probé con tus 142 fotos: solo cambian 2 sugerencias de 142. «Sleepwear» la descarté porque se quedaba con 32 vestidos y shorts.

## Lo que comprobé en tus fotos (Raspberry Pi, nada sale de casa)
- Probé 10 variantes de recorte. La mejor combina u2netp + MediaPipe Interactive Segmenter (selección inteligente automática) + un detector de perchas (D-FINE, clase «Hanger») + MobileSAM. Todo es de licencia libre (Apache/MIT).
  - Arregla las fugas en prendas blancas sobre la puerta blanca y los tiradores sueltos.
  - Quita la mayoría de perchas de alambre. Las pinzas de faldas y shorts solo las quita más o menos la mitad de las veces; para eso está «Retocar recorte».
  - Tiempo en la Pi: ~2 s sin percha y ~6 s con percha. En el móvil hay que medirlo (puerta G-SEG-1).
- Tu idea (segmentar todo + etiquetar con EmbeddingGemma) la probé y no funciona: ~1 minuto por foto, y EmbeddingGemma llamaba «percha» o «puerta» a las blusas blancas, así que el resultado salía vacío. Las diferencias entre etiquetas eran de 0,001–0,06, es decir, ruido. El detector D-FINE es esa misma idea con un modelo entrenado para objetos, y sí funciona.

## Qué tienes que hacer tú
1. Responder las preguntas Q1–Q7 del mensaje y aprobar los mockups (o pedir cambios).
2. G-SEG-1 (~10 min): instalar el APK de prueba, pulsar «Run cutout benchmark» en Developer y pegar el resultado.
3. G-SEG-2 (~15 min): hacer fotos de 10 prendas (con percha, blanco sobre blanco, calcetines) y decir si el recorte te convence.
4. Z02: lista final de comprobación en el móvil.
