Tengo un nuevo request para Robia app de Android. Esto se basa en una nueva
ronda con muchas propuestas y mejoras:

- Estaria bien que se pudiera hacer Horizontal scroll en "Manage" para
cambiar de entre los tabs. Como manera alternativa a tener que estar dando
tapping a cada tab
- La screen de advanced filters es bastante fea. Simplemente las etiquetas
están flotando en mitad de la nada. Quizá habría una mejor manera de
organizar esto. Como solución propondría que pienses en algo y des un
mockup html similar a la solucion que propones
- cuando los filtros están activos, estaría bien tener una opción de reset
filters a la derecha del boton Filters. Ahora mismo tengo que darle a
filters, y en la nueva ventana que se abre darle a "Reset all" arriba a la
derecha
- El photo cleanup suele fallar. SImplemente random y sin aviso. Tengo que
darle a retry y ahi si funciona (esta parte quiza no sea necesaria si
directamente reemplazamos el modelo de segmentacion. Depende de dónde se
encuentre el problema). Aunque a veces simplemente falla siempre. En
developer mode veo: Background Failure: reason=MODEL_UNAVIALABLE,
cause=y7.a, message=Waiting for the subject segmentation optional module to
eb downloaded. Please wait. Pero nada cambia
- La ficha cuando añado una nueva prenda no es muy estética: representar
las categorias, occasions y demas boxes: ademas de ser impares lo que
provocan deasjuste visual cuando se representan en dos columnas, no me
gusta ese patron porque se ve raro. Quiza aqui se pueda hacer un rework
visual. De nuevo, con el mockup primero para que se pueda aprobar o iterar
mejor
- Cuando se está procesando la foto, ahora mismo únicamente aparece un
spinner y hace la foto un poco menos visible para que el spinner se vea.
Quiza se puede hacer un poco mas "magico" para que el usuario tenga un
mejor feedback Quiza haya algo de inspiracion en webs como Creating a
shimmer effect on a masked image with CSS . Puede ser algo tuyo o de alguna
libreria extra que se te ocurra
- me encantaría tener un modelo de recorte y seleccion de ropa/accesorios
mas preciso. Entre ellas, que detecte mejor cuando no haya tanto contraste
entre ropa y fondo y el cual detecte elementos extras como perchas para
saber que se tienen que eliminar tambien. Esto podría realizarse con un
modelo de segmentación, pero no sé si es posible o práctico. He estado
viendo que hay modelos en ML Kit o ONNX (adaptados a movil). Aquí vas a
tener que estudiar un poco para ver qué alternativas tenemos y si lo
consideras factible. Si son modelos que unicamente hacen mask basadas en
labels, podriamos usar entonces Gemma embeddings 2 con un mini prompt tipo
"ropa, accesorios [...] pero SIN percha". Con eso, podriamos quedarnos con
la mascara que presente mas similaridad. De nuevo, investiga esto y no
tengas miedo en proponer benchmarks/tests locales en raspberry pi si es
posible. Yo puedo hacer evaluacion visual
- El background crop me parece un poco agresivo. Se puede ver el borde
dentado de los diferentes píxeles. Quizá un poco de suavizado lo mejoraría,
pero no se como. Aqui depende tambien si cambiamos el modelo de masking y
demas comentado con anterioridad
- en el menú principal aún veo cierta redundancia en la información que se
muestra al usuario. por un lado veo un icono de la nube indicando que el
ítem se ha sincronizado correctamente y por otro lado veo el texto "Synced"
debajo de cada ropa. Creo que ese texto no tiene mucho sentido. Ademas, veo
demasiado espacio en blanco debajo de la ropa. Haya o no haya texto. Hay
espacios innecesarios en mi opinion
- en la main screen de Browse, la ficha de la ropa así como el fondo que se
genera para la ropa, no tienen los mismos bordes. Esto hace que en la
esquina superior izquierda y derecha haya un pequeño hueco en blanco donde
el fondo generado para la ropa no llega. Es un pequeño detalle
- El pdf que se genera al exportar una garment es bastante feo. Quizá
puedas hacer rework ahí y darme un mockup de cómo quedaría. Lo quiero
estetico
- el menu settings no escala bien. Por cada idioma que añadimos se añade
una nueva entrada al punto en general quiero que hagas un full rework
basandote en UX/UI
- A nivel de UX/UI, el browse queda raro. Esto es así porque hay prendas
que tienen categorías y ocasiones y otras que no tienen absolutamente nada.
esto hace que el card que representa a cada ropa tenga una diferente altura
vertical. Sumado a ello, hay algunas chips que no caben y debido a ello se
abrevian como por ejemplo "Dressed-up" a "Dressed-u" lo cual da lugar a
confusión. Creo que merece la pena hacer un rework completo de esta
pantalla si así consideras. Quizá puedas darme un mockup y a partir de ahí
decidimos o iteramos
- Quiza hay algo mas que se pueda mejorar en relacion al feedback al
usuario. Cuando se pulsan botones, ropa, cuando se presionan botones como
"Accept/"Discard" en review garment... en general el "feeling" se siente
muy plano. Me gustaria mejorarlo pero sin ser efectos super exagerados.
Algo simple pero funcional. Y los botones relacionados con IA y deteccion,
que se sienta cierta magia
- piensa en general qué categorías más hacen falta o como las
simplificarías. Por ejemplo no hay nada de socks o underwear. Cuando subo
la foto de unos calcetines se me detecta como pantalones. Eso sí también
ten cuidado porque no es cuestión de crear 400 categorías

Como nota general, no tengas miedo de proponer nuevas librerias o cosas que
podamso incluir en el proyecto. No todo tiene por que rehacerse a mano.
Incluido temas de UX/UI o cualquier otra cosa. Tampoco tengas miedo de
proponer whole reworks. El codigo y logica fue hecho por un modelo muy
inferior y tu tienes mucha mas capacidad. Dado que consumes mucho, no dudes
en delegar tareas simples y faciles a Haiku 5.5. Por otro lado, ten cuidado
dado que es comun que te quedes sin usage para esta sesion. Asegurate de ir
guardadno informacion relevante en documentacion para no perder mucho
progreso. La idea seria definiir las especificaciones, requirements y demas
(spec driven development skill). Preguntame todo lo que no este claro, haz
challenge de lo que propongo.

Por último me gustaría hacer hincapié en el hecho de que tú se lo vas a
planificar y luego otro modelo servirá como coordinador y otro como
implementador
