# Primer avance de código: guardar en el celular y enviar al portal

Fecha: 17 de octubre de 2026.

## Recorrido incorporado

1. Abrir Productos y pulsar Nuevo producto.
2. Completar los datos y pulsar **Guardar en celular**. El producto queda en SQLite como borrador, aunque no haya conexión.
3. Buscar, editar o descartar el borrador antes de enviarlo.
4. Pulsar **Enviar al portal** cuando el servicio esté disponible. El botón envía todos los borradores pendientes, incluso los ocultos por el filtro de búsqueda.
5. Cuando el servicio confirma la recepción, el registro deja de ser un borrador local. Se solicita actualizar el inventario del portal.

No hay aprobación de supervisores ni envío automático en segundo plano. Un borrador no modifica por sí mismo las existencias compartidas.

## Preparación necesaria

La primera vez hay que cargar las categorías desde el portal mediante **Actualizar**. Se conserva una copia local de esas categorías para registrar nuevos borradores después sin conexión. Si una categoría se desactiva posteriormente, el servicio rechazará el envío y se podrá elegir otra categoría activa.

Hay que instalar la nueva aplicación y reiniciar el servicio con su código actualizado. Editar los archivos o compilar no cambia la versión que ya está instalada en el Xiaomi ni un servicio que siga ejecutando la versión anterior.

## Datos y reintentos

- La base local pasa de versión 2 a 3 mediante una actualización aditiva. Se conservan los registros de la antigua tabla `products`; esa tabla no se envía ni se mezcla con el catálogo del portal.
- `product_drafts` conserva los datos completos, una clave única de envío y el estado de intento. `cached_categories` conserva las categorías descargadas.
- El servidor agrega `ProductSubmissions`, un recibo vinculado al producto. Producto y recibo se guardan juntos. Reenviar la misma clave devuelve la confirmación existente y no crea un segundo producto.
- Si se pierde la respuesta, el borrador permanece sin confirmación. Se bloquea su edición y descarte hasta resolver el envío mediante el mismo botón. Esto evita cambiar el contenido de una operación que pudo llegar al portal.
- Un rechazo explícito por datos inválidos o un código ocupado permite corregir el borrador. Un SKU ocupado por otro producto no se sobrescribe.
- Los ids locales y del servidor se mantienen separados. Los productos pendientes no se ofrecen para registrar movimientos.
- El botón Actualizar consulta el portal; la búsqueda filtra los productos ya cargados y los borradores locales.

## Alcance de esta entrega

El guardado local corresponde a **productos nuevos y sus borradores**. La edición y desactivación de productos ya publicados, las categorías y los movimientos siguen operando directamente con el servicio y requieren conexión. No se agregó una copia persistente del inventario completo del portal: al abrir la app sin conexión se recuperan los borradores y las categorías guardadas, no las existencias remotas.

Actualización del mismo avance: en los productos ya publicados se bloquea la edición directa de existencias, tanto en la pantalla como en el servicio. Para cambiar la cantidad se utiliza un ingreso o una salida. Los productos nuevos con existencias iniciales generan un movimiento de ingreso junto con el alta; no se reconstruyen movimientos antiguos ni se atribuyen operaciones históricas inexistentes. Se agregó detección de cambios simultáneos para rechazar un movimiento calculado sobre un saldo que otro registro ya modificó.

## Validaciones incorporadas

Las reglas se aplican en el servicio y se reflejan en el formulario Android y en las restricciones del portal. El servidor continúa siendo quien decide si acepta cada operación.

- SKU: de 1 a 40 caracteres; comienza con una letra o número; admite letras latinas sin acentos, números, guion, punto y guion bajo, sin espacios. La unicidad conserva la comparación exacta que ya utilizaba el proyecto.
- Nombre: obligatorio, hasta 120 caracteres. Marca: hasta 60. Modelo: hasta 80. Categoría: nombre obligatorio de hasta 80. Se admiten letras con acentos, números y signos comunes de artículos de computación; no se admite un nombre formado solamente por signos.
- Precio: entre 0 y 9 999 999,99, con hasta dos decimales.
- Existencias y mínimo: enteros entre 0 y 1 000 000. Un movimiento usa una cantidad entre 1 y 1 000 000; una entrada no puede superar el máximo de existencias y una salida no puede dejar saldo negativo.
- Descripciones y notas: hasta 300 caracteres, sin caracteres de control ajenos al texto.

Estos máximos son decisiones de implementación, no cifras exigidas por la rúbrica; deben incorporarse a la siguiente revisión de las reglas del informe. No se añadieron usuarios, contraseñas, aprobaciones ni atribución de movimientos a personas. El historial identifica la operación, el producto, la cantidad, la fecha y la nota.

## Estado para el informe

Este archivo describe el código incorporado. No constituye evidencia de funcionamiento en el teléfono. Las capturas, las comprobaciones en Xiaomi y los resultados de las pruebas siguen pendientes; no corresponde retirar todavía sus marcas amarillas del Word.

La aplicación Android (`assembleDebug`) y el servicio (`dotnet build`) compilaron sin errores. Se generó el instalador en `app/build/outputs/apk/debug/app-debug.apk`. El compilador avisó de una vulnerabilidad conocida en una dependencia SQLite existente (`SQLitePCLRaw.lib.e_sqlite3` 2.1.11); su actualización queda pendiente antes de publicar el servicio. Android informó además de un constructor de Locale obsoleto ya existente. No se ejecutaron pruebas funcionales ni se modificó la base de datos del servidor durante la compilación.

Se conservó una copia de los archivos anteriores en `backups/antes-guardado-local-20261017`.
