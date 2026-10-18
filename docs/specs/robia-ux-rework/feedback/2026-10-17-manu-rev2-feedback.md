# Manu — feedback on revision 2 (Telegram, 2026-10-17, verbatim)

> Diria que todo tiene buena pinta por ahora. Como veo que todo esta escrito y no hay riesgo de perder informacion, te dejo mas comentarios:
>
> Veo que tenias problemas de autenticacion de Github. Ya deberias poder hacer push. He autorizado el login
>
> - Posibilidad de hacer zoom in and out en la galeria tal y como ocurre en google photos para cambiar el numero de columnas? Seria factible? Smooth? Necesidad de librerias extra? De ser asi, seria solo en Browse y con minimo y maximo numero de columnas.
> - Analiza algoritmo de extracción de colores y evalua si es mejorable en algun sentido (velocidad, precision...) pero no es obligatorio hacer ningun cambio
> - Si fuera posible, en los ajustes de Sync estaria bien saber cuánto ocupa lo que tenemos en la nube en total (solo de Robia claro)
> - Me gustaria hacer integracion con Ricardo para poder vender ropa desde mi app. Se que hay varios niveles, metodos y categorias. En mi caso, el mas simple que requiera menos permisos. Incluso si es que se pueda generar con un boton sobre la ropa una plantilla que se abre automáticamente en Ricardo para poder completarla y darle al boton publicar desde la app de Ricardo. Quiero que explores esto y su viabilidad y me digas qué vias tenemos si es que las hay
> - Es importante que en el google drive sync no se pierda ninguna foto. Quizá se pueda hacer audit para ver que todo está bien y que no hay ningun escenario donde las fotos se puedan perder o no se puedan recuperar en la app?
>
> Ten cuidado con la ventana de contexto y delega para evitar problemas de debsorde cuando consideres. Ademas, puedes delegar a Haikuu 5.5 si la tarea es sencillla

Items (revision 3): R16 Browse pinch-to-zoom columns · R17 colour extraction analysis (change optional) · R18 Drive storage used by Robia in Sync settings · R19 Ricardo selling integration feasibility · R20 Drive sync photo-loss audit.

## Answers to the rev-3 questions (Telegram, 2026-10-17, verbatim)
> 1. Sí
> 2. A
> 3. A
> 4. Descartado 100%
> 5. A
>
> Me gustaría añadir unas cosas más. En relación a los modelos de IA locales, estaba pensando que quizá merece la pena probar 2 cosas más para mejorar la segmentacion y recorte:
> - Modelos generarivos: quizá se pueda probar un modelo de tipo generativo de imágenes que puede corrar en local (stable diffusion 1.5, mobile diffusion,  flux2 klein 4b, sana 0.6b, snapgen, snaphen++, dreamlite mobile...) para pedirle al modelo que con la foto como input, genere la prenda con fondo transparente (o fondo blanco y luego se borra), incluido "sin percha". Quizá merezca la pena hacer ese ultimo benchmark con los modelos que consideres para ver qué resultados obtenemos.
> - Tambien he encontrado que hay otros modelos como el birefnet lite el cual está diseñado para recorte del fondo. Podria ser interesante que encontraras cuál serian los mas valido y probases. Otroa de wsta categoria podrian ser BEN, BEN2, IS-NET general, birefnet... Pero puedes buscar los tuyos. Por supuesto si encuentras un nuevo tipo o modelo de IA que crees que podria servirnos, pruebalo/comentalo
> Para todos estos casos, tambien considero que puedes utilizar combinaciones incluso con diferente orden para optimizar los resultados. [...] Al final estaría bien que lo probases con las mismas prendas que probaste antes, así tendriamos una imagen comparativa con todo lo que se te ocurrió testear. Incluyendo current, vs baseline proposed vs new IA based approaches (si es que tiene sentido)
> Dado que el usage puede andar limitado, no olvides que puedes documentar tu proceso/decisiones/pensamientos y delegar tareas faciles a modelos mas simples como Haiku 5.5

Decisions: Gray swatch yes (COL-DEC-001); sync D02 = option A; original photo not backed up; Ricardo rejected; all added to this round. R21 = segmentation benchmark round 3.
