# StockFácil PC — avance inicial

## Avance del 17 de octubre de 2026

Continuación: se alinearon las validaciones del portal y Android, se bloquearon los envíos repetidos mientras se guarda y se mejoraron los avisos ante respuestas inciertas. [Estado de los formularios y límites pendientes](docs/Avance_formularios.md).

La aplicación incorpora borradores de productos en SQLite y el botón **Enviar al portal**. Los borradores se pueden consultar, modificar y descartar antes de enviarlos. La primera conexión carga las categorías necesarias. Las demás operaciones siguen requiriendo conexión. Es necesario actualizar tanto la aplicación como el servicio; todavía falta comprobar este recorrido en el Xiaomi. [Detalle del avance y límites](docs/Avance_guardado_local.md).

La descripción inicial que sigue se conserva como antecedente; el detalle del avance anterior precisa el funcionamiento actual del guardado local.

Aplicación Android para registrar y consultar el inventario de componentes y accesorios de computación.

## Qué incluye este avance

- Proyecto Android Studio en Kotlin con layouts XML y ViewBinding.
- Pantalla de inventario con lista personalizada en `RecyclerView`.
- Almacenamiento local SQLite mediante `SQLiteOpenHelper`.
- Registro, búsqueda, modificación y eliminación de productos.
- Validación de campos y código de producto único.
- Seis productos ficticios de demostración, cargados solo si el inventario está vacío.
- API REST inicial en ASP.NET Core con SQLite para productos, categorías y movimientos de stock.
- Portal administrativo con resumen del inventario, alertas de stock bajo y vistas para productos, categorías y movimientos.
- Registro de ingresos y salidas de stock desde Android; el inventario compartido se actualiza mediante la API REST.
- App móvil con secciones de resumen, productos, categorías y movimientos, conectadas a los mismos datos que el portal.

## Qué falta para completar el proyecto del curso

- Definir el contexto real del negocio y sustentar el diagnóstico SEPTE con fuentes.
- Completar el informe, objetivos SMART, casos de uso y reglas de negocio.
- Documentar el modelo entidad-relación y preparar los mockups.
- Definir la estrategia de sincronización y respaldo local con SQLite.
- Alojar la API y la base de datos remota; el archivo SQLite actual es solo para desarrollo.
- Completar el portal para administrar categorías y movimientos de stock.
- Agregar consumo de un segundo servicio REST.
- Preparar y grabar la demostración final.

## Abrir en Android Studio

Abre la carpeta `StockFacil` desde Android Studio. Si Android Studio pide seleccionar un SDK o sincronizar Gradle, acepta la configuración que corresponda a tu instalación. Este proyecto usa el mismo Gradle y catálogo de versiones que el proyecto Android ya existente en este equipo.

## API REST y portal administrativo

La API está en `api/StockFacil.Api` y el portal se sirve desde la misma API. Requiere .NET 10 SDK. Desde una terminal abierta en esa carpeta, ejecuta `dotnet run`; abre `http://localhost:5080` en la computadora para ver el portal. El servicio escucha en el puerto 5080 también por la interfaz LAN. El endpoint de comprobación es `/api/health`.

Rutas principales: `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}`, `GET/POST/PUT/DELETE /api/categories` y `GET/POST /api/stock-movements`. La base de datos de desarrollo se crea como `stockfacil-api.db` junto al proyecto.

La app Android consume la API en `http://192.168.18.9:5080` y realiza las operaciones de productos a través del servicio. Si cambia la IP que el router asignó a la computadora, actualiza `BASE_URL` en `app/src/main/java/pe/edu/cibertec/stockfacil/data/StockFacilApi.kt`. El Xiaomi debe conectarse al Wi-Fi del mismo router que conecta la computadora por cable LAN. La API es solo para pruebas en la red local; no debe exponerse públicamente con la configuración de desarrollo actual.
