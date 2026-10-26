# Aceptación del servidor (Fase 8)

Cada requisito de la tabla §1.1 de `arquitectura/servidor_clases_componentes.md` con la prueba que lo demuestra.

**Prueba de punta a punta:** `ServidorPuntaAPuntaTest` (en `servidor/presentacion/inicio-servidor/src/test`).
- Arranca el servidor real, con el mismo ensamblado que `ServidorApp`.
- Usa clientes Java que hablan el protocolo §7 por TCP.
- Al final revisa las tablas de la BD y la carpeta `almacen`.
- **Resultados** (25 de octubre de 2026): en verde contra **MySQL 8.4** y contra **PostgreSQL 17**.

En la tabla, «E2E» se refiere a esa prueba.

| Requisito (§1.1) | Unitarias / integración | E2E y verificación manual |
|---|---|---|
| Usuarios con código, nombres, apellidos, contraseña y programa; validación por código y contraseña | `ServicioUsuariosTest` (autenticación, no expone el hash) | LOGIN correcto y `CREDENCIALES_INVALIDAS`; el usuario de la respuesta no trae `contrasenaHash` |
| Usuarios cargados inicialmente desde un archivo plano | `ImportadorUsuariosTest` (formato, un único ADMIN, `config/usuarios.txt` válido) | 10 filas en `usuarios` tras el arranque con la BD vacía; `LISTAR_USUARIOS` devuelve 10 |
| Restringir el número de usuarios conectados con un archivo de configuración | `GestorConexionesTest` (cupo global, liberación idempotente), `ConfiguracionServidorTest` | `servidor.maxSesiones=3`: la cuarta sesión recibe `CUPO_LLENO` y se le cierra el canal; un evento `CUPO_LLENO` en `logs_eventos` |
| Información y logs en una BD relacional, con la conexión configurable | `PersistenciaIntegracionTest` (MySQL 8.4 y PostgreSQL 17), `LogEventosTest` | Filas en `mensajes`, `mensajes_texto`, `imagenes_mensaje`, `logs_eventos` y `conexiones_usuario`; conexión leída de `bd.properties` |
| Guardar en un directorio toda la información enviada | `AlmacenarInformacionTest`, `AlmacenamientoDirectorioLocalTest` | `almacen/textos` con 3 archivos, `almacen/originales` con 1 y `almacen/filtradas` con 5; rutas guardadas con `/` |
| Mensajes en estructuras de datos y consultas desde allí | `AlmacenMensajesMemoriaTest` (foro, conversación, orden, historial) | `HISTORIAL` del foro y de la conversación Ana–Luis; `INFORME MENSAJES` |
| Texto: SHA-256, número de caracteres y de palabras | `ProcesadorTextoTest` (tildes, saltos de línea, emojis, UTF-8) | «Hola comunidad, ñandú 😀»: 23 caracteres, 4 palabras y hash de 64 caracteres |
| Imagen: SHA-256 y 5 filtros secuenciales; cada filtro produce una imagen que se guarda | `PipelineFiltrosTest` (orden, encadenado, entrada sin modificar), `ServiciosProcesamientoTest` | El push trae 6 versiones; `OBTENER_IMAGEN` ORIGINAL devuelve los bytes enviados y SEPIA responde; 6 filas en `imagenes_mensaje` y 6 archivos |
| Seguir la arquitectura del diagrama | Comprobaciones de la matriz §4 con `grep` (fases 0–4) | Revisión de las fases 5–7: la fachada solo usa interfaces y las vistas solo la fachada |
| Enviar mensajes a todos los usuarios | `EnrutadorMensajesTest`, `FachadaServidorTest` | El mensaje de Ana al foro llega a Luis y a Ana; el aviso del ADMIN llega a los conectados |
| Un usuario se puede conectar más de una vez | `GestorConexionesTest` | Ana con 2 sesiones en `USUARIOS_CONECTADOS`; 3 filas en `conexiones_usuario` |
| Cerrar conexiones de los usuarios | `FachadaServidorTest` (cierre por el ADMIN, apagado), `ServidorTCPTest` | LOGOUT cierra el canal y avisa `USUARIO_DESCONECTADO`; el apagado envía `SESION_CERRADA` y cierra todas las sesiones. Manual: botón **Cerrar** del Monitor (motivo `CERRADA_POR_ADMIN`) |
| Mostrar en consola la información de cada mensaje que llega | `VistaConsolaTest.onMensajeImprimeElDetalleDeCadaMensaje`, `LogEventosTest` (formato §10) | Manual: líneas de Logback por evento y tarjetas en vivo del Monitor |
| Informes: usuarios registrados, conectados (con la cantidad de veces), mensajes con su detalle y logs | `FachadaServidorTest.losCuatroInformes`, `PanelInformesTest` | Los cuatro informes por el comando `INFORME`, incluido el filtro de logs por tipo. Manual: pestaña Informes con la ficha del mensaje y sus 6 imágenes |
| Capas y componentes; inversión e inyección de dependencias; fábricas | Matriz §4; `FabricaVistasTest` | `ServidorApp` es la única raíz de composición, y la prueba de punta a punta usa ese mismo ensamblado |
| Protocolo TCP/IP | `CodificadorTramasTest`, `ServidorTCPTest` | Toda la prueba de punta a punta corre sobre sockets reales con la trama `[int32][JSON]` |

## Cómo ejecutarla

```bash
cd servidor
mvn verify -Pintegracion -pl presentacion/inicio-servidor -am -Dbd.motor=MYSQL -Dbd.url=jdbc:mysql://localhost:3306/mensajeria_e2e -Dbd.usuario=root -Dbd.clave=secreto
```

- Usa una BD **solo para pruebas**, porque se vacía al empezar.
- Con `-pl presentacion/inicio-servidor -am` también corren las pruebas de integración de `persistencia` contra la misma BD.
- Para PostgreSQL: `-Dbd.motor=POSTGRESQL -Dbd.url=jdbc:postgresql://…`.
