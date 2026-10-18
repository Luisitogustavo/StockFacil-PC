# StockFácil PC
### Sistema de gestión de inventario de productos informáticos

**Proyecto académico — Cibertec**

StockFácil PC es un sistema de gestión de inventarios orientado al registro, consulta y control de componentes y accesorios de computación.

Integra una aplicación móvil Android con un portal administrativo web, utilizando una API REST y una base de datos SQLite para administrar información de productos, categorías y movimientos de inventario.

## Tecnologías utilizadas

| Componente | Tecnología |
|---|---|
| Aplicación móvil | Kotlin, Android Studio y XML |
| Backend | ASP.NET Core (.NET 10) |
| Base de datos | SQLite y Entity Framework Core |
| Comunicación | API REST / HTTP |
| Control de versiones | Git y GitHub |

## Funcionalidades principales

- Registro, consulta, edición y eliminación de productos.
- Administración de categorías.
- Búsqueda de productos mediante nombre o código SKU.
- Control de entradas y salidas del inventario.
- Alertas de productos con stock bajo.
- Sincronización entre Android y el portal administrativo.
- Almacenamiento local de borradores mediante SQLite.
- Validación de datos y prevención de registros duplicados.

## Requisitos para ejecutar el proyecto

Es necesario disponer de:

- Android Studio con Android SDK compatible con el proyecto.
- Emulador Android o dispositivo físico compatible (Android 7.0 o superior).
- .NET SDK 10.
- Git, opcional si se descarga el proyecto como ZIP.

## Instalación y ejecución

### 1. Descargar el proyecto

Desde GitHub, seleccionar **Code → Download ZIP** y extraer el contenido en una carpeta.

Alternativamente, utilizar:

```bash
git clone https://github.com/Luisitogustavo/StockFacil-PC.git
```

### 2. Ejecutar el backend

Abrir PowerShell en la carpeta principal del proyecto y ejecutar:

```powershell
dotnet run --project .\api\StockFacil.Api\StockFacil.Api.csproj
```

Mantener esa terminal abierta durante las pruebas.

Comprobar el estado del servicio en:

`http://localhost:5080/api/health`

El servicio crea automáticamente su base de datos SQLite cuando se inicia por primera vez e incorpora categorías y productos ficticios de demostración.

### 3. Ejecutar el portal administrativo

Con el backend activo, abrir:

`http://localhost:5080`

Desde el portal se podrá consultar y administrar el inventario.

### 4. Ejecutar la aplicación Android

1. Abrir Android Studio.
2. Seleccionar **Open** y elegir la carpeta principal del proyecto.
3. Esperar la sincronización de Gradle.
4. Seleccionar un emulador Android, como Pixel 6.
5. Ejecutar el módulo `app` mediante el botón **Run ▶**.

**Importante:** iniciar primero la API para que la aplicación pueda consultar los datos compartidos.

En el emulador Android se utiliza la dirección `http://10.0.2.2:5080` para acceder al servidor local de la computadora.

Para utilizar un teléfono físico, ambos dispositivos deben estar conectados a la misma red y se debe configurar la dirección IP de la computadora en:

`app/src/main/java/pe/edu/cibertec/stockfacil/data/StockFacilApi.kt`

La API utiliza HTTP para las pruebas locales. Esta configuración no está preparada para exponerse directamente a Internet.

## Prueba de funcionamiento

Para verificar la comunicación entre los componentes:

1. Iniciar la API.
2. Abrir el portal administrativo.
3. Ejecutar la aplicación Android.
4. Registrar un producto de prueba en Android.
5. Enviar el producto al portal, si se guardó primero como borrador.
6. Actualizar el portal administrativo.
7. Verificar que el producto aparezca con su código, precio y cantidad correspondiente.

Esta secuencia permite comprobar la comunicación entre la aplicación móvil, la API REST y la base de datos SQLite.

## Estructura del proyecto

```text
StockFacil-PC/
├── app/                 # Aplicación Android
├── api/StockFacil.Api/  # API REST y portal web
├── docs/                # Documentación académica
├── gradle/              # Configuración de Gradle
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Documentación del proyecto

Los avances y documentos académicos se encuentran en la carpeta `docs`.

Documentos complementarios:

- [Definición del proyecto](docs/Definicion_Proyecto.md)
- [Avance de formularios](docs/Avance_formularios.md)
- [Almacenamiento local](docs/Avance_guardado_local.md)

## Colaboración

Los integrantes del equipo pueden descargar el repositorio y proponer mejoras.

Se recomienda desarrollar cada funcionalidad en una rama independiente y utilizar Pull Requests para revisar los cambios antes de incorporarlos a `main`.

## Estado del proyecto

**Versión:** 0.1 — Desarrollo académico en curso.

Las funcionalidades descritas corresponden al avance actual. El sistema continúa en proceso de desarrollo, pruebas y documentación.

---

*StockFácil PC — Proyecto académico de Cibertec.*
