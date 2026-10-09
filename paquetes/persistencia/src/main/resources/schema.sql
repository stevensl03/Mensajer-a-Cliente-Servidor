-- ===============================================================
-- Sistema de Mensajeria Academica - Esquema MySQL
-- Fuente: guia.md, seccion 8.4
-- ===============================================================
CREATE DATABASE IF NOT EXISTS mensajeria_academica
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mensajeria_academica;

-- ---------------------------------------------------------------
-- USUARIOS (se cargan desde el archivo plano al arrancar)
-- ---------------------------------------------------------------
CREATE TABLE usuarios (
  codigo              VARCHAR(20)  PRIMARY KEY,
  nombres             VARCHAR(80)  NOT NULL,
  apellidos           VARCHAR(80)  NOT NULL,
  contrasena_hash     CHAR(64)     NOT NULL,
  programa_academico  VARCHAR(100) NOT NULL,
  rol                 ENUM('USUARIO','ADMIN') NOT NULL DEFAULT 'USUARIO'
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- CONEXIONES (multi-sesion: varias filas por usuario)
-- ---------------------------------------------------------------
CREATE TABLE conexiones_usuario (
  id_conexion        VARCHAR(36)  PRIMARY KEY,
  codigo_usuario     VARCHAR(20)  NOT NULL,
  ip_remota          VARCHAR(45)  NOT NULL,
  fecha_conexion     DATETIME(3)  NOT NULL,
  fecha_desconexion  DATETIME(3)  NULL,
  motivo_cierre      ENUM('NORMAL','FORZADO','ERROR') NULL,
  CONSTRAINT fk_conexion_usuario
    FOREIGN KEY (codigo_usuario) REFERENCES usuarios(codigo),
  INDEX idx_conexion_usuario (codigo_usuario)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- MENSAJES (datos comunes)
-- ---------------------------------------------------------------
CREATE TABLE mensajes (
  id                   CHAR(36)    PRIMARY KEY,
  remitente_codigo     VARCHAR(20) NOT NULL,
  destinatario_codigo  VARCHAR(20) NULL,
  id_conexion          VARCHAR(36) NULL,
  canal                ENUM('FORO','PRIVADO') NOT NULL,
  tipo_mensaje         ENUM('TEXTO','IMAGEN') NOT NULL,
  fecha_envio          DATETIME(3) NOT NULL,
  hash_sha256          CHAR(64)    NOT NULL,
  CONSTRAINT fk_msg_remitente
    FOREIGN KEY (remitente_codigo)    REFERENCES usuarios(codigo),
  CONSTRAINT fk_msg_destinatario
    FOREIGN KEY (destinatario_codigo) REFERENCES usuarios(codigo),
  CONSTRAINT fk_msg_conexion
    FOREIGN KEY (id_conexion)         REFERENCES conexiones_usuario(id_conexion),
  CONSTRAINT chk_canal_destino CHECK (
    (canal = 'FORO'    AND destinatario_codigo IS NULL) OR
    (canal = 'PRIVADO' AND destinatario_codigo IS NOT NULL)
  ),
  INDEX idx_msg_remitente    (remitente_codigo),
  INDEX idx_msg_destinatario (destinatario_codigo),
  INDEX idx_msg_fecha        (fecha_envio)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- SUBTIPO TEXTO (relacion 1 : 0..1 con mensajes)
-- ---------------------------------------------------------------
CREATE TABLE mensajes_texto (
  mensaje_id      CHAR(36)   PRIMARY KEY,
  contenido       MEDIUMTEXT NOT NULL,
  num_caracteres  INT        NOT NULL,
  num_palabras    INT        NOT NULL,
  ruta_archivo    VARCHAR(500) NULL,
  CONSTRAINT fk_texto_mensaje
    FOREIGN KEY (mensaje_id) REFERENCES mensajes(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- SUBTIPO IMAGEN (6 filas por mensaje: orden 0..5)
-- ---------------------------------------------------------------
CREATE TABLE imagenes_mensaje (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  mensaje_id       CHAR(36)     NOT NULL,
  orden_secuencia  TINYINT      NOT NULL,
  etapa_filtro     ENUM('ORIGINAL','ESCALA_GRISES','SEPIA',
                        'ROTAR_180','BRILLO','REDUCCION_TAMANO') NOT NULL,
  nombre_archivo   VARCHAR(255) NOT NULL,
  datos            LONGBLOB     NOT NULL,
  tamano_bytes     INT          NOT NULL,
  ancho            INT          NULL,
  alto             INT          NULL,
  ruta_archivo     VARCHAR(500) NULL,
  hash_sha256      CHAR(64)     NOT NULL,
  CONSTRAINT fk_imagen_mensaje
    FOREIGN KEY (mensaje_id) REFERENCES mensajes(id) ON DELETE CASCADE,
  CONSTRAINT uq_mensaje_orden UNIQUE (mensaje_id, orden_secuencia),
  CONSTRAINT chk_orden CHECK (orden_secuencia BETWEEN 0 AND 5)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- LOGS (usuario_codigo SIN FK a proposito)
-- ---------------------------------------------------------------
CREATE TABLE logs_eventos (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  tipo            ENUM('CONEXION','DESCONEXION','LOGIN_OK','LOGIN_FALLIDO',
                       'CUPO_LLENO','MENSAJE_FORO','MENSAJE_PRIVADO',
                       'DIFUSION','CIERRE_FORZADO','ERROR') NOT NULL,
  nivel           ENUM('INFO','WARN','ERROR') NOT NULL,
  descripcion     TEXT        NOT NULL,
  fecha_hora      DATETIME(3) NOT NULL,
  usuario_codigo  VARCHAR(20) NULL,
  ip              VARCHAR(45) NULL,
  mensaje_id      CHAR(36)    NULL,
  id_conexion     VARCHAR(36) NULL,
  CONSTRAINT fk_log_mensaje
    FOREIGN KEY (mensaje_id)  REFERENCES mensajes(id),
  CONSTRAINT fk_log_conexion
    FOREIGN KEY (id_conexion) REFERENCES conexiones_usuario(id_conexion),
  INDEX idx_log_fecha   (fecha_hora),
  INDEX idx_log_usuario (usuario_codigo)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- INFORMES (instantanea JSON del informe generado)
-- ---------------------------------------------------------------
CREATE TABLE informes (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  tipo            ENUM('USUARIOS_REGISTRADOS','USUARIOS_CONECTADOS',
                       'MENSAJES','LOGS') NOT NULL,
  generado_en     DATETIME(3) NOT NULL,
  generado_por    VARCHAR(20) NULL,
  contenido_json  JSON        NOT NULL,
  CONSTRAINT fk_informe_usuario
    FOREIGN KEY (generado_por) REFERENCES usuarios(codigo)
) ENGINE=InnoDB;
