# Publicación Render + Neon — punto de continuación

## Estado del 18 de octubre de 2026

- Cuentas creadas por el usuario. Render: Docker, rama main, Oregon, Free; aún sin desplegar.
- Neon: proyecto StockFacilPc, Oregon, rama production, base neondb; conexión agrupada disponible. No guardar contraseña en Git ni en el chat.
- Se añadió Npgsql EF Core 10.0.0 y selección PostgreSQL mediante DATABASE_URL. Sin esta variable se conserva SQLite local; en Render su ausencia detiene el arranque para evitar guardar en disco temporal.
- Dockerfile en la raíz publica únicamente la API y el portal incluido en wwwroot. .dockerignore excluye datos locales y compilaciones.
- Compilación Release correcta; no se ejecutaron pruebas ni se conectó con Neon. Falta construir la imagen Docker y verificar el despliegue real.
- Sigue una advertencia previa NU1903 de SQLitePCLRaw.lib.e_sqlite3 2.1.11: revisar actualización antes de cerrar la entrega.
- Los cambios están guardados localmente, aún sin commit ni push.

## Pasos siguientes

1. Revisar cambios, confirmar y subir a Luisitogustavo/StockFacil-PC, rama main.
2. Render: Root Directory vacío; Dockerfile Path ./Dockerfile; Free; puerto 10000 (definido por Dockerfile). Health Check Path /api/health.
3. En Render añadir DATABASE_URL, pegando directamente la URL PostgreSQL de Neon mediante Copy snippet; solo la URL, sin comando psql ni comillas. No enviarla por chat. Se valida TLS y se exige channel binding.
4. Desplegar y revisar los registros, sin compartir secretos. Al primer arranque se crean las tablas en una base vacía y se agregan los datos de demostración existentes en el inicializador.
5. La base local NO se transfiere automáticamente. Decidir si se requiere migrar los registros anteriores o trabajar con datos de demostración. No borrar la base local ni los borradores Android.
6. Copiar la URL pública HTTPS de Render y actualizar BASE_URL en app/src/main/java/pe/edu/cibertec/stockfacil/data/StockFacilApi.kt. Aún apunta al entorno local.
7. Compilar Android y, cuando el usuario autorice la comprobación, verificar catálogo, registro, envío de borradores, movimientos y persistencia después de reiniciar el servicio. Registrar evidencia real.

## Límites

No hay cuentas ni permisos de usuario: quien acceda a la demo pública puede modificar sus datos. Usar únicamente datos ficticios; no presentarla como sistema empresarial con acceso restringido.

Render Free suspende el servicio por inactividad. La app tiene tiempos de espera cortos: abrir el portal y esperar a que responda antes de demostrar el envío desde Android; revisar experiencia de arranque en frío.

EnsureCreated inicializa una base vacía; no reemplaza una estrategia de migraciones futuras. Mantener una sola instancia durante la creación inicial.

Fuentes: https://render.com/docs/free ; https://render.com/docs/docker ; https://www.npgsql.org/efcore/release-notes/10.0.html

## Actualizacion 18/10/2026
- SQLite actualizado a 10.0.13 y Npgsql a 10.0.3.
- Ultima revision NuGet sin vulnerabilidades reportadas.
- API Release y Android Debug compilados correctamente.
- API local SQLite: health, categorias y productos probados.
- Neon y Render: pruebas reales pendientes.
- GitHub: commit y push pendientes.
