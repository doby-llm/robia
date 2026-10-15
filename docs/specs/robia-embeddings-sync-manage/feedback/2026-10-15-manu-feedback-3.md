# Manu feedback 3 (Telegram, 2026-10-15, after APK run 38744486783; Spanish, verbatim)

El hueco del editor sigue. El resto, esta todo bien. Me gustaria añadir lo siguiente:

- Dresses se repite. No deberia haber ninguna category ni occasion... Repetida. Tanto built in como user given
- El icono del cloud dinciendo Syncing es muy util. Una vez terminado, el texto desaparece y el icono se queda. Creo que ahí, para ese caso, el icono no aporta nad ay es mas confuso que otra cosa
- cuando en garment review selecciono accept cada prenda, era mucho más cómodo antes cuando desaparecía el card una vez aceptada. Eso sí me gustaría que tenga un feedback discreto pero que no sea simplemente desaparecer (como una transición o un efecto pero muy disimulado). Aplica la misma para el de color mapping para tener uniformidad
- en garment review, si estoy en category, el nuevo menu eprmite hacer scrolldown. El único problema es que es difícil saber que se puede hacer scroll down y puede llegar a entender que solo hay ese número de categorías cuando en realidad si haces scroll down hay más
- el color mapping sigue funcionando regular: he editado el color negro a gris, y en el review veo: un pantalon que pasa de (Negro, Azul) a (Azul, Azul). Para esos casos, si se detecta primario como Azul, entonces el azul pasaría a ser primario y el secundario se dejaría vacio o según otro color si se identifica un color secundario con nuestro algoritmo.
- me gustaría que además del botón de accept all en garment review hubiese uno de "discard change" en el cual salimos y se anula la acción que hemos realizado. Piensa bien en donde colocar el botón y como indicarlo. Quizá la mejor manera sea arriba a la izquierda con la típica flecha de "volver atrás" el cual arrancaría con un mensaje diciendo esto descarta la acción y si quieres confirmar
- Ahora mismo he borrado el modelo de embeddings y sigo viendo la search bar lo cual da un comportamiento raro. Quiza lo mejor sea que la barra no aparezca si no hay modelo. Para indicar al usuario que existe algo asi, podemos poner a la derecha de Filters unicamente el icono con los sparks. Si el usuario le da, se explicaria lo del smart suggestions y una opcion para descargar el modelo. Una vez se cargue el modelo, estaria bien que la barra de search bar apareciese con una transicion chula pero discreta, teniendo en cuenta de que hay que mover toda la vista con la ropa un pcoo para abajo. Si esot no es posible o buggy, no hagas transicion entonces

En cuanto al syncing:
- He entrado otra vez en una especie de loop donde veo el icono con "Syncing...". A los pocos segundos, veo un parpadeo super pequeño que pasa al estado donde solo hay un icono, y vuelve rapidamente a "Syncing...". Aqui tienes los logs por si ayuda

ts=1792073760529 correlation_id=d0ba4506 category=sync level=info message="sync ended outcome=result=Success" duration_ms=5165
ts=1792073760558 correlation_id=ed7c7376 category=sync level=info status=Running message="sync started trigger=Background forceSnapshot=false forceImport=false garments=0 metadata=0"
ts=1792073766812 correlation_id=ed7c7376 category=sync level=info message="sync ended outcome=result=Success" duration_ms=6275
ts=1792073766834 correlation_id=7cdc1265 category=sync level=info status=Running message="sync started trigger=Foreground forceSnapshot=false forceImport=false garments=0 metadata=0"
ts=1792073772199 correlation_id=7cdc1265 category=sync level=info message="sync ended outcome=result=Success" duration_ms=5380
ts=1792073772229 correlation_id=9e573923 category=sync level=info status=Running message="sync started trigger=Foreground forceSnapshot=false forceImport=false garments=0 metadata=0"
ts=1792073777283 correlation_id=9e573923 category=sync level=info message="sync ended outcome=result=Success" duration_ms=5069
ts=1792073777306 correlation_id=26393e21 category=sync level=info status=Running message="sync started trigger=Background forceSnapshot=false forceImport=false garments=0 metadata=0"
