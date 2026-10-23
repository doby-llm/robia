# Manu email "Prompt robia" (Thu, 22 Oct 2026 00:35:18 +0200)

Verbatim:

Quiero hacer otra ronda de planificación más para Robia app. Aquí te dejo
los diferentes puntos. Tú únicamente funcionarás como el planificador. La
idea es que evalúes cierta soluciones (feasability, research...) y hagas un
plan solido que luego pasaremos a otro agente de algo menos capacidad para
ser el coordinador, el cual lanzará haikuu workers. Usa la skill spec
driven development. Vamos a iterar lo que haga falta. Ten en cuenta que
consumes mucho y que no me gustaria perder el progreso. Deja por escrito lo
que vayas considerando para no perder mucho cuando el usage limit hits.
Otra opcion es que delegues a haikuu 5.5 aquellas tareas simples para
ahorrar uso (siempre y cuando no afecte calidad o toma de decisiones).

Hay dos tareas que requiere research (decision models y outfit generators).
Para esto, me interesa saber lo antes posible si es viable o no (debido a
rendimiento del movil, tiempo de computacion, portabilidad,
precision/utilidad) antes de invertir mucho tiempo y recursos explorando
esto. Si esto fuera mas optimo, podria dejar conectado el movil en ADB si
esto permite correr algun test rapido (por ejemplo de performance) para asi
descartar ciertos modelos. Eso si, no descartes demasiado rapido ya que
podriamos estar eliminando una solucion que podria funcionar

- Audit the performance of the smooth when scrolling through browse.
Evaluate if library or if ir can be improved to be smoother. Ahora mismo se
siente un poco pesado cuando pasamos al modo de cuatro columnas y hacemos
scroll rápido. Hasta cierto punto es entendible, así que si considera que
no hay ninguna mejora también lo podemos dejar así
- La ropa blanca es dificil de ver. Esto es así porque la ropa es blanca y
a la vez se usará un degradado blanco haciendo que sea difícil de ver, ya
que todo es blanco
- Cuando abro un garment, la mini card the "last synced" con información
extra no la quiero. Podemos dejarla únicamente visible para el modo
developer.
- Mejorar el boton de export cuando doy a una pieza de ropa: creo que es
mejor un nueva pantalla donde podemos personalizar qué queremos exportar y
como: 1) si queremos foto sin editar o foto de robia. Sé que soy consciente
de que actualmente no guardamos la foto sin editar, pero he cambiado de
opinión. Esto puede ser útil para este tipo de caso o simplemente por si
algún día queremos reprocesar algo. En cualquiera de los casos vas a tener
que pensar en como ampliar la copia de la nube para además contener una
copia de la foto original. Cuando digo la foto original me refiero a que el
único cambio que espero es el de tamaño, ya que la foto de alta resolución
del móvil puede ser muy pesada y simplemente no aportar gran cosa. Por lo
que esperaría una resolución similar a la de las fotos editadas de robia
pero en el equivalente raw. Así de esta manera si volvemos a reprocesar las
fotos raw deberíamos de tener un resultado más o menos similar o idealmente
igual al que ya tenemos. 2) la posibilidad de elegir que campos de los que
se exportan se enseñan en lo que se exporta (occasions, colors...) 3) la
posibilidad de elegir exportar foto o pdf. En el caso de la foto habría que
añadir la lógica para añadir dichos campos occasions, colors...
Seleccionadoa por el usuario.
- En los advanced filters no está el filtro que permite filtrar aquella
ropa que "fit" o "does not fit"
- quiero que se en la pestaña browse se deja pulsado una foto, no solo
permita borrar una a una las fotos, sino que podamos hacer hold and drag
para seleccionar múltiples fotos de una y poder borrarlas después
- Me gustaria tener un detector de prendas identicas. Aprovechando que
tenemos los embeddings, podemos escanear por potenciales duplicados. La
idea seria que en Browse apareciese un warning de "Se han encontrado
potencialmente duplicados, revisalos". La idea es que esto te llevaría a
una nueva pantalla donde se crean diferentes grupos donde se identifican
las prendas que son iguales. En ese caso el usuario podría indicar cuál es
de ese grupo habría que eliminar o si se conserva tal y como está. Aquí hay
que tener cuidado para no tener falsos positivos, por lo que habrá que
elegir un umbral de similaridad muy alto.
- Deberia haber un issue en github con cosas/decisiones pending los cuales
tambien me gustaria añadir para procesar en esta planificacion
- Cuando se aumenta el número de las columnas y por lo tanto se reduce el
espacio de cada prenda en Browse, la chip de "Syncing" con el icono de la
nube apenas se ve ya que no hay espacio. Cuando nos encontramos con tan
poco espacio como a creo que es mejor simplemente poner el icono y no poner
el texto de "Syncing". Esto solo pasaría empezando en las cuatro columnas
incluidas
- A la derecha de los filtros en robia (o donde consideres) quiero tener un
icono que representa analytics. Cuando este se pulsa, se abre un nuevo menú
donde aparecen diferentes histogramas, uno para cada principal categoría de
los tags (colors, locations, categories...) donde se puede ver la cantidad
de elementos que se tiene por cada tag. Quiza un mockup para ver como
quedaria ayudaria
- En la opcion de la nube, etsaria genial tener la posibilidad se descargar
un .zip con todos los datos de Robia (con fotos, fotos originales...)
- Me gustaria saber si podemos portear modelos como d3-lite/edge al movil y
con uso de GPU. Habria posibilidades como potencial mejor deteccion de
tags, potencial creador de outfits... etc. Eso sí, puede que sea imposible
o demasiado lento, dado que estariamos trabajando con imagenes en el movil.
- La interfaz de Robia a nivel de colores en UI es muy mejorable. Hicimos
ciertos experimentos sobre el base y no nos gusta nada. Nos gusto el
concepto de Earth and steel con ese marron que tenemos ahora. Pero el resto
no termino de encarjarnos. Quiza se puedan hacer ciertos mockups muy
basicos con diferentes schemes de colores segun lo que tu consideres mas
estetico para nuestro concepto y uso.
- Por ahora los shinny button parecen demasiado cheap. Creo que con
dejarlos sin el shiny pero añadiendo sparks cada vez que hay smart features
podria servir. Esto también incluye el botón que se encuentra a la derecha
de "Filters" como icono de smart suggestions enabler. Antes tal y como
estaba estaba perfecto. Ahora esta metido en un círculo shiny horrible.
Antes con el icono y sin ningún fondo estaba bien.
- Cuanso hay problemas de aign in la interfaz se pone un poco fea. Esto es
así porque arriba del todo ya sale un error bastante claro y en rojo, pero
esto además añade para cada foto el error en rojo "Sign in" con un icono de
la nube tachado. Esto en cada foto sobra ya que está bastante claro debido
al error que se muestra arriba del todo y lo único que hace es hacer lo más
feo
- Estoy pensando en feature de generacion de outfits. Para ello tengo en
mente usar algo como Text2Outfit, FashionClip 2.0, OutfitTransformer,
Hybrid CLIP + Transformer (2025)... O cualquier otro modelo que se te
ocurra. Algunos de ellos puede que sea demasiado como para correr en el
móvil por lo que habría que considerar cuantización si consideras que
podría ser útil en nuestra aplicación. Lo que quiero conseguir es algún
tipo de outfit generator: ya sea que seleccionando una pieza de ropa como
por ejemplo una camiseta te selecciono un pantalón, o que des un prompt
textual de "cena formal para una noche..." y que te saque el outfit. Quizá
para este caso podamos incluso reutilizar gemma embeddings 2 y los
embeddings que ya tenemos, u otros nuevos. Aunque no descarto utilizar una
nueva herramienta más especializada también basada en embeddings para este
tema del outfit. Creo que los embeddings es la única solución ya que nos
permitiría hacer consultas relativamente rápidas en el móvil. Dependiendo
de como salga este prototipo, podría considerar el crear un nuevo menú para
crear outfits en robia
- Se supone que de qcuerdo al Fix8b, la pantalla donde se hace review de
los garments cuando se cambia un tag o color se supone que hay únicamente
una columna (tal y como se ve en él Batch processing). Pero no veo nada de
esto como por lo que puede que sea un malentendido de que me ha instalado
una versión incorrecta o que directamente no está implementado

No dudes en parar si hay algo que no está claro
