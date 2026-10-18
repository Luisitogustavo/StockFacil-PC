# Continuación del código de StockFácil PC

Fecha: 17 de octubre de 2026.

## Alcance incorporado

Se mantienen productos, categorías y movimientos, con borradores de productos guardados en SQLite y envío manual. No se agregaron usuarios ni aprobaciones.

- El portal valida nombres, SKU, precios, cantidades, categorías y notas antes de enviar. Los límites coinciden con los del servicio; el nombre de categoría admite 80 caracteres.
- Los formularios deshabilitan los controles mientras esperan la respuesta. Esto evita enviar varias solicitudes por pulsaciones repetidas durante el guardado. No equivale a reconocer reintentos posteriores de movimientos.
- Ante una respuesta incierta al registrar un movimiento, Android ofrece consultar el historial y el portal indica revisarlo antes de volver a registrar. No se reenvía automáticamente. La protección persistente contra duplicados por clave de envío sigue correspondiendo a los borradores de productos.
- Android permite incluir el motivo opcional del movimiento. Comprueba también el máximo de unidades después de una entrada.
- Los selectores Android conservan la lista con la que se abrió el formulario para evitar que una actualización cambie el producto o categoría seleccionado por posición.
- El portal distingue datos actualizados de datos conservados tras perder conexión. Las respuestas de consultas anteriores no sustituyen una consulta más reciente.
- El servicio responde con un aviso comprensible ante conflictos de actualización o restricciones de la base. La desactivación de un producto también interviene en la comprobación de cambios simultáneos.
- El historial comunica las fechas almacenadas como UTC para mostrarlas en la hora local del dispositivo; los movimientos de la misma fecha se ordenan por su identificador descendente.

## Comprobación y límites

La aplicación Android y el servicio se compilaron. Se revisó la sintaxis JavaScript del portal. No se ejecutaron pruebas funcionales, no se instaló la app en el Xiaomi y no se publicó el servicio. La base de datos de trabajo no se modificó mediante estas compilaciones.

Permanece la advertencia de compilación por la dependencia existente SQLitePCLRaw.lib.e_sqlite3 2.1.11. Su actualización debe resolverse antes de publicar. También quedan pendientes las evidencias del recorrido, el alojamiento y la decisión sobre mantenimiento de movimientos sin perder el historial. Este avance no declara completo el proyecto ni elimina los pendientes de evidencia del informe.

Para usar este avance deben actualizarse la aplicación y el servicio. La compilación por sí sola no reemplaza las versiones que estén ejecutándose.

## Conexión durante la sesión con el emulador

Se inició el servicio local en la computadora y se instaló la aplicación en el Pixel 6 virtual. En Android Emulator la app utiliza el alias 10.0.2.2 para llegar al servicio de la computadora; el teléfono físico conserva su dirección LAN de desarrollo. El servicio de esta sesión escucha únicamente en la computadora, por lo que el acceso desde el Xiaomi requiere preparar después el enlace de red correspondiente.

Se incorporó la declaración y solicitud del permiso ACCESS_LOCAL_NETWORK requerido por Android 17 para apps con target SDK 37. Si se rechaza, se conservan los borradores y se muestra cómo autorizar la conexión; no se concede el permiso automáticamente. La versión con este cambio compiló y se instaló, pero se espera la autorización del usuario y su confirmación de carga antes de documentar este recorrido como aprobado.

Fuentes oficiales: [red del emulador](https://developer.android.com/studio/run/emulator-networking-address) y [permiso de red local](https://developer.android.com/privacy-and-security/local-network-permission).
