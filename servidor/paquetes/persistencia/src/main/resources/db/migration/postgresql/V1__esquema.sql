-- Esquema inicial del servidor (PostgreSQL). Ver docs/arquitectura/modelo_relacional.md.

CREATE TABLE usuarios (
  codigo           VARCHAR(20)  NOT NULL PRIMARY KEY,
  nombres          VARCHAR(100) NOT NULL,
  apellidos        VARCHAR(100) NOT NULL,
  contrasena_hash  CHAR(64)     NOT NULL,
  programa         VARCHAR(120) NOT NULL,
  rol              VARCHAR(10)  NOT NULL,
  CONSTRAINT ck_usuarios_rol CHECK (rol IN ('USUARIO','ADMIN'))
);

CREATE TABLE conexiones_usuario (
  id_conexion        CHAR(36)     NOT NULL PRIMARY KEY,
  codigo_usuario     VARCHAR(20)  NOT NULL,
  ip_remota          VARCHAR(45)  NOT NULL,
  fecha_conexion     TIMESTAMP(3) NOT NULL,
  fecha_desconexion  TIMESTAMP(3) NULL
);
CREATE INDEX ix_conexiones_usuario ON conexiones_usuario (codigo_usuario, fecha_desconexion);

CREATE TABLE mensajes (
  id                   CHAR(36)     NOT NULL PRIMARY KEY,
  remitente_codigo     VARCHAR(20)  NOT NULL,
  destinatario_codigo  VARCHAR(20)  NULL,
  conversacion_id      VARCHAR(41)  NULL,
  canal                VARCHAR(10)  NOT NULL,
  tipo_mensaje         VARCHAR(10)  NOT NULL,
  fecha_envio          TIMESTAMP(3) NOT NULL,
  hash_sha256          CHAR(64)     NOT NULL,
  CONSTRAINT ck_mensajes_canal CHECK (canal IN ('FORO','PRIVADO')),
  CONSTRAINT ck_mensajes_tipo  CHECK (tipo_mensaje IN ('TEXTO','IMAGEN')),
  CONSTRAINT ck_mensajes_destino CHECK ((canal='FORO' AND destinatario_codigo IS NULL)
                                     OR (canal='PRIVADO' AND destinatario_codigo IS NOT NULL))
);
CREATE INDEX ix_mensajes_canal ON mensajes (canal, fecha_envio);
CREATE INDEX ix_mensajes_conversacion ON mensajes (conversacion_id, fecha_envio);

CREATE TABLE mensajes_texto (
  mensaje_id      CHAR(36) NOT NULL PRIMARY KEY,
  contenido       TEXT     NOT NULL,
  num_caracteres  INT      NOT NULL,
  num_palabras    INT      NOT NULL,
  CONSTRAINT fk_texto_mensaje FOREIGN KEY (mensaje_id) REFERENCES mensajes(id) ON DELETE CASCADE
);

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
);

CREATE TABLE logs_eventos (
  id              CHAR(36)     NOT NULL PRIMARY KEY,
  tipo            VARCHAR(20)  NOT NULL,
  descripcion     TEXT         NOT NULL,
  fecha_hora      TIMESTAMP(3) NOT NULL,
  usuario_codigo  VARCHAR(20)  NULL,
  ip              VARCHAR(45)  NULL,
  CONSTRAINT ck_logs_tipo CHECK (tipo IN ('CONEXION','DESCONEXION','LOGIN_OK','LOGIN_FALLIDO',
                                          'CUPO_LLENO','MENSAJE_FORO','MENSAJE_PRIVADO','ERROR'))
);
CREATE INDEX ix_logs_fecha_tipo ON logs_eventos (fecha_hora, tipo);
CREATE INDEX ix_logs_usuario ON logs_eventos (usuario_codigo);
