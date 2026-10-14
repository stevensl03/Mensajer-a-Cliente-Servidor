-- Esquema inicial del servidor (MySQL 8.0.16+). Ver docs/arquitectura/modelo_relacional.md.

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
