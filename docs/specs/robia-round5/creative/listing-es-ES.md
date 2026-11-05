# Play listing — es-ES · CR1

Text inside each ```text block is the exact field value (CR2 copies it to `play-assets/es-ES/{title,short,full}.txt`,
PL3 layout; release notes go to the PL2/PL5 release). Counts = Python `len()` of the stripped text, checked by
`listing_check.py` (see PR). Tuteo, same terms as the app (prenda, armario, colección, Ajustes).

## Title (25/30)
```text
Robia: Tu armario digital
```

## Short description (79/80)
```text
Fotografía tu ropa y encuéntrala por significado. IA en tu móvil. Sin anuncios.
```

## Full description (1629/4000)
```text
Tienes más ropa de la que recuerdas. Robia la convierte en un armario en el que puedes buscar, directamente en tu móvil.

HAZ UNA FOTO
Haz o elige una foto de una prenda. Robia quita el fondo en tu móvil y te sugiere la categoría, las ocasiones y los colores. Puedes cambiar cualquier cosa con un toque.

AÑADE MUCHAS A LA VEZ
Elige varias fotos de tu galería y Robia las procesa como un lote.

BUSCA POR SIGNIFICADO
Escribe lo que necesitas como lo dirías, por ejemplo "algo abrigado para una boda", y Robia ordena tus prendas según lo bien que encajan. La búsqueda usa un modelo de IA que funciona en tu móvil. Se descarga una sola vez (unos 388 MB).

TODO EN ORDEN
• Duplicados: Robia te señala posibles duplicados para que te quedes solo con uno.
• Revisión de etiquetas: cuando añades o cambias una categoría o una ocasión, Robia revisa tus prendas y te sugiere cambios. Nada cambia hasta que aceptas.
• Colecciones: agrupa prendas como quieras: un viaje, un armario cápsula, una lista de regalos. Una prenda puede estar en varias colecciones.
• Estadísticas: mira cómo se reparte tu armario entre categorías, colores, ocasiones y estaciones.
• Fichas para compartir: exporta una prenda como una ficha limpia para compartirla.

PRIVADO DESDE EL DISEÑO
• Todas las funciones de IA funcionan en tu móvil.
• Sin cuenta que crear. Sin anuncios. Sin analíticas. Sin servidor del desarrollador.
• Copia de seguridad opcional en tu propio Google Drive, en una carpeta privada de la app.

DETALLES
• Gratis.
• En inglés, español y alemán.
• Dos paletas de colores: Tabaco y burdeos (predeterminada) y Tierra y acero.
• Para Android.
```

## Offline sentence — append to the search paragraph only if shot F passed (73 characters; full description with it: 1704/4000)
```text
Una vez descargado el modelo, la búsqueda funciona incluso en modo avión.
```

## Release notes 1.0.0 (485/500)
```text
Primera versión pública de Robia.
• Añade prendas desde fotos: el fondo se quita en tu móvil y se sugieren categoría, ocasiones y colores.
• Añade muchas fotos a la vez.
• Busca por significado con IA en tu móvil (una sola descarga, unos 388 MB).
• Detección de duplicados, revisión de etiquetas, colecciones, estadísticas y fichas para compartir.
• Copia de seguridad opcional en tu propio Google Drive.
• Nueva paleta predeterminada Tabaco y burdeos; Tierra y acero sigue disponible.
```

## Notes
- Palette names follow SPEC §1.2 (ES: "Tabaco y burdeos", "Tierra y acero").
- The offline sentence is appended to the end of the BUSCA POR SIGNIFICADO paragraph only if shot F passed at CAP.
- "Sin cuenta" = no Robia account; the optional Drive backup uses the user's own Google account.
