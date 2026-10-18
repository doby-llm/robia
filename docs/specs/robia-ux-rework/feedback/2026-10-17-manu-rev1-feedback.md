# Manu feedback on revision 1 (Telegram, 2026-10-17) — verbatim

Vale. Ya he leido todo. Aqui está mi feedback

- No me enviaste el PDF con toda la toma de decisiones y tal. Aunque no hace falta ya que lo que te voy a enviar ahora crea otra iteracion supongo
- Me gustaría mejorar velocidad del batch processing donde se añaden varias ropas a la vez. Quizá paralelizando si es posible. La idea es seguir viendo que las prendas se procesan en el mismo orden que en la pantalla, pero quizá se puede usar multiprocessing (o a lo mejor paralelizar ciertas stages para ganar tiempo) para no tener solo una prenda processing al mismo tiempo y el resto queued.
En cuanto a preguntas:
1. A: aunque tengo dudas. El recorte mejora algo pero no mucho. Los principales problemas vienen de cara a la percha: esta suele crear espacios del background delimitados por la propia percha. Imagino que primero se elimina el bakground, dejando la ropa + percha + espacio delimitado por percha (o a veces incluso del cinturón). Estos no suelen ser detectados bien. Me preguntaba si había alguna manera de mejorar eso (quizá si primero se borra la percha y luego se procede el algoritmo ya sea el ya existente o el de ahora o algo así). Y para la calidad que estamos obteniendo, no se si merece la pena en cuanto a phone processing speed.lo digo porque el modelo actual suele eliminar bien el fondo de cinturones y demás. No se qué combinacion al final es mejor. Lo de la velocidad queda pendiente tambien. En el peor de los casos tendria que hacerse responsable el usuario con el segmentador
2. A. Quizá lo fel retoque se pueda incluir en el actual editor de fotos donde puedes tocar el brillo y demás. Aunque ese menu no me gusta nada. No descarto un full rework. Esto te lo dejo a tu juicio
3. Lo de retocar el recorte tiene sentido. Tanto para restaurar partes borradas como para añadirlas de nuevo supongo. La idea es que con el dedo seleccionas partes y hay cierto tipo de segmentación
4. Para browse mejor un hibrido: 2 columnas pero solo las imagenes (es decir, como lo que propones de 3 columnas donde no hay tedto acompañando la ropa, pero mejor 2). Configurable sen settings
5. B
6. A. Como categoria me gusta extender ma descripcion de accesories para incluir tambien cinturones
7. No le veo el sentido a la vibración
8. (Mockups) Garment form, settings, filters me gustan: Processing el efecto me gusta, aunque habria que ver como queda en la app. El pdf me gusta aunque tienes que pensar en nuestro caso. Ahora mismo solo hay item description y encima es opcional. Piensa en como luciría el pdf en estos casos. Motion.html me gusta. Me faltan algunos efectos o pantallas cuando se hace review garment: quizá al terminar se gacer review de todo sale una pantalla intermedia con sparks o algo así diciendo que ya se han asignado las nuevas etiquetas/colores (aunque no muy exagerado)
- En el batch processing, las imagenes tiene un chip de status (processing, queue...) y un texto debajo duplicando el estado. El texto de abajo me sobra. Eso sí, en ese hueco estaria bien esta vez poner la categoria detectada y las occasions (si es que hay algunas). Aunque no se como quedaria ya que cada card tendria una altura diferente y debido a ello el grid quedaria irregular. Quizá se puede poner como hiciste en motion donde simplemente hay 1 columna con multiples rows. Each row/entry con cada prenda. Habria mas espacio ademas para poner categories, occasions y demás. Ademas, dirante el batch processing hay texto redundante: "Processing 12 items... Save becomes available after processing finishes. Accepted 0. Review 0.
Failed 0. Interrupted 0." Ese texto aparece ambas arriba y abajo. Eso es mejorable. Además creo que cerca de la barra de carga estaria genial poner un ETA aproximado

## Planner mapping (his numbering shifts by one from "3")
- 1 → Q1 precise cutout: A with doubts; asks hanger-enclosed background holes (hanger triangle, belt loops), hanger-first idea, worth vs phone speed; ML Kit handles belts well; "worst case user refines".
- 2 → Q2 refine: A; maybe inside the existing photo editor (brightness etc., he dislikes that menu; full rework at planner's judgment).
- 3 → comment on refine: restore AND remove parts, finger selection with smart segmentation.
- 4 → Q3 Browse: hybrid — 2 columns, photos only, configurable in Settings.
- 5 → Q4 filters: B = keep AND everywhere (interpretation; confirm in reply).
- 6 → Q5 categories: A = Socks + Underwear; extend Accessories description to include belts.
- 7 → Q6 haptics: dropped.
- 8 → Q7 mockups: form/settings/filters OK; processing OK (check in app); PDF: design for no name + optional notes; motion OK; add review-complete interstitial with subtle sparks.
- New: batch speed (parallel/pipelined, keep visual order); batch UI as 1-column rows with thumbnail + category + occasions, no duplicate status text, no duplicated summary top/bottom, ETA near progress bar.
- Deliver the decision PDF this time.

## Clarification (Telegram, 2026-10-17, verbatim)
> En cuanto a la duda, me he equivocado. Si se marca summer + winter quiero que aparezcan tanto las de verano como las de invierno. Sigue de forma autonoma pero teniendo en cuenta que el usage se dispara y que tienes que escribir de vez en cuando en la docs para no perder progreso

→ Filters CONFIRMED: OR within a section, AND across sections (REQ-FI-04, UX-DEC-003). The "5 → B" mapping above is superseded.
