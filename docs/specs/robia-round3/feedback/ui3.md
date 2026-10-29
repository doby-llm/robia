# Manu feedback 3, 2026-10-27 (Telegram, verbatim, Spanish) on staging build 0de712b
Rule: anything NOT mentioned = device check approved.

F21 el icono de los sparks no se ve en el pop up de review garments en dark mode debido a la elección de color
F22 En review garments, la screen, la barra de progreso a medida que voy aplicando cambios, es demasiado brusco, hazla un poco más smooth.
F23 En esa misma pantalla, es bastante molesto que cada vez que le doy apply 1 change, salga un popup que diga "1 change applied". Same for "Kept as it was".
F24 Ahora mismo si presiono en la categoría en verde, la cual es la propuesta, simplemente se deselecciona. Me gustaría que si se deja pulsado se pueda proponer otra (aparecería el selector de tags/categoria/occasion que aplique).
F25 Las instrucciones que dicen "Tap a change to skip it..." Me parece bastante redundante, simplemente no las digas.
F26 Una vez revisado todas, se ve muy rara la transición a la pantalla que dice "Tags updated on 20 garments". Primero, por lo que he dicho: es muy brusco. Segundo, porque la pantalla da demasiada información. Ahora mismo únicamente con mantener el tic grande y todo lo que viene debajo, me vale (es decir, quitar la barra de progreso, quitar las instrucciones, quitar "Tap a change to skip it..."). El resto me gusta. En lo que sí que me he fijado es que a veces cuando le doy a "Done" a veces no veo esa transición chula que ya teníamos. Al menos ese efecto sigue apareciendo cuando cambiamos y revisamos los colores.
F27 En la pantalla de review solo ver en todas las ropas el comentario de untitled garment. Esto es normal ya que no hay ningún garment que tenga título. Eso se puede simplificar. Además, cuando termina de seleccionar los duplicados y acepto, estaría bien que volviéramos a tener una transición similar a la de cuando terminamos el review garments, la cual redirige automaticamente a browse sin dejar al usuario en una pantalla vacía tal y como ocurre ahora
F28 El press back again to exit NO funciona nunca
F29 cuando borro una categoría la cual ningún garment tiene, el popup de review garments no quiero que aparezca ya que no tiene sentido. No sé si esta comprobación sería posible hacer a tiempo real
F30 V-K00-D: El cutout method es algo raro. Pondria: "Standard", "Precise" y "Automatic". Ahora mismo no hay "Precise" como tal y el problema es que en la propia config del cutout se referencia ese nombre, pero no queda claro cual es
F31 GPU related: el model benchmark GPU todavia sigue dando error. No se si eso es algo que se sigue investigando, si deberia funcionar o que. La idea original era intentar tener mas velocidad en todo el proceso de procesamiento de fotos. Logs:
  device=Xiaomi M2012K11AG (alioth) API 33
  GPU unavailable: LiteRtLmJniException: ComputeEmbedding failed: UNKNOWN: ERROR: [third_party<path> └ ERROR: [third_party<path> └ ERROR: [third_party<path> └ ERROR: [.<path> └ ERROR: [third_party<path> └ TensorBuffer must have all static dimensions
F32 Se supone que el smart eraser iba a ser quitado. Tanto en "Keep" como en "Remove", el default mode is brush (y dado que es el unico, no hace falta boton "Brush")
