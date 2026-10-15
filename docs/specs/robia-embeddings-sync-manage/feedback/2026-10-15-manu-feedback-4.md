# Manu feedback 4 (Telegram, 2026-10-15, device checklist results on APK run 38774274147; Spanish, verbatim)

A1-A8: PASS. B2, B3, B4, B5(PASS pero mejorable), C1, D1-D3, E1, E2(PASS + mejora), F (todo funciona salvo E3).

B1: Funciona pero mejorable: simplemente veo que el estado de la prenda se queda dentro de la ficha intentando enviarlo todo el rato. De la misma manera en Browse se queda el spinning indefinido y creo que arriba a la derecha sale el icono con "Syncing" todo el rato (estando en modo avion). Quizá esto se pueda detectar y mostrar al usuario de mejor manera
B5: PASS pero mejorable. Nada más abrir la aplicación por primera vez aparece todo a la vez: primero el menú que recomienda lo de la búsqueda inteligente y por otro lado la cuenta recomendada de Google Drive por encima. Creo que gestionar el orden de los menús para presentarlo de otra manera ganaría bastante. Te dejo a ti pensar como se muestra. Pero esto solo ocurre cuando acepto el primer mensaje de sincronizar en la nube. Ahí se abre el menú de selección de cuenta de Google y en el fondo veo lo de descargar el modelo de Smart suggestions.
B6: Si le doy a combine, el menu se queda ahí y veo que en el fondo pasan cosas. La cosa es que dado que el menú se quedó sin hacer nada, le di a Cancel y ahí me salió la pregunta de que lado quiero conservar (phone o cloud). El problema es que el "Not now" estaba overlapped con "keep this phone". Por lo que todo este menú es muy mejorable en general. Piensa si hay algo más que se pueda mejorar ahí
E2: PASS. Hay una cosa que me gustaría mejorar. Cuando eliminó una categoría, me sale si quiero hacer review de garments o not now. Ahí estaría bien que si pulso fuera de ese cuadro simplemente la acción se cancele. Ahora tal y como está se borra
E3: Se contradice un poco. En el menu settings me sale: 5 changes waiting to sync. Syncing to cloud. En browse veo el icono de syncing arriba a la derecha. Pero si hago scroll no veo ninguna prenda con el spinner de syncing. Y por lo que veo, el syncing nunca termina. No he podido capturar los logs pero veo esto:

CLOUD SYNC (DEVELOPER)
Pending changes: 5
Processing: no
Started: 8:05:50 pm
Ended: 8:05:56 pm
Result: c3
Pending at start: 5

Feedback extra:
- He estado probando y ya veo que el espacio en blanco has conseguido eliminarlo. Aunque ahora me parece demasiado. Quiero un pelín más despacio si es posible
- He estado probando editando y borrando etiquetas: editar la descripción de cualquier etiqueta debería hacer trigger del review garment (en caso de que ya no lo sea).
- He hecho el experimento de borrar la etiqueta Dresses. Ahí tenía varios vestidos los cuales fueron automáticamente detectados en su día como Dresses. Le he vuelto a dar a Restaure default, lo cual ha vuelto a añadir la etiqueta Dresses. Sin embargo, el review garments no ha mostrado ninguna coincidencia. Luego he probado a crear una etiqueta nueva llamada Dresses (aunque sin descripcion) y me ha encontrado todos los vestidos cuando antes no he encontrado ni uno solo. Dudo que sea un problema de la descripción. Creo que hay un problema real ahí
- Una vez terminó la revisión y acepto uno a uno todas las pruebas de review garment, me encuentro con una pantalla en blanco y hasta que no le dé a aceptar all no podemos pasar de pantalla a punto esto es contraintuitivo ya que ya he aceptado todos los elementos uno a uno y por lo tanto no debería de requerir ningún botón más. Una vez se acepta la última prenda ya debería de cerrarse el menú
- Hay varias pantallas que todavía tienen el problema de que hacen scroll down un poco más de lo que deberían y dejar un espacio en blanco. Esto pasa con Browse y con los Advanced filters
