# Robia — Qué quedará implementado (resumen para humanos)

Paquete: `docs/specs/robia-embeddings-sync-manage/` · Revisión 1 · Estado: aprobado el 14-10-2026.

Este documento explica, sin jerga, qué cambiará en Robia cuando se completen todas las tareas. Los detalles técnicos están en los demás documentos del paquete.

## 1. Sugerencias inteligentes (sustituye a la red neuronal actual)

- **Adiós a la red neuronal actual.** Se borra el clasificador antiguo (TFLite), su modelo, sus scripts y los avisos de "etiqueta detectada por IA" al renombrar o borrar etiquetas.
- **Nuevo modelo: EmbeddingGemma 2** (de Google). Convierte cada foto en una "huella" numérica y la compara con:
  1. el texto de cada etiqueta (por ejemplo "a dress: one-piece garment…");
  2. tus prendas ya etiquetadas (las 3 más parecidas de cada categoría).
  Con tus 142 fotos acertó la categoría el 86 % de las veces solo con texto y el 91 % combinando con tus prendas. Mejora con cada prenda que etiquetes.
- **Qué se sugiere:** category y occasion. Season no se predice: se sigue gestionando a mano.
- **Cuándo:** solo al hacer o elegir la foto (también en el alta por lotes y tras la edición rápida). Al guardar no se vuelve a predecir nada.
- **Si no está claro:**
  - La categoría solo se marca si gana con claridad a la segunda. Si no, se queda sin marcar y no aparecen sugerencias.
  - Occasion: se marca una, dos o ninguna según lo clara que sea la diferencia (margen 0,010 de inicio).
- **"Sugerir ocasiones para esta categoría":** interruptor en cada categoría. Viene apagado en Accessories y Jewelry. Si está apagado, no se sugiere occasion para esas prendas.
- **Descripción de etiqueta:** al crear o editar una etiqueta puedes añadir una descripción corta (con un ejemplo claro del estilo y la longitud) para que las sugerencias funcionen mejor.
- **Tiempo:** la sugerencia puede añadir como máximo 2 s tras el recorte de fondo en tu Poco F3. Si no se consigue, se reduce el detalle de imagen y, si aun así no llega, se para y lo replanificamos contigo.

## 2. Descarga del modelo

- El modelo pesa 388 MB y no va dentro de la app. Se descarga de Hugging Face en una versión fija y se comprueba que el fichero es exactamente el esperado.
- La primera vez que abras la app tras actualizar verás "Descargar ahora / Más tarde". También habrá una entrada fija en Ajustes: "Sugerencias inteligentes: descargar / borrar modelo".
- Por defecto solo con Wi-Fi. Con datos móviles te pedirá confirmación ("388 MB"). Si se corta, sigue donde se quedó.
- Mientras no esté descargado, puedes dar de alta prendas como siempre, pero sin sugerencias, y la búsqueda funciona por palabras en el nombre y las notas.
- El modelo se carga solo cuando hace falta (al entrar en "Añadir prenda", al buscar, en una revisión) y se libera tras 60 s sin uso o al salir de la app, para no ocupar ~1 GB de memoria.

## 3. Prendas que ya tienes

- Al descargar el modelo, Robia prepara las "huellas" de tus prendas en segundo plano, con la app abierta. Verás un aviso sencillo: **"Preparando sugerencias para N prendas…"**.
- Estas huellas se guardan también en tu copia de Google Drive. Al restaurar en un móvil nuevo no hay que recalcularlas (si el modelo es el mismo).

## 4. Revisión al cambiar etiquetas (igual que con los colores)

- Al **añadir, renombrar o borrar** una categoría u occasion, o al **restaurar las predeterminadas**, se abre una pantalla de revisión con **solo las prendas afectadas**:
  - categoría nueva → prendas para las que pasa a ser la mejor opción;
  - categoría borrada → prendas que la tenían, con una sustituta propuesta;
  - occasion nueva → prendas que encajan con ella.
- Tú aceptas, cambias o descartas cada propuesta. No se aplica nada sin tu confirmación.
- Las prendas que aún no tienen huella se saltan, y se indica cuántas son.

## 5. Búsqueda en Browse

- Nuevo campo de búsqueda: escribes, por ejemplo, "vestido rojo" o "falda negra" (en español, inglés o alemán).
- Respeta los filtros que ya tengas puestos y muestra todas esas prendas ordenadas de más a menos parecida.
- La búsqueda combina la foto (70 %) con el nombre y las notas (30 %).
- En la ficha de cada prenda: botón **"Ver prendas parecidas"** (aprobado). Abre Explorar con tus prendas ordenadas de más a menos parecida a esa foto.

## 6. Google Drive más fiable y más rápido

- **Fotos perdidas:** se corrige la causa (el nombre del fichero crecía en cada restauración hasta superar el límite de Android). Los nombres pasan a ser cortos y fijos, y se limpian los ficheros antiguos sin dejar nada huérfano.
- **Más rápido:**
  - fotos en Drive en WebP (calidad 90, máx. 2048 px): entre 5 y 8 veces más pequeñas, sin diferencia visible;
  - solo se sube lo que ha cambiado (hoy se suben todas en cada sincronización);
  - una sola consulta para listar los ficheros y 4 descargas a la vez.
- **Menos fallos:**
  - la sincronización se hace en segundo plano aunque la app esté cerrada, cuando hay conexión;
  - un corte de red ya no es un "fallo": la prenda queda "Pendiente de subir" y se reintenta sola;
  - una foto problemática ya no bloquea al resto;
  - un límite de peticiones de Google se reintenta en vez de pedirte iniciar sesión.
- **"Sync failed — retry/details" desaparece.** Cuando algo necesite tu acción verás la causa en lenguaje claro y botones que funcionan (por ejemplo "Google Drive está lleno · Reintentar" o "Inicia sesión de nuevo"). Solo avisos dentro de la app, sin notificaciones.
- **Conectar Drive con prendas ya en el móvil:** ahora se permite; antes de combinar verás un resumen ("Drive: 34 · Este móvil: 5 → 39 prendas").
- **Cambio de móvil:** un Robia recién instalado trae todo de Drive. Si un móvil antiguo intenta sincronizar después de que otro haya escrito, te pregunta en vez de sobrescribir.

## 7. Manage más fluido y rediseñado

- Se rediseña Manage según los mockups (estilo Earth & Steel) y se elimina el lag: pestañas por tipo de etiqueta (Categorías, Ocasiones, Temporada, Ubicación, Colores), buscador de etiquetas y una lista que se dibuja fila a fila en vez de secciones enteras de golpe.
- El editor de etiquetas incluye la descripción y el interruptor de ocasiones.

## 8. APK de pruebas optimizado

- CI generará dos APK: **staging** (optimizado, como el de una versión real, para el uso diario y para medir el lag) y **debug** (para depurar). Staging se instala encima del actual sin perder datos.

## 9. Modo desarrollador

En la ficha de la prenda (solo con el modo desarrollador activo):
- top-5 de category y occasion con sus puntuaciones, y si se aplicó o no y por qué;
- modelo y receta usados, fecha, qué entró (imagen, nombre, notas) y tiempo de cálculo;
- botones "Recalcular embedding" y "Exportar imagen de entrada".
En Ajustes, una "Prueba del modelo" que mide carga, tiempo por foto y memoria en tu móvil.

## 10. Lo que NO entra

- Fine-tuning del modelo (posible mejora futura).
- Modo oscuro, publicación en Play, varios móviles editando a la vez, perfiles de arranque (baseline profiles), filtro de móviles compatibles.

## 11. Qué necesitaremos de ti

1. Aprobar el paquete (mockups incluidos) escribiendo **APROBADO**.
2. Instalar el APK staging de la prueba del modelo en tu Poco F3 y pasarnos los números de "Prueba del modelo".
3. Una validación final en el móvil (sync, Manage, sugerencias y búsqueda) con una lista de comprobación corta.
