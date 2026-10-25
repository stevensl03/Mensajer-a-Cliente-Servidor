# Modelo relacional del servidor

> Fuente: `docs/arquitectura/modelo_relacional.drawio`.
> Describe la base de datos del servidor para implementarla sin ver el diagrama. La base del cliente (H2) está en `cliente_clases_componentes.md` §8.

## 1. Reglas

- Motor actual: **MySQL 8** (los `CHECK` se aplican desde la 8.0.16). El mismo modelo debe funcionar en **PostgreSQL** cambiando solo `bd.motor`, `bd.url` y el driver.
- **Solo el paquete `Persistencia` ejecuta SQL**, y solo `AlmacenarInformacion` llama a `Persistencia`. Ningún componente (Usuarios, ConexionesClientes, etc.) toca estas tablas.
- Claves primarias UUID (`CHAR(36)`) generadas por la aplicación. No se usa `AUTO_INCREMENT` ni `IDENTITY`.
- Valores enumerados con `VARCHAR` + `CHECK`. No se usa `ENUM`.
- Fechas con milisegundos: `DATETIME(3)` en MySQL y `TIMESTAMP(3)` en PostgreSQL. En Java se usa `LocalDateTime`.
- `usuarios` no tiene claves foráneas entrantes: las demás tablas guardan el código como **referencia lógica**.
- Migraciones con **Flyway**, un script por motor:
  - `paquetes/persistencia/src/main/resources/db/migration/mysql/V1__esquema.sql`
  - `paquetes/persistencia/src/main/resources/db/migration/postgresql/V1__esquema.sql`

  `ProveedorConexionJDBC.migrar()` elige la carpeta con `IDialectoSQL.carpetaMigraciones()`. El archivo actual `schema.sql` (que usa `ENUM`) se reemplaza por estas migraciones.
- Los archivos (textos e imágenes) se guardan en `./almacen` y la BD solo guarda la ruta, el tamaño y el hash.

## 2. Tablas

### 2.1 `usuarios`: catálogo de usuarios (lo escribe solo AlmacenarInformación)

| Columna | Tipo | Nulo | Nota |
|---|---|---|---|
| `codigo` | VARCHAR(20) | no | PK |
| `nombres` | VARCHAR(100) | no | |
| `apellidos` | VARCHAR(100) | no | |
| `contrasena_hash` | CHAR(64) | no | SHA-256 en hexadecimal (minúsculas) de la contraseña |
| `programa` | VARCHAR(120) | no | programa académico |
| `rol` | VARCHAR(10) | no | `CHECK (rol IN ('USUARIO','ADMIN'))` |

Se carga una sola vez desde `config/usuarios.txt` con `ImportadorUsuarios` cuando la tabla está vacía (`IRepositorioUsuarios.contar() == 0` → `guardarTodos`). Al arrancar, `AlmacenarInformacion.usuariosRegistrados()` la lee completa y el Composition Root se la entrega al componente `Usuarios`.

### 2.2 `conexiones_usuario`: historial de sesiones

| Columna | Tipo | Nulo | Nota |
|---|---|---|---|
| `id_conexion` | CHAR(36) | no | PK, igual a `idSesion` (id del canal) |
| `codigo_usuario` | VARCHAR(20) | no | referencia lógica a `usuarios` |
| `ip_remota` | VARCHAR(45) | no | IPv4 o IPv6 |
| `fecha_conexion` | DATETIME(3) / TIMESTAMP(3) | no | momento del LOGIN |
| `fecha_desconexion` | DATETIME(3) / TIMESTAMP(3) | sí | `NULL` mientras la sesión está abierta |

Índice: `(codigo_usuario, fecha_desconexion)`.

Se escribe en `ILogEventos.conexion` y `desconexion`, a través de `IAlmacenarInformacion.registrarConexion` y `registrarDesconexion`. Al arrancar, `cerrarHuerfanas(ahora)` pone la fecha de cierre a las filas que quedaron abiertas. **Los usuarios conectados y su cantidad de sesiones se calculan en memoria** (`ConexionesClientes`); esta tabla es solo historial.

### 2.3 `mensajes`: cabecera de cada mensaje

| Columna | Tipo | Nulo | Nota |
|---|---|---|---|
| `id` | CHAR(36) | no | PK (`idMensaje`) |
| `remitente_codigo` | VARCHAR(20) | no | referencia lógica |
| `destinatario_codigo` | VARCHAR(20) | sí | `NULL` si es FORO |
| `conversacion_id` | VARCHAR(41) | sí | `menor|mayor` de los dos códigos en PRIVADO; `NULL` en FORO |
| `canal` | VARCHAR(10) | no | `CHECK (canal IN ('FORO','PRIVADO'))` |
| `tipo_mensaje` | VARCHAR(10) | no | `CHECK (tipo_mensaje IN ('TEXTO','IMAGEN'))` |
| `fecha_envio` | DATETIME(3) / TIMESTAMP(3) | no | |
| `hash_sha256` | CHAR(64) | no | hash del contenido **original** |

Reglas: `CHECK ((canal='FORO' AND destinatario_codigo IS NULL) OR (canal='PRIVADO' AND destinatario_codigo IS NOT NULL))`. Índices: `(canal, fecha_envio)` y `(conversacion_id, fecha_envio)`.

### 2.4 `mensajes_texto`: detalle de un mensaje de texto (relación 1 : 0..1)

| Columna | Tipo | Nulo | Nota |
|---|---|---|---|
| `mensaje_id` | CHAR(36) | no | PK y FK → `mensajes(id)` `ON DELETE CASCADE` |
| `contenido` | TEXT | no | |
| `num_caracteres` | INT | no | |
| `num_palabras` | INT | no | |

### 2.5 `imagenes_mensaje`: versiones de una imagen (relación 1 : 1..6)

| Columna | Tipo | Nulo | Nota |
|---|---|---|---|
| `id` | CHAR(36) | no | PK |
| `mensaje_id` | CHAR(36) | no | FK → `mensajes(id)` `ON DELETE CASCADE` |
| `etapa` | VARCHAR(20) | no | `CHECK (etapa IN ('ORIGINAL','ESCALA_GRISES','SEPIA','ROTAR_180','BRILLO','REDUCCION_TAMANO'))` |
| `nombre_archivo` | VARCHAR(255) | no | |
| `ruta_archivo` | VARCHAR(500) | no | `./almacen/originales/…` o `./almacen/filtradas/…`; siempre con `/` (portable entre Windows y Linux) |
| `tamano_bytes` | BIGINT | no | |
| `hash_sha256` | CHAR(64) | no | hash de **esa** versión |

Restricción única `(mensaje_id, etapa)`. El campo `orden` de `ImagenVersionDTO` se obtiene de la posición de la etapa en el enum `TipoFiltro` (ORIGINAL = 0 … REDUCCION_TAMANO = 5).

### 2.6 `logs_eventos`: logs de la aplicación

| Columna | Tipo | Nulo | Nota |
|---|---|---|---|
| `id` | CHAR(36) | no | PK |
| `tipo` | VARCHAR(20) | no | `CHECK (tipo IN ('CONEXION','DESCONEXION','LOGIN_OK','LOGIN_FALLIDO','CUPO_LLENO','MENSAJE_FORO','MENSAJE_PRIVADO','ERROR'))` |
| `descripcion` | TEXT | no | sin contraseñas ni el contenido completo del mensaje |
| `fecha_hora` | DATETIME(3) / TIMESTAMP(3) | no | |
| `usuario_codigo` | VARCHAR(20) | sí | referencia lógica |
| `ip` | VARCHAR(45) | sí | |

Índices: `(fecha_hora, tipo)` y `(usuario_codigo)`.

## 3. Qué clase lee o escribe cada tabla

| Tabla | Escribe | Lee | Repositorio (Persistencia) |
|---|---|---|---|
| `usuarios` | `ImportadorUsuarios` vía `AlmacenarInformacion.inicializar()` | `AlmacenarInformacion.usuariosRegistrados()` | `IRepositorioUsuarios` |
| `conexiones_usuario` | `LogEventos.conexion` / `desconexion` → `AlmacenarInformacion.registrarConexion` / `registrarDesconexion` | (solo el arranque, para cerrar huérfanas) | `IRepositorioConexiones` |
| `mensajes`, `mensajes_texto`, `imagenes_mensaje` | `AlmacenMensajesMemoria.agregar` → `AlmacenarInformacion.guardarMensaje` | `AlmacenMensajesMemoria.cargarHistorial` → `historialMensajes()` | `IRepositorioMensajes` |
| `logs_eventos` | `LogEventos.registrar` → `AlmacenarInformacion.guardarLog` | `LogEventos.cargarHistorial` → `historialLogs()` | `IRepositorioLogs` |

**Transacción de un mensaje** (`RepositorioMensajesJDBC.guardar`): `INSERT mensajes` + (`INSERT mensajes_texto` **o** 6 × `INSERT imagenes_mensaje`) en una sola transacción. Si falla, `rollback`, y `AlmacenarInformacion` borra los archivos que ya había escrito.

**Lectura del historial**: `mensajes LEFT JOIN mensajes_texto` más una consulta de `imagenes_mensaje` agrupada por `mensaje_id`, ordenadas por `fecha_envio`. `MensajeDTO.nombreArchivo` sale de la fila con etapa `ORIGINAL`.

## 4. DDL de referencia

### 4.1 MySQL (`db/migration/mysql/V1__esquema.sql`)

```sql
CREATE TABLE usuarios (
  codigo           VARCHAR(20)  NOT NULL PRIMARY KEY,
  nombres          VARCHAR(100) NOT NULL,
  apellidos        VARCHAR(100) NOT NULL,
  contrasena_hash  CHAR(64)     NOT NULL,
  programa         VARCHAR(120) NOT NULL,
  rol              VARCHAR(10)  NOT NULL,
  CONSTRAINT ck_usuarios_rol CHECK (rol IN ('USUARIO','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conexiones_usuario (
  id_conexion        CHAR(36)    NOT NULL PRIMARY KEY,
  codigo_usuario     VARCHAR(20) NOT NULL,
  ip_remota          VARCHAR(45) NOT NULL,
  fecha_conexion     DATETIME(3) NOT NULL,
  fecha_desconexion  DATETIME(3) NULL,
  INDEX ix_conexiones_usuario (codigo_usuario, fecha_desconexion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mensajes (
  id                   CHAR(36)    NOT NULL PRIMARY KEY,
  remitente_codigo     VARCHAR(20) NOT NULL,
  destinatario_codigo  VARCHAR(20) NULL,
  conversacion_id      VARCHAR(41) NULL,
  canal                VARCHAR(10) NOT NULL,
  tipo_mensaje         VARCHAR(10) NOT NULL,
  fecha_envio          DATETIME(3) NOT NULL,
  hash_sha256          CHAR(64)    NOT NULL,
  CONSTRAINT ck_mensajes_canal CHECK (canal IN ('FORO','PRIVADO')),
  CONSTRAINT ck_mensajes_tipo  CHECK (tipo_mensaje IN ('TEXTO','IMAGEN')),
  CONSTRAINT ck_mensajes_destino CHECK ((canal='FORO' AND destinatario_codigo IS NULL)
                                     OR (canal='PRIVADO' AND destinatario_codigo IS NOT NULL)),
  INDEX ix_mensajes_canal (canal, fecha_envio),
  INDEX ix_mensajes_conversacion (conversacion_id, fecha_envio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mensajes_texto (
  mensaje_id      CHAR(36) NOT NULL PRIMARY KEY,
  contenido       TEXT     NOT NULL,
  num_caracteres  INT      NOT NULL,
  num_palabras    INT      NOT NULL,
  CONSTRAINT fk_texto_mensaje FOREIGN KEY (mensaje_id) REFERENCES mensajes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE imagenes_mensaje (
  id              CHAR(36)     NOT NULL PRIMARY KEY,
  mensaje_id      CHAR(36)     NOT NULL,
  etapa           VARCHAR(20)  NOT NULL,
  nombre_archivo  VARCHAR(255) NOT NULL,
  ruta_archivo    VARCHAR(500) NOT NULL,
  tamano_bytes    BIGINT       NOT NULL,
  hash_sha256     CHAR(64)     NOT NULL,
  CONSTRAINT uq_imagen_etapa UNIQUE (mensaje_id, etapa),
  CONSTRAINT ck_imagen_etapa CHECK (etapa IN ('ORIGINAL','ESCALA_GRISES','SEPIA','ROTAR_180','BRILLO','REDUCCION_TAMANO')),
  CONSTRAINT fk_imagen_mensaje FOREIGN KEY (mensaje_id) REFERENCES mensajes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE logs_eventos (
  id              CHAR(36)    NOT NULL PRIMARY KEY,
  tipo            VARCHAR(20) NOT NULL,
  descripcion     TEXT        NOT NULL,
  fecha_hora      DATETIME(3) NOT NULL,
  usuario_codigo  VARCHAR(20) NULL,
  ip              VARCHAR(45) NULL,
  CONSTRAINT ck_logs_tipo CHECK (tipo IN ('CONEXION','DESCONEXION','LOGIN_OK','LOGIN_FALLIDO',
                                          'CUPO_LLENO','MENSAJE_FORO','MENSAJE_PRIVADO','ERROR')),
  INDEX ix_logs_fecha_tipo (fecha_hora, tipo),
  INDEX ix_logs_usuario (usuario_codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 4.2 PostgreSQL (`db/migration/postgresql/V1__esquema.sql`)

Es el mismo esquema con estas diferencias:

- `DATETIME(3)` → `TIMESTAMP(3)`.
- Sin `ENGINE`, `CHARSET` ni `COLLATE`.
- Los `INDEX …` dentro del `CREATE TABLE` pasan a sentencias separadas `CREATE INDEX ix_… ON tabla(…)`.

`TEXT`, `CHAR`, `VARCHAR`, `BIGINT`, `INT` y los `CHECK` se escriben igual.

### 4.3 Consultas usadas (SQL estándar, válido en ambos motores)

```sql
-- IRepositorioConexiones.cerrarHuerfanas
UPDATE conexiones_usuario SET fecha_desconexion = ? WHERE fecha_desconexion IS NULL;
-- IRepositorioConexiones.cerrar
UPDATE conexiones_usuario SET fecha_desconexion = ? WHERE id_conexion = ?;
-- IRepositorioUsuarios.contar
SELECT COUNT(*) FROM usuarios;
-- IRepositorioMensajes.listarTodos (cabecera + texto)
SELECT m.*, t.contenido, t.num_caracteres, t.num_palabras
FROM mensajes m LEFT JOIN mensajes_texto t ON t.mensaje_id = m.id
ORDER BY m.fecha_envio;
SELECT * FROM imagenes_mensaje ORDER BY mensaje_id;
```

Siempre `PreparedStatement` con parámetros; nunca se concatenan valores en el SQL.

## 5. Formato de `config/usuarios.txt`

```
codigo;nombres;apellidos;contrasena;programa;rol
20201001;Ana;Pérez;clave123;Ingeniería de Sistemas;USUARIO
ADMIN01;Servidor;Administrador;admin2026;Administración;ADMIN
```

UTF-8, separador `;` y la primera línea es el encabezado. La contraseña viene en claro y se guarda como `SHA-256(UTF-8)` en hexadecimal. Debe haber exactamente un `ADMIN`. Si una línea está mal formada, el arranque se detiene e informa el número de línea.
