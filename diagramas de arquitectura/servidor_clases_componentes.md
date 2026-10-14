# Servidor: diagrama de clases por componentes

> Fuente: `diagramas de arquitectura/servidor_clases_componentes.drawio`.
> Este documento describe ese diagrama en texto para que un asistente de código pueda implementarlo **sin ver el archivo .drawio**. Si el código y este documento no coinciden, manda este documento (y por encima de él, el diagrama original y `.claude/contexto.txt`).

## 0. Reglas que no se negocian

**Jerarquía de diseño (en caso de conflicto, gana el de arriba):**

1. Diagrama original de paquetes y componentes del servidor (sección 2).
2. `.claude/contexto.txt`.
3. Consideraciones acordadas con el usuario (sección 1.2).
4. `.claude/skills/SKILL.md`: solo se usan sus reglas de concurrencia, framing TCP y pruebas que **no** contradigan lo anterior. Su catálogo de módulos, el formato binario, los límites de UDP, las salas y el uso de `System.Logger` **no aplican**.

**Reglas de estructura:**

- Solo se agregan detalles o piezas nuevas que no cambien el sentido del diagrama original. No se agregan flechas entre contenedores que el original no tenga (ver la matriz de la sección 4).
- Los **componentes** (amarillos) no conocen a nadie más: solo su propia interfaz y los transversales. Quien los coordina es `FachadaDeServicios`.
- **Solo `AlmacenarInformacion` habla con `Persistencia`.** `Usuarios` y `ConexionesClientes` no la conocen.
- **Solo `ProtocoloComunicacion` conoce sockets.** Hoy implementa TCP. Para cambiar de protocolo se toca solo ese componente (receta en la sección 12).
- **Solo `ProtocoloClienteServidor` conoce el formato JSON del protocolo.**
- **Solo `InicioServidor` (`ServidorApp`) instancia clases concretas** (Composition Root). Todo lo demás recibe interfaces por constructor.
- Java 21 sin APIs *preview*. No usar serialización nativa de Java en la red.
- No inventar versiones de librerías: verificar la última estable en Maven Central antes de agregarla.

## 1. Qué debe hacer el servidor

### 1.1 Requisitos de `contexto.txt` y dónde se cumplen

| Requisito | Dónde se cumple |
|---|---|
| Usuarios con código, nombres, apellidos, contraseña y programa; se validan por código y contraseña | `Usuarios` (`ServicioUsuarios.autenticar`), con contraseña guardada como SHA-256 |
| Usuarios registrados inicialmente en un archivo plano | `config/usuarios.txt`, que `ImportadorUsuarios` (en `AlmacenarInformacion`) carga a la BD la primera vez |
| Restringir el número de usuarios conectados con un archivo de configuración | `servidor.maxSesiones` → `GestorConexiones.cupo : Semaphore` |
| Información y logs en MySQL; conexión configurable | `Persistencia` + `config/bd.properties`; logs en la tabla `logs_eventos` |
| Guardar en un directorio toda la información enviada | `./almacen/textos`, `./almacen/originales` y `./almacen/filtradas` (`AlmacenamientoDirectorioLocal`) |
| Todos los mensajes en estructuras de datos, y consultarlos desde allí | `AlmacenMensajesMemoria` (mapas, deque y colas concurrentes); los informes y el historial leen de allí |
| Texto: SHA-256, número de caracteres y número de palabras | Subcomponente `ProcesamientoTexto` |
| Imagen: SHA-256 y 5 filtros secuenciales; cada filtro produce una imagen nueva que se guarda | Subcomponente `FiltrosImagen` (`PipelineFiltros`) + `AlmacenarInformacion` |
| Seguir la arquitectura del diagrama | Secciones 2 y 4 |
| Enviar mensajes a todos los usuarios | Canal `FORO` (`EnrutadorMensajes`) |
| Un usuario se puede conectar más de una vez | `GestorConexiones` acepta varias sesiones por código |
| Cerrar conexiones de usuarios | `IFachadaServidor.cerrarSesion`, desde la consola y el escritorio |
| Mostrar en consola la información de cada mensaje que llega | `LogEventos` (Logback → consola) y `VistaConsola.onMensaje` |
| Informes: usuarios registrados, conectados (con la cantidad de veces), mensajes con su detalle y logs | `GeneradorInformes` + comando `INFORME` |
| Capas y componentes; DIP, DI y fábricas | Todo el diagrama |
| Protocolo TCP/IP | `ProtocoloComunicacion` / `tcp` |

### 1.2 Consideraciones acordadas

- No hay CRUD de usuarios: unos 10 usuarios precargados.
- La mensajería es tipo chat: canal **FORO** (todos) y **PRIVADO** (un usuario concreto, incluido el ADMIN).
- El **ADMIN** es un usuario con `Rol.ADMIN` que escribe desde las vistas del servidor. No ocupa cupo de sesiones y para los clientes siempre aparece como conectado.
- El parámetro **`servidor.maxSesiones = N`** es un cupo **global** de sesiones. Si un mismo usuario abre las N sesiones, los demás no pueden entrar.
- Logs con **SLF4J + Logback** (consola con colores y archivo con rotación diaria).
- Caché de dos niveles: **L1 Caffeine + L2 Redis** (L2 se puede apagar por configuración).
- El cliente ve todos los informes. Los informes y listados viajan como **JSON**.
- Solo texto e imágenes.
- Hoy solo **TCP**; el cambio de protocolo debe ser sencillo y quedar dentro de `ProtocoloComunicacion`.
- La BD es MySQL, pero el código debe funcionar también con PostgreSQL.
- `Servicios` se divide en dos subcomponentes (texto e imágenes) para respetar la responsabilidad única.

## 2. Estructura del diagrama original (obligatoria)

```
┌──────────── Presentación ────────────┐ ┌──────────────────────── Servicios ─────────────────────────┐ ┌─ Persistencia ─┐
│                    ┌─► VistaConsola ─┼─┼─►                                                          │ │                │
│ InicioServidor ────┤                 │ │  FachadaDeServicios ─► «InterfazProtocoloComunicacion» ─○ ProtocoloComunicacion
│                    └─► VistaEscritorio┼─┼─►        │          ─► «InterfazClienteServidor»       ─○ ProtocoloClienteServidor
└──────────────────────────────────────┘ │           │          ─► «InterfazServiciosDisponibles»  ─○ Servicios
                                         │           │          ─► «InterfazUsuariosDisponibles»   ─○ Usuarios
                                         │           │          ─► «InterfazGestorMensajes»        ─○ GestorColasDeMensajes
                                         │           ├─► Mensajes ──────┐                             │ │                │
                                         │           ├─► LogDeEventos ──┴─► AlmacenarInformacion ─────┼─┼─► Persistencia ─┼─► BASE DE DATOS
                                         │           └─► ConexionesClientes                           │ │                │
                                         └────────────────────────────────────────────────────────────┘ └────────────────┘
┌──────────────────────────── Transversales ─────────────────────────────┐
│   DTO-Records          UtileriasVarias          Fachada                 │
└─────────────────────────────────────────────────────────────────────────┘
```

Cada flecha del original es una dependencia de compilación permitida. El diagrama detallado solo agrega clases **dentro** de cada contenedor, más un subpaquete `Cache` dentro de `AlmacenarInformacion`, un subpaquete `tcp` dentro de `ProtocoloComunicacion`, dos subcomponentes dentro de `Servicios` y los recursos externos (`./almacen`, Redis, `logs/servidor.log`, `config/usuarios.txt`).

La única relación extra que se dibuja es `ServidorApp ⇢ FachadaServidor «crea e inyecta»`. Es una relación de **construcción**, no de uso: el Composition Root necesita conocer todo para ensamblarlo.

## 3. Contenedor → módulo Maven → paquete Java

Se respetan los módulos que ya existen en `pom.xml`. Base de paquetes: `com.universidad.mensajeria`.

| Contenedor del diagrama | Módulo Maven | Paquete Java |
|---|---|---|
| Transversal `DTO-Records` | `transversales/dto-records` | `…dto` |
| Transversal `UtileriasVarias` | `transversales/utilerias-varias` | `…utilerias` |
| Transversal `Fachada` | `transversales/fachada` | `…fachada` |
| «InterfazProtocoloComunicacion» | `interfaces/interfaz-protocolo-comunicacion` | `…componentes.protocolo` |
| Componente `ProtocoloComunicacion` | `componentes/protocolo-comunicacion` | `…componentes.protocolo` y `…componentes.protocolo.tcp` |
| «InterfazClienteServidor» | `interfaces/interfaz-cliente-servidor` | `…componentes.canal` |
| Componente `ProtocoloClienteServidor` | `componentes/protocolo-cliente-servidor` | `…componentes.canal` |
| «InterfazServiciosDisponibles» | `interfaces/interfaz-servicios-disponibles` | `…componentes.servicios` |
| Componente `Servicios` | `componentes/servicios` | `…componentes.servicios`, `…componentes.servicios.texto`, `…componentes.servicios.imagen` |
| «InterfazUsuariosDisponibles» | `interfaces/interfaz-usuarios-disponibles` | `…componentes.usuarios` |
| Componente `Usuarios` | `componentes/usuarios` | `…componentes.usuarios` |
| «InterfazGestorMensajes» | `interfaces/interfaz-gestor-mensajes` | `…componentes.gestor` |
| Componente `GestorColasDeMensajes` | `componentes/gestor-colas-de-mensajes` | `…componentes.gestor` |
| Paquete `Mensajes` | `paquetes/mensajes` | `…soporte.mensajes` |
| Paquete `LogDeEventos` | `paquetes/log-de-eventos` | `…soporte.log` |
| Paquete `ConexionesClientes` | `paquetes/conexiones-clientes` | `…soporte.conexiones` |
| Paquete `AlmacenarInformacion` (+ `Cache`) | `paquetes/almacenar-informacion` | `…soporte.almacenar` y `…soporte.almacenar.cache` |
| Paquete `Persistencia` | `paquetes/persistencia` | `…persistencia` y `…persistencia.jdbc` |
| Paquete `FachadaDeServicios` | `paquetes/fachada-de-servicios` | `…fachada.servicios` y `…fachada.servicios.comandos` |
| `VistaConsola` | `presentacion/vista-consola` | `…presentacion.consola` |
| `VistaEscritorio` | `presentacion/vista-escritorio` | `…presentacion.escritorio` |
| `InicioServidor` | `presentacion/inicio-servidor` | `…inicio` |

Dónde vive cada interfaz «callback»: `IReceptorConexiones` va en `interfaz-protocolo-comunicacion`, `IConsumidorMensajes` en `interfaz-gestor-mensajes` y `PeticionInvalidaException` en `interfaz-cliente-servidor`. Las tres son parte del contrato de su componente.

## 4. Dependencias permitidas (dependencias Maven de ámbito `compile`)

| Módulo | Puede depender de | No puede depender de |
|---|---|---|
| `dto-records` | `jackson-databind` (solo por `JsonNode`) | ningún módulo del proyecto |
| `utilerias-varias` | `dto-records`, `jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api` | componentes, paquetes |
| `fachada` (transversal) | `dto-records` | todo lo demás |
| `interfaz-*` (las 5) | `dto-records`; `interfaz-protocolo-comunicacion` además depende de `fachada` (por `ICanalCliente`) | componentes, paquetes |
| `protocolo-comunicacion` | su interfaz, `fachada`, `dto-records`, `utilerias-varias`, `slf4j-api` | otros componentes, paquetes, Jackson |
| `protocolo-cliente-servidor` | su interfaz, `dto-records`, `utilerias-varias`, `slf4j-api` | sockets, otros componentes, paquetes |
| `servicios` | su interfaz, `dto-records`, `utilerias-varias`, `slf4j-api` | otros componentes, paquetes |
| `usuarios` | su interfaz, `dto-records`, `utilerias-varias` | **`persistencia`, `almacenar-informacion`**, otros componentes |
| `gestor-colas-de-mensajes` | su interfaz, `dto-records`, `slf4j-api` | `mensajes` y otros paquetes, otros componentes |
| `mensajes` | `almacenar-informacion`, `dto-records`, `utilerias-varias` | componentes, `persistencia` |
| `log-de-eventos` | `almacenar-informacion`, `dto-records`, `utilerias-varias`, `slf4j-api` | componentes, `persistencia` |
| `conexiones-clientes` | `fachada`, `dto-records` | **`persistencia`, `almacenar-informacion`**, componentes |
| `almacenar-informacion` | `persistencia`, `dto-records`, `utilerias-varias`, `caffeine`, `jedis`, `slf4j-api` | `mensajes`, `log-de-eventos`, componentes |
| `persistencia` | `dto-records`, `utilerias-varias`, `HikariCP`, `flyway-core` (+ `flyway-mysql`, `flyway-database-postgresql`), `slf4j-api`; drivers `mysql-connector-j` y `postgresql` en ámbito `runtime` | todo lo de arriba |
| `fachada-de-servicios` | `fachada`, las 5 `interfaz-*`, `mensajes`, `log-de-eventos`, `conexiones-clientes`, `dto-records`, `utilerias-varias`, `slf4j-api` | **componentes concretos**, `almacenar-informacion`, `persistencia` |
| `vista-consola`, `vista-escritorio` | `fachada`, `dto-records`, `utilerias-varias` | todo lo demás (la flecha Vista → FachadaDeServicios del original se materializa con el contrato `IFachadaServidor`) |
| `inicio-servidor` | todos (es el Composition Root) + `logback-classic` (`runtime`) | — |

Reglas que se comprueban con `grep` en la revisión:

- `java.net.*` (Socket, ServerSocket) solo aparece en `protocolo-comunicacion`.
- `java.sql.*` solo aparece en `persistencia`.
- `ObjectMapper` / `JsonNode` del protocolo solo se usan en `protocolo-cliente-servidor`, `utilerias-varias` y `dto-records`.
- Instanciar (`new`) clases de otro módulo solo ocurre en `inicio-servidor`. Las excepciones son los records y enums de `dto-records` y las excepciones de las interfaces.

## 5. Responsabilidad de cada contenedor

| Contenedor | Responsabilidad | No hace |
|---|---|---|
| `InicioServidor` | Carga la configuración, ensambla el sistema con fábricas, elige la vista y registra el apagado ordenado | lógica de negocio |
| `VistaConsola` / `VistaEscritorio` | Interfaz del administrador: monitor, chat ADMIN, informes y cierre de sesiones | acceder a componentes o paquetes |
| `FachadaDeServicios` | Casos de uso: login, envío, enrutamiento, informes, cierre de sesiones; despacha los comandos del protocolo (patrón Command) | sockets, JSON, SQL |
| `ProtocoloComunicacion` | Transporte: aceptar conexiones, leer y escribir tramas, crear un `ICanalCliente` por conexión | interpretar JSON, reglas de negocio |
| `ProtocoloClienteServidor` | JSON ↔ DTO, validación de peticiones, armado de respuestas y eventos | sockets, casos de uso |
| `Servicios` | Procesar texto (subcomponente `ProcesamientoTexto`) e imágenes (subcomponente `FiltrosImagen`) | guardar archivos o filas |
| `Usuarios` | Catálogo de usuarios en memoria y autenticación | leer la BD o archivos |
| `GestorColasDeMensajes` | Cola productor/consumidor de mensajes entrantes | conocer `Mensaje` o la red |
| `Mensajes` | Entidades `Mensaje` y sus **estructuras de datos**; write-through a `AlmacenarInformacion` | SQL directo |
| `LogDeEventos` | Registrar eventos en SLF4J, indexarlos en memoria y persistirlos | SQL directo |
| `ConexionesClientes` | Sesiones activas y cupo global (en memoria) | persistir |
| `AlmacenarInformacion` | Única puerta a la persistencia: archivos en `./almacen`, filas en BD, caché, carga inicial de usuarios, cierre de conexiones huérfanas | lógica de negocio |
| `Persistencia` | JDBC portable, pool, migraciones Flyway, repositorios y directorio de archivos | conocer el dominio |

## 6. Flujos (en el orden exacto en que ocurren)

### 6.1 Arranque (`ServidorApp.main`)

1. `cfg = ConfiguracionServidor.cargar(Path.of("config/servidor.properties"), Path.of("config/bd.properties"))`. Si falta una clave, el arranque falla con un mensaje claro.
2. `hash = new GeneradorHashSHA256()`, `json = new SerializadorJSON()`.
3. `proveedor = new ProveedorConexionJDBC(cfg.bd())`, luego `proveedor.migrar()` (Flyway, carpeta según `bd.motor`).
4. `repos = new FabricaRepositoriosJDBC(proveedor, dialecto)`, con el dialecto elegido por `bd.motor`.
5. `caches = new FabricaCaches(cfg.cache(), json)`.
6. `almacen = new AlmacenarInformacion(repos, repos.archivos(), new ImportadorUsuarios(cfg.archivoUsuarios(), hash), caches…)`, luego `almacen.inicializar()`:
   - si la tabla `usuarios` está vacía, importa `config/usuarios.txt`;
   - cierra las conexiones huérfanas (`fecha_desconexion IS NULL`) con la hora actual.
7. `usuarios = FabricaUsuarios.crear(almacen.usuariosRegistrados(), hash)`. **Aquí** se entrega la lista, así `Usuarios` no conoce la persistencia.
8. `servicios = FabricaServicios.crear(hash)`.
9. `mensajes = new AlmacenMensajesMemoria(almacen)`, luego `mensajes.cargarHistorial()`.
10. `log = new LogEventos(almacen)`, luego `log.cargarHistorial()`.
11. `conexiones = new GestorConexiones(cfg.maxSesiones())`.
12. `gestor = new GestorColasDeMensajes(cfg.colaCapacidad())`.
13. `clienteServidor = FabricaClienteServidor.crear(json, cfg)`.
14. `protocolo = FabricaProtocolos.crear(TipoProtocolo.valueOf(cfg.protocolo()), cfg)`. `ConfiguracionServidor.protocolo()` devuelve `String` porque `utilerias-varias` no puede depender de un componente.
15. `fachada = new FachadaServidor(protocolo, clienteServidor, servicios, usuarios, gestor, mensajes, new FabricaMensajes(), log, conexiones, json, cfg)`. Por dentro crea `NotificadorObservadores`, `EnrutadorMensajes`, `ControlSesiones`, `GeneradorInformes`, `ProcesadorMensajes`, `FabricaManejadoresComando`, `DespachadorComandos` y `ReceptorConexiones`, todas del mismo paquete.
16. `vista = FabricaVistas.crear(TipoVista.valueOf(cfg.vista()), fachada)`.
17. `fachada.iniciarServidor()`: llama a `gestor.iniciar(cfg.hilosConsumidores(), procesador)` y luego a `protocolo.iniciar(cfg.puerto(), receptor)`.
18. `registrarApagado(fachada, vista)` y después `vista.iniciar()`. En la vista de consola, `iniciar()` contiene el bucle del menú.

**Apagado ordenado** (hook o menú «0»): se envía el push `SESION_CERRADA {motivo:"SERVIDOR_DETENIDO"}` a todos, `protocolo.detener()`, se cierran todas las sesiones (registro `DESCONEXION`), `gestor.detener()` (vacía la cola con un plazo de 5 s), se cierra el pool de BD y Redis.

### 6.2 Conexión y LOGIN

1. `ServidorTCP` acepta el socket, crea `CanalTCP` (id = UUID) y lanza un `ManejadorCliente` en un hilo virtual.
2. `receptor.alConectar(canal)`: registra `CONEXION` (IP, sin usuario) y envía el push `INFO_SERVIDOR {ipServidor, puerto, protocolo, cuposLibres}`. La conexión TCP sola **no** ocupa cupo.
3. El cliente envía `LOGIN {codigo, clave}`. `ManejadorCliente` lee la trama y llama a `receptor.alRecibir(canal, json)`.
4. `peticion = clienteServidor.interpretar(json)`. Si lanza `PeticionInvalidaException`, se responde `PETICION_INVALIDA` y el canal sigue abierto.
5. `despachador.despachar(peticion, canal)` → `ManejadorLogin` → `controlSesiones.abrir(canal, codigo, clave)`:
   - Si las credenciales no son válidas: evento `LOGIN_FALLIDO` y `ExcepcionNegocio(CREDENCIALES_INVALIDAS)`.
   - Si el canal ya tiene sesión: `ExcepcionNegocio(PETICION_INVALIDA)`.
   - Si `conexiones.abrirSesion(canal, usuario)` devuelve `false`: evento `CUPO_LLENO` y `ExcepcionNegocio(CUPO_LLENO)`. El receptor envía la respuesta y **después** cierra el canal.
   - Si todo sale bien: `log.conexion(sesion)` (evento `LOGIN_OK` + fila en `conexiones_usuario`), push `USUARIO_CONECTADO` a todos y `notificador.sesiones(conexiones.resumen())`.
6. Respuesta: `exito`, con `datos = {usuario: UsuarioDTO, idSesion, ipServidor}`. Se cumple siempre que `idSesion == canal.getId()`.

### 6.3 Mensaje al FORO o PRIVADO (texto o imagen)

1. El cliente envía `ENVIAR_FORO` o `ENVIAR_PRIVADO {destino}` con su `contenido`.
2. `DespachadorComandos` exige sesión; si no la hay, responde `NO_AUTENTICADO`.
3. El manejador arma un `SolicitudMensajeDTO`:
   - `idMensaje` = UUID nuevo;
   - el **remitente siempre sale de la sesión**, nunca del JSON;
   - en PRIVADO, `destino` debe existir (`usuarios.existe`) y ser distinto del remitente. Si no, `DESTINO_INEXISTENTE`.
4. `gestor.encolar(solicitud)`. Si devuelve `false`, responde `COLA_LLENA`. Si no, responde `exito {idMensaje, estado:"ENCOLADO"}` de inmediato.
5. Un hilo consumidor ejecuta `ProcesadorMensajes.procesar(s)`:
   1. TEXTO: `r = servicios.procesarTexto(texto)` y `m = fabricaMensajes.deTexto(s, r)`.
      IMAGEN: `bytes = Base64.decode(datosBase64)`, `r = servicios.procesarImagen(bytes, nombre)` (ORIGINAL + 5 filtros en cadena) y `m = fabricaMensajes.deImagen(s, r)`.
   2. `dto = almacenMensajes.agregar(m)`. Primero `AlmacenarInformacion.guardarMensaje` escribe los archivos y luego las filas en **una** transacción, y devuelve el `MensajeDTO` con las rutas. Después se indexa en las estructuras de datos.
   3. `log.registrar(MENSAJE_FORO | MENSAJE_PRIVADO, descripción, remitente, ip)`. La consola muestra la línea (sección 10).
   4. `enrutador.entregar(dto)`, que envía el push `MENSAJE_NUEVO {mensaje}` a:
      - FORO → todos los canales con sesión;
      - PRIVADO → los canales del destinatario y del remitente.
      Siempre llama a `notificador.mensaje(dto)` para que las vistas lo vean (si el destinatario es el ADMIN, es su bandeja).
   5. Si algo falla, se registra un evento `ERROR` con el `idMensaje` y no se envía push. El remitente no ve su mensaje aparecer.

### 6.4 El ADMIN escribe

`IFachadaServidor.publicarEnForo(c)` o `enviarPrivado(destino, c)` arman el mismo `SolicitudMensajeDTO`, con `remitente = usuarios.administrador().codigo()`, `idSesion = null` e `idPeticion = null`, y lo encolan. A partir de ahí el flujo es el mismo de 6.3.

### 6.5 HISTORIAL, OBTENER_IMAGEN, LISTAR_USUARIOS e INFORME

- `HISTORIAL {conversacion: "FORO" | <codigo>, limite?}`: `almacenMensajes.foro()` o `conversacion(yo, otro)`. Devuelve los últimos `limite` mensajes (200 por defecto), ordenados por fecha.
- `OBTENER_IMAGEN {idMensaje, etapa}`: solo si el mensaje es de FORO o si el usuario es su remitente o destinatario. Lee `almacenMensajes.imagen(id, etapa)`, que pasa por la caché de `AlmacenarInformacion`. Devuelve `{idMensaje, etapa, nombreArchivo, datosBase64}`. Si no existe, `NO_ENCONTRADO`.
- `LISTAR_USUARIOS`: `List<EstadoUsuarioDTO>` con **todos** los registrados (todos son visibles), con `conectado` y `sesiones`. El ADMIN aparece siempre con `conectado = true`.
- `INFORME {tipo, tipoEvento?, usuario?, desde?, hasta?}`: `GeneradorInformes.generar`:

| TipoInforme | `filas` | Fuente |
|---|---|---|
| `USUARIOS_REGISTRADOS` | `List<UsuarioDTO>` (nunca la contraseña) | `IUsuariosDisponibles` |
| `USUARIOS_CONECTADOS` | `List<ConexionUsuarioDTO>` (`cantidadSesiones` = cantidad de veces conectado ahora) | `IGestorConexiones` |
| `MENSAJES` | `List<MensajeDTO>` con todo el detalle | `IAlmacenMensajes` |
| `LOGS` | `List<EventoLogDTO>` filtrada con `FiltroLogs` | `ILogEventos` |

### 6.6 Fin de sesión

- `LOGOUT`: `controlSesiones.cerrar(idSesion, "LOGOUT")`, se responde `exito` y se cierra el canal.
- `receptor.alDesconectar(canal)`: si el canal tenía sesión, `controlSesiones.cerrar(id, "DESCONEXION")`.
- El administrador cierra una sesión (`IFachadaServidor.cerrarSesion`): push `SESION_CERRADA {motivo:"CERRADA_POR_ADMIN"}` a ese canal, luego `cerrar(id, …)` y `canal.cerrar()`.
- `ControlSesiones.cerrar` hace, en este orden: `conexiones.cerrarSesion(id)`, que libera el cupo **una sola vez** (si devuelve vacío, no hace nada más); `log.desconexion(sesion, motivo)` (evento `DESCONEXION` + `fecha_desconexion`); push `USUARIO_DESCONECTADO {codigo, sesiones}` a todos; `notificador.sesiones(…)`.

## 7. Contrato del protocolo (independiente del lenguaje)

**Trama TCP:** `[int32 big-endian = N][N bytes de JSON en UTF-8]`, con `1 ≤ N ≤ servidor.maxTramaBytes`. Se lee con `readFully`. Si una trama tiene una longitud inválida, se cierra el canal.

**JSON:** las fechas van en ISO-8601 sin zona (`2026-10-13T10:42:05.118`), los enums por nombre y los campos nulos se omiten.

```json
// Petición (cliente → servidor)
{"idPeticion":"7f1c…","comando":"ENVIAR_PRIVADO","parametros":{"destino":"20201002"},
 "contenido":{"tipo":"TEXTO","texto":"Hola"}}
// Respuesta (servidor → cliente): siempre trae idPeticion
{"idPeticion":"7f1c…","exito":true,"codigo":"OK","mensaje":"Encolado","datos":{"idMensaje":"…","estado":"ENCOLADO"}}
// Evento push (servidor → cliente): siempre trae evento y NO trae idPeticion
{"evento":"MENSAJE_NUEVO","fecha":"2026-10-13T10:42:05.118","datos":{"mensaje":{…MensajeDTO…}}}
```

| Comando | Sesión | `parametros` | `contenido` | `datos` de la respuesta | Errores posibles |
|---|---|---|---|---|---|
| `LOGIN` | no | `codigo`, `clave` | — | `usuario`, `idSesion`, `ipServidor` | `CREDENCIALES_INVALIDAS`, `CUPO_LLENO`, `PETICION_INVALIDA` |
| `LOGOUT` | sí | — | — | — | `NO_AUTENTICADO` |
| `LISTAR_USUARIOS` | sí | — | — | `usuarios: EstadoUsuarioDTO[]` | `NO_AUTENTICADO` |
| `ENVIAR_FORO` | sí | — | obligatorio | `idMensaje`, `estado` | `CONTENIDO_INVALIDO`, `COLA_LLENA` |
| `ENVIAR_PRIVADO` | sí | `destino` | obligatorio | `idMensaje`, `estado` | `DESTINO_INEXISTENTE`, `CONTENIDO_INVALIDO`, `COLA_LLENA` |
| `HISTORIAL` | sí | `conversacion`, `limite?` | — | `mensajes: MensajeDTO[]` | `DESTINO_INEXISTENTE` |
| `OBTENER_IMAGEN` | sí | `idMensaje`, `etapa` | — | `idMensaje`, `etapa`, `nombreArchivo`, `datosBase64` | `NO_ENCONTRADO` |
| `INFORME` | sí | `tipo`, `tipoEvento?`, `usuario?`, `desde?`, `hasta?` | — | `InformeDTO` | `PETICION_INVALIDA` |

Todos los comandos pueden responder además `PETICION_INVALIDA` (JSON mal formado o parámetros faltantes) y `ERROR_INTERNO`.

**Reglas de `ContenidoDTO`:**

- `TEXTO`: `texto` sin estar en blanco y con un máximo de `mensajes.maxTextoCaracteres` caracteres; `nombreArchivo` y `datosBase64` en `null`.
- `IMAGEN`: `nombreArchivo` con extensión png, jpg, jpeg, gif o bmp; `datosBase64` decodificado de un máximo de `mensajes.maxImagenBytes` bytes y legible por `ImageIO`.

| Evento push | Cuándo | `datos` |
|---|---|---|
| `INFO_SERVIDOR` | al aceptar la conexión | `ipServidor`, `puerto`, `protocolo`, `cuposLibres` |
| `MENSAJE_NUEVO` | después de procesar un mensaje | `mensaje: MensajeDTO` |
| `USUARIO_CONECTADO` | login correcto | `codigo`, `nombreCompleto`, `sesiones` |
| `USUARIO_DESCONECTADO` | fin de una sesión | `codigo`, `sesiones` (las que le quedan) |
| `SESION_CERRADA` | el ADMIN cierra la sesión o el servidor se detiene | `motivo` |
| `CUPO_LLENO` | reservado. No se envía en v1: el rechazo viaja como respuesta con `codigo = CUPO_LLENO` | — |

Los enums `TipoComando`, `TipoEventoPush` y `CodigoError` viajan por nombre: **no se renombran ni se reordenan valores existentes**; los nuevos se agregan al final.

## 8. Concurrencia

- `ServidorTCP`: un hilo acepta conexiones. Cada conexión tiene un **hilo virtual lector** (`ManejadorCliente`) creado con `Executors.newVirtualThreadPerTaskExecutor()`.
- `CanalTCP`: cola de salida **acotada** (`sesion.colaSalida`) y **un único hilo escritor** por canal. `enviar()` solo encola (`offer`); si la cola está llena, se cierra el canal (cliente lento). Nada escribe en el socket fuera de ese hilo. Para I/O se usa `ReentrantLock`, no `synchronized`.
- `GestorColasDeMensajes`: `LinkedBlockingQueue` con capacidad `cola.capacidad` y `cola.hilosConsumidores` **hilos de plataforma** (procesar imágenes consume CPU). `encolar` usa `offer` y nunca bloquea el hilo de red.
- `GestorConexiones`: `new Semaphore(maxSesiones, true)`. `abrirSesion` usa `tryAcquire()`. `cerrarSesion` hace `release()` solo si la sesión se quitó del mapa (es idempotente).
- Estructuras compartidas: `ConcurrentHashMap`, `ConcurrentLinkedDeque` y `ConcurrentLinkedQueue`. Las listas por conversación son listas sincronizadas y las lecturas devuelven copias inmutables.
- Los observadores se notifican desde hilos de fondo. Las vistas pasan al hilo de UI (`SwingUtilities.invokeLater`) o imprimen con un bloqueo de consola.
- Al capturar `InterruptedException` siempre se restaura la interrupción.

## 9. Configuración

`config/servidor.properties`:

| Clave | Valor por defecto | Uso |
|---|---|---|
| `servidor.protocolo` | `TCP` | `FabricaProtocolos` |
| `servidor.puerto` | `5000` | puerto de escucha |
| `servidor.maxSesiones` | `10` | cupo global (N) |
| `servidor.maxTramaBytes` | `16777216` | tamaño máximo de una trama |
| `servidor.vista` | `CONSOLA` | `CONSOLA` o `ESCRITORIO` |
| `sesion.colaSalida` | `256` | mensajes pendientes por canal |
| `cola.capacidad` | `1000` | cola de mensajes entrantes |
| `cola.hilosConsumidores` | `4` | consumidores |
| `mensajes.maxTextoCaracteres` | `4000` | validación |
| `mensajes.maxImagenBytes` | `5242880` | validación (5 MB) |
| `archivos.directorio` | `./almacen` | raíz de los archivos |
| `usuarios.archivoInicial` | `./config/usuarios.txt` | carga inicial |
| `cache.l1.max` / `cache.l1.ttlMinutos` | `5000` / `10` | Caffeine |
| `cache.l2.habilitada` | `false` | `true` usa Redis |
| `redis.host` / `redis.puerto` / `redis.ttlMinutos` | `localhost` / `6379` / `60` | Redis |

`config/bd.properties`: `bd.motor` (`MYSQL` o `POSTGRESQL`), `bd.url`, `bd.usuario`, `bd.clave` y `bd.pool` (10).

`config/usuarios.txt` (UTF-8, separador `;`, la primera línea es el encabezado): `codigo;nombres;apellidos;contrasena;programa;rol`. Debe haber **exactamente un** usuario con rol `ADMIN`.

`ConfiguracionServidor` expone un getter por cada clave. En el diagrama solo se muestran los principales.

## 10. Logs y lo que se ve en la consola

- `logback.xml` (en `inicio-servidor`) tiene un `ConsoleAppender` con `%highlight` y un `RollingFileAppender` diario en `logs/servidor.log` que guarda 30 días. El patrón es `%d{HH:mm:ss.SSS} %highlight(%-5level) [%thread] %X{usuario} %X{ip} - %msg%n`.
- `LogEventos.registrar` escribe una línea por evento y la persiste (`logs_eventos`). Formato de la descripción de un mensaje:
  - texto: `MENSAJE_FORO id=<uuid> de=<codigo> tipo=TEXTO hash=<sha256> caracteres=<n> palabras=<m>`
  - imagen: `MENSAJE_PRIVADO id=<uuid> de=<codigo> para=<codigo> tipo=IMAGEN archivo=<nombre> hash=<sha256> filtros=5`
- En los logs **no** se escriben contraseñas ni el contenido completo de los mensajes. El contenido solo lo muestra `VistaConsola.onMensaje`, que imprime remitente, canal, destino, tipo, texto o nombre de archivo, hash y conteos.

## 11. Directorio `./almacen`

| Qué | Ruta |
|---|---|
| Texto enviado | `./almacen/textos/{idMensaje}.txt` |
| Imagen original | `./almacen/originales/{idMensaje}_{nombreArchivo}` |
| Imágenes filtradas | `./almacen/filtradas/{idMensaje}_{orden}_{ETAPA}.png` (orden 1–5) |

La BD guarda en `imagenes_mensaje` la ruta, el tamaño y el hash de **las 6** versiones (la ORIGINAL incluida). La escritura de archivos ocurre antes de la transacción. Si la transacción falla, los archivos escritos se borran.

## 12. Cambiar de protocolo (UDP, gRPC…) sin tocar nada fuera del componente

1. En `componentes/protocolo-comunicacion`, crear el subpaquete del protocolo (`udp` o `grpc`) con:
   - `ServidorXxx implements IProtocoloComunicacion`;
   - `CanalXxx implements ICanalCliente` (un canal por cliente, con su cola y su único escritor).
2. Agregar el valor en `TipoProtocolo` y el `case` correspondiente en `FabricaProtocolos`.
3. Cambiar `servidor.protocolo` en `servidor.properties`.
4. El payload sigue siendo el **mismo JSON** (`ProtocoloClienteServidor` no cambia):
   - UDP: un datagrama por mensaje, más fragmentación y confirmación si supera el tamaño seguro; la sesión se identifica por la dirección remota y se crea solo al recibir `LOGIN`, con un TTL por inactividad.
   - gRPC: un *stream* bidireccional con un campo `bytes json`. Las clases generadas por Protobuf no salen del módulo.
5. Ejecutar la misma batería de pruebas de contrato de transporte (conectar, LOGIN, enviar, recibir push, desconectar) sobre la nueva implementación.

No cambian: las interfaces, `FachadaDeServicios`, los demás componentes, los paquetes ni el cliente (salvo su propio componente `ProtocoloComunicacion` si se quiere el mismo protocolo).

## 13. Catálogo de clases (generado desde el diagrama)

Notación: `+` público, `-` privado, `#` protegido, `«…»` estereotipo o nota. En los records, cada línea es un componente del record. En los enums, cada línea es un valor, en orden.

### Capa de Presentación

#### InicioServidor  ·  _paquete_
Arranque del servidor y raíz de composición.
- **`ServidorApp`** — «Composition Root» clase. Único punto que conoce clases concretas: carga la configuración, crea cada módulo con su fábrica, inyecta por constructor y entrega IFachadaServidor a la vista elegida.
  ```text
  + main(args : String[]) : void
  - ensamblar(cfg : ConfiguracionServidor) : IFachadaServidor
  - registrarApagado(f : IFachadaServidor, v : IVista) : void
  ```
- **`FabricaVistas`** — «Factory». Crea VistaConsola o VentanaPrincipal según servidor.vista y las envuelve en un adaptador `IVista` (las vistas no pueden depender de `inicio-servidor`, así que no implementan `IVista` directamente; tienen los métodos `iniciar()` y `detener()`).
  ```text
  + crear(t : TipoVista, f : IFachadaServidor) : IVista
  ```
- **`IVista`** — «interface». Contrato común de las vistas del servidor.
  ```text
  + iniciar() : void
  + detener() : void
  ```
- **`TipoVista`** — «enum».
  ```text
  CONSOLA
  ESCRITORIO
  ```

#### VistaConsola  ·  _paquete_
Vista de consola del servidor.
- **`VistaConsola`** — «Vista · IObservadorServidor» clase. Interfaz de texto. Imprime la información de cada mensaje que llega y atiende el menú del administrador.
  ```text
  - fachada : IFachadaServidor
  - menu : MenuConsola
  - chat : ChatAdminConsola
  + iniciar() : void
  + detener() : void
  + onMensaje(m : MensajeDTO) : void
  + onEvento(e : EventoLogDTO) : void
  + onSesiones(l : List<SesionDTO>) : void
  ```
- **`MenuConsola`** — clase. Menú 0–7 de la consola.
  ```text
  + mostrarOpciones() : void
  + leerOpcion() : int
  + imprimirInforme(json : String) : void
  + imprimirSesiones(l : List<SesionDTO>) : void
  ```
- **`ChatAdminConsola`** — «ADMIN» clase. El administrador escribe en el foro o por privado desde la consola.
  ```text
  + publicarEnForo(texto : String) : void
  + enviarPrivado(codigo : String, texto : String) : void
  ```

#### VistaEscritorio  ·  _paquete_
Vista de escritorio (Swing) del servidor.
- **`VentanaPrincipal`** — «JFrame · IObservadorServidor» clase. Ventana Swing con tres pestañas: Monitor, Chat ADMIN e Informes. Toda actualización de UI va por SwingUtilities.invokeLater.
  ```text
  - fachada : IFachadaServidor
  + iniciar() : void
  + detener() : void
  + onMensaje(m : MensajeDTO) : void
  + onEvento(e : EventoLogDTO) : void
  + onSesiones(l : List<SesionDTO>) : void
  ```
- **`PanelMonitor`** — «JPanel» clase. Sesiones activas, cupo ocupado y mensajes entrantes en vivo.
  ```text
  + refrescarSesiones(l : List<SesionDTO>) : void
  + agregarMensaje(m : MensajeDTO) : void
  + cerrarSesion(idSesion : String) : void
  ```
- **`PanelChatAdmin`** — «JPanel · ADMIN» clase. Chat del administrador: foro y privados, texto e imagen.
  ```text
  + publicarEnForo(c : ContenidoDTO) : void
  + enviarPrivado(codigo : String, c : ContenidoDTO) : void
  + mostrarMensaje(m : MensajeDTO) : void
  ```
- **`PanelInformes`** — «JPanel» clase. Los cuatro informes en tablas y su JSON.
  ```text
  + mostrar(t : TipoInforme, json : String) : void
  ```

### Capa de Servicios

#### FachadaDeServicios  ·  _paquete_
Orquestación de casos de uso. Depende solo de las 5 interfaces y de Mensajes, LogDeEventos y ConexionesClientes.
- **`FachadaServidor`** — «Facade · IFachadaServidor» clase principal del componente. Única puerta de entrada de las vistas. Orquesta componentes y paquetes; no contiene lógica de transporte ni SQL.
  ```text
  - protocolo : IProtocoloComunicacion
  - gestorMensajes : IGestorMensajes
  - usuarios : IUsuariosDisponibles
  - conexiones : IGestorConexiones
  - receptor : ReceptorConexiones
  - procesador : ProcesadorMensajes
  - sesiones : ControlSesiones
  - informes : GeneradorInformes
  - notificador : NotificadorObservadores
  + iniciarServidor() : void
  + detenerServidor() : void
  + publicarEnForo(c : ContenidoDTO) : void
  + enviarPrivado(destino : String, c : ContenidoDTO) : void
  + informe(t : TipoInforme, filtros : Map<String,String>) : String
  + listarSesiones() : List<SesionDTO>
  + listarUsuarios() : List<EstadoUsuarioDTO>
  + cerrarSesion(idSesion : String) : void
  + suscribir(o : IObservadorServidor) : void
  + getIpServidor() : String
  ```
- **`ReceptorConexiones`** — «IReceptorConexiones» clase. Recibe los eventos del transporte: interpreta el JSON con IClienteServidor y despacha el comando.
  ```text
  - clienteServidor : IClienteServidor
  - despachador : DespachadorComandos
  - sesiones : ControlSesiones
  + alConectar(c : ICanalCliente) : void
  + alRecibir(c : ICanalCliente, json : String) : void
  + alDesconectar(c : ICanalCliente) : void
  ```
- **`ControlSesiones`** — clase. Login (autenticación + cupo global) y cierre de sesiones; registra CONEXION/DESCONEXION y avisa a todos.
  ```text
  - usuarios : IUsuariosDisponibles
  - conexiones : IGestorConexiones
  - log : ILogEventos
  - enrutador : EnrutadorMensajes
  + abrir(c : ICanalCliente, codigo : String, clave : String) : UsuarioDTO
  + cerrar(idSesion : String, motivo : String) : void
  ```
- **`NotificadorObservadores`** — «Observer · sujeto» clase. Avisa a las vistas del servidor (consola / escritorio).
  ```text
  - observadores : CopyOnWriteArrayList<IObservadorServidor>
  + suscribir(o : IObservadorServidor) : void
  + mensaje(m : MensajeDTO) : void
  + evento(e : EventoLogDTO) : void
  + sesiones(l : List<SesionDTO>) : void
  ```
- **`ExcepcionNegocio`** — «RuntimeException» clase. Error esperado de un caso de uso; se traduce a RespuestaDTO con su código.
  ```text
  - codigo : CodigoError
  + getCodigo() : CodigoError
  ```
- **`DespachadorComandos`** — clase. Busca el manejador del comando; exige sesión iniciada para todo comando distinto de LOGIN.
  ```text
  - manejadores : EnumMap<TipoComando, IManejadorComando>
  - conexiones : IGestorConexiones
  + despachar(p : PeticionDTO, c : ICanalCliente) : RespuestaDTO
  ```
- **`IManejadorComando`** — «Command» interfaz. Un caso de uso por comando del protocolo.
  ```text
  + ejecutar(p : PeticionDTO, c : ICanalCliente) : RespuestaDTO
  ```
- **`FabricaManejadoresComando`** — «Factory». Registra un manejador por cada TipoComando.
  ```text
  + crearTodos() : EnumMap<TipoComando, IManejadorComando>
  ```
- **`ManejadorLogin`** — clase.
- **`ManejadorLogout`** — clase.
- **`ManejadorListarUsuarios`** — clase.
- **`ManejadorEnviarForo`** — clase.
- **`ManejadorEnviarPrivado`** — clase.
- **`ManejadorHistorial`** — clase.
- **`ManejadorObtenerImagen`** — clase.
- **`ManejadorInforme`** — clase.
- **`ProcesadorMensajes`** — «IConsumidorMensajes» clase. Lo ejecutan los hilos consumidores de la cola: procesa (hash, conteos, filtros), crea el Mensaje, lo guarda, lo registra y lo entrega.
  ```text
  - servicios : IServiciosDisponibles
  - fabrica : FabricaMensajes
  - almacen : IAlmacenMensajes
  - log : ILogEventos
  - enrutador : EnrutadorMensajes
  + procesar(s : SolicitudMensajeDTO) : void
  ```
- **`EnrutadorMensajes`** — clase. FORO → todas las sesiones; PRIVADO → sesiones del destinatario y del remitente; si interviene el ADMIN → vistas.
  ```text
  - conexiones : IGestorConexiones
  - clienteServidor : IClienteServidor
  - notificador : NotificadorObservadores
  + entregar(m : MensajeDTO) : void
  + difundir(e : EventoPushDTO) : void
  + enviarA(codigo : String, e : EventoPushDTO) : void
  ```
- **`GeneradorInformes`** — clase. Arma los 4 informes desde las estructuras de datos en memoria.
  ```text
  - usuarios : IUsuariosDisponibles
  - conexiones : IGestorConexiones
  - mensajes : IAlmacenMensajes
  - log : ILogEventos
  + generar(t : TipoInforme, filtros : Map<String,String>) : InformeDTO
  ```
- **`IProtocoloComunicacion`** — «InterfazProtocoloComunicacion» interfaz. Transporte independiente del protocolo concreto. Hoy: TCP.
  ```text
  + iniciar(puerto : int, r : IReceptorConexiones) : void
  + detener() : void
  + estaActivo() : boolean
  + getIpServidor() : String
  ```
- **`IReceptorConexiones`** — «callback» interfaz. Lo implementa la fachada; el transporte lo invoca. Forma parte del contrato InterfazProtocoloComunicacion.
  ```text
  + alConectar(c : ICanalCliente) : void
  + alRecibir(c : ICanalCliente, json : String) : void
  + alDesconectar(c : ICanalCliente) : void
  ```

#### ProtocoloComunicacion  ·  _componente_
Transporte. Cambiar de protocolo (UDP, gRPC…) solo toca este componente.
- **`FabricaProtocolos`** — «Factory». Punto de extensión: un nuevo protocolo = un nuevo case aquí + su subpaquete.
  ```text
  + crear(t : TipoProtocolo, cfg : ConfiguracionServidor) : IProtocoloComunicacion
  ```
- **`TipoProtocolo`** — «enum».
  ```text
  TCP
  ```

##### tcp  ·  _subpaquete_
- **`ServidorTCP`** — «IProtocoloComunicacion» clase principal del componente. Acepta sockets y crea un ManejadorCliente por conexión.
  ```text
  - serverSocket : ServerSocket
  - hilos : ExecutorService «virtuales»
  - maxTramaBytes : int
  + iniciar(puerto : int, r : IReceptorConexiones) : void
  + detener() : void
  - aceptarConexiones() : void
  ```
- **`ManejadorCliente`** — «Runnable · hilo lector» clase. Lee tramas en bucle y las entrega al receptor como String JSON; al terminar llama alDesconectar.
  ```text
  - canal : CanalTCP
  - receptor : IReceptorConexiones
  + run() : void
  ```
- **`CanalTCP`** — «ICanalCliente» clase. Un canal por socket. enviar() solo encola; un único hilo escritor escribe en el socket.
  ```text
  - socket : Socket
  - salida : BlockingQueue<String> «acotada»
  - escritor : Thread «único escritor»
  - tramas : CodificadorTramas
  + enviar(json : String) : void
  + cerrar() : void
  ```
- **`CodificadorTramas`** — «[int32 longitud][UTF-8]» clase. Framing con prefijo de longitud (big-endian) y readFully.
  ```text
  - maxTramaBytes : int
  + leer(in : DataInputStream) : byte[]
  + escribir(out : DataOutputStream, b : byte[]) : void
  ```
- **`IClienteServidor`** — «InterfazClienteServidor» interfaz. Contrato de la conversación cliente-servidor: JSON ↔ DTO y validación de peticiones.
  ```text
  + interpretar(json : String) : PeticionDTO
  + serializar(r : RespuestaDTO) : String
  + serializar(e : EventoPushDTO) : String
  + exito(idPeticion : String, datos : Object) : RespuestaDTO
  + error(idPeticion : String, c : CodigoError, detalle : String) : RespuestaDTO
  ```
- **`PeticionInvalidaException`** — «RuntimeException» clase. JSON mal formado o petición incompleta.
  ```text
  - idPeticion : String
  ```

#### ProtocoloClienteServidor  ·  _componente_
Formato de los mensajes (JSON) y validación. No conoce sockets ni casos de uso.
- **`ProtocoloClienteServidorJSON`** — «IClienteServidor» clase principal del componente. Implementación JSON (Jackson) del contrato.
  ```text
  - json : ISerializador
  - validador : ValidadorPeticiones
  + interpretar(json : String) : PeticionDTO
  + serializar(r : RespuestaDTO) : String
  + serializar(e : EventoPushDTO) : String
  ```
- **`ValidadorPeticiones`** — clase. Parámetros obligatorios por comando y límites de contenido.
  ```text
  - requeridos : EnumMap<TipoComando, Set<String>>
  - maxTextoCaracteres : int
  - maxImagenBytes : int
  + validar(p : PeticionDTO) : void
  ```
- **`FabricaClienteServidor`** — «Factory».
  ```text
  + crear(json : ISerializador, cfg : ConfiguracionServidor) : IClienteServidor
  ```
- **`IServiciosDisponibles`** — «InterfazServiciosDisponibles» interfaz. Procesamiento de los mensajes recibidos.
  ```text
  + procesarTexto(texto : String) : ResultadoTextoDTO
  + procesarImagen(datos : byte[], nombre : String) : ResultadoImagenDTO
  ```

#### Servicios  ·  _componente_
Procesamiento de mensajes, dividido en dos subcomponentes.
- **`ServiciosProcesamiento`** — «IServiciosDisponibles» clase principal del componente. Fachada interna del componente: solo delega en el subcomponente que corresponde.
  ```text
  - texto : IProcesadorTexto
  - imagen : IProcesadorImagen
  + procesarTexto(texto : String) : ResultadoTextoDTO
  + procesarImagen(datos : byte[], nombre : String) : ResultadoImagenDTO
  ```
- **`FabricaServicios`** — «Factory». Ensambla los dos subcomponentes.
  ```text
  + crear(hash : IGeneradorHash) : IServiciosDisponibles
  ```

##### ProcesamientoTexto  ·  _subcomponente_
Subcomponente de texto (una sola responsabilidad).
- **`IProcesadorTexto`** — «interface».
  ```text
  + procesar(texto : String) : ResultadoTextoDTO
  ```
- **`ProcesadorTexto`** — «IProcesadorTexto» clase. SHA-256 del texto UTF-8, número de caracteres y de palabras.
  ```text
  - hash : IGeneradorHash
  + procesar(texto : String) : ResultadoTextoDTO
  - contarCaracteres(t : String) : int
  - contarPalabras(t : String) : int
  ```

##### FiltrosImagen  ·  _subcomponente_
Subcomponente de imágenes: hash y 5 filtros secuenciales.
- **`IProcesadorImagen`** — «interface».
  ```text
  + procesar(datos : byte[], nombre : String) : ResultadoImagenDTO
  ```
- **`ProcesadorImagen`** — «IProcesadorImagen» clase. Hash de la original y ejecución del pipeline; devuelve ORIGINAL + 5 imágenes.
  ```text
  - hash : IGeneradorHash
  - pipeline : PipelineFiltros
  + procesar(datos : byte[], nombre : String) : ResultadoImagenDTO
  ```
- **`PipelineFiltros`** — «Pipes & Filters» clase. Ejecuta en orden; la salida de un filtro es la entrada del siguiente; cada paso produce una imagen nueva (PNG).
  ```text
  - filtros : List<IFiltroImagen>
  + agregar(f : IFiltroImagen) : PipelineFiltros
  + ejecutarSecuencial(img : BufferedImage) : List<ImagenProcesadaDTO>
  ```
- **`FabricaFiltros`** — «Factory». Orden fijo: ESCALA_GRISES → SEPIA → ROTAR_180 → BRILLO → REDUCCION_TAMANO.
  ```text
  + crearPipelinePorDefecto() : PipelineFiltros
  + crear(t : TipoFiltro) : IFiltroImagen
  ```
- **`IFiltroImagen`** — «Strategy» interfaz. Un filtro; nunca modifica la imagen de entrada.
  ```text
  + nombre() : String  «= TipoFiltro.name()»
  + aplicar(img : BufferedImage) : BufferedImage
  ```
- **`FiltroEscalaGrises`** — clase.
- **`FiltroSepia`** — clase.
- **`FiltroRotar180`** — clase.
- **`FiltroBrillo`** — clase.
  ```text
  - factor : float
  ```
- **`FiltroReduccionTamano`** — clase.
  ```text
  - escala : double
  ```
- **`IUsuariosDisponibles`** — «InterfazUsuariosDisponibles» interfaz. Catálogo de usuarios registrados (sin CRUD).
  ```text
  + autenticar(codigo : String, clave : String) : Optional<UsuarioDTO>
  + listarRegistrados() : List<UsuarioDTO>
  + buscar(codigo : String) : Optional<UsuarioDTO>
  + existe(codigo : String) : boolean
  + administrador() : UsuarioDTO
  ```

#### Usuarios  ·  _componente_
Usuarios registrados y autenticación.
- **`ServicioUsuarios`** — «IUsuariosDisponibles» clase principal del componente. Valida código + SHA-256(contraseña) contra el catálogo en memoria. No conoce la persistencia.
  ```text
  - catalogo : Map<String, UsuarioRegistroDTO> «inmutable»
  - hash : IGeneradorHash
  + autenticar(codigo : String, clave : String) : Optional<UsuarioDTO>
  ```
- **`FabricaUsuarios`** — «Factory». Recibe la lista ya cargada (la entrega el Composition Root).
  ```text
  + crear(registrados : List<UsuarioRegistroDTO>, h : IGeneradorHash) : IUsuariosDisponibles
  ```
- **`IGestorMensajes`** — «InterfazGestorMensajes» interfaz. Cola productor/consumidor de mensajes entrantes.
  ```text
  + iniciar(hilos : int, c : IConsumidorMensajes) : void
  + encolar(s : SolicitudMensajeDTO) : boolean
  + pendientes() : int
  + detener() : void
  ```
- **`IConsumidorMensajes`** — «callback» interfaz. Lo implementa la fachada (ProcesadorMensajes).
  ```text
  + procesar(s : SolicitudMensajeDTO) : void
  ```

#### GestorColasDeMensajes  ·  _componente_
Cola de mensajes entrantes.
- **`GestorColasDeMensajes`** — «IGestorMensajes · productor/consumidor» clase principal del componente. El hilo de red solo encola; N hilos consumidores procesan. No conoce Mensajes ni la red.
  ```text
  - cola : BlockingQueue<SolicitudMensajeDTO> «acotada»
  - consumidores : ExecutorService
  - consumidor : IConsumidorMensajes
  - activo : AtomicBoolean
  + encolar(s : SolicitudMensajeDTO) : boolean  «offer, no bloquea»
  - consumir() : void
  ```

#### Mensajes  ·  _paquete_
Dominio de mensajes y sus estructuras de datos.
- **`Mensaje`** — «abstract · entidad» clase. Entidad de dominio. claveConversacion = menor|mayor de los dos códigos.
  ```text
  # id : String «UUID»
  # canal : Canal
  # remitente : String
  # destinatario : String «null si FORO»
  # fecha : LocalDateTime
  # hash : String
  + tipo() : TipoMensaje  {abstract}
  + aDTO() : MensajeDTO
  + claveConversacion(a : String, b : String) : String  {static}
  ```
- **`MensajeTexto`** — clase.
  ```text
  - texto : String
  - numCaracteres : int
  - numPalabras : int
  ```
- **`MensajeImagen`** — clase. Guarda ORIGINAL + 5 versiones filtradas.
  ```text
  - nombreArchivo : String
  - versiones : List<ImagenProcesadaDTO>
  ```
- **`FabricaMensajes`** — «Factory».
  ```text
  + deTexto(s : SolicitudMensajeDTO, r : ResultadoTextoDTO) : Mensaje
  + deImagen(s : SolicitudMensajeDTO, r : ResultadoImagenDTO) : Mensaje
  ```
- **`IAlmacenMensajes`** — «interface». Consulta de mensajes siempre desde las estructuras de datos.
  ```text
  + agregar(m : Mensaje) : MensajeDTO
  + buscar(id : String) : Optional<MensajeDTO>
  + foro() : List<MensajeDTO>
  + conversacion(a : String, b : String) : List<MensajeDTO>
  + todos() : List<MensajeDTO>
  + imagen(id : String, etapa : TipoFiltro) : Optional<byte[]>
  + cargarHistorial() : void
  ```
- **`AlmacenMensajesMemoria`** — «Estructuras de datos · write-through» clase. Primero persiste (AlmacenarInformación) y después indexa en memoria.
  ```text
  - porId : ConcurrentHashMap<String, MensajeDTO>
  - porConversacion : ConcurrentHashMap<String, List<MensajeDTO>>
  - foro : ConcurrentLinkedDeque<MensajeDTO>
  - enOrden : ConcurrentLinkedQueue<MensajeDTO>
  - almacenamiento : IAlmacenarInformacion
  ```

#### LogDeEventos  ·  _paquete_
Logs de eventos y mensajes.
- **`ILogEventos`** — «interface». Gestor de logs de la aplicación.
  ```text
  + registrar(t : TipoEvento, descripcion : String, usuario : String, ip : String) : void
  + conexion(s : SesionDTO) : void
  + desconexion(s : SesionDTO, motivo : String) : void
  + consultar(f : FiltroLogs) : List<EventoLogDTO>
  + todos() : List<EventoLogDTO>
  + cargarHistorial() : void
  ```
- **`FiltroLogs`** — «record». Campos nulos = sin filtro.
  ```text
  tipo : TipoEvento
  usuario : String
  desde : LocalDateTime
  hasta : LocalDateTime
  + acepta(e : EventoLogDTO) : boolean
  ```
- **`LogEventos`** — «ILogEventos» clase. Escribe en SLF4J (consola + archivo), indexa en memoria y persiste en BD.
  ```text
  - logger : org.slf4j.Logger «Logback»
  - eventos : ConcurrentLinkedDeque<EventoLogDTO>
  - porTipo : ConcurrentHashMap<TipoEvento, Queue<EventoLogDTO>>
  - porUsuario : ConcurrentHashMap<String, Queue<EventoLogDTO>>
  - almacenamiento : IAlmacenarInformacion
  ```

#### ConexionesClientes  ·  _paquete_
Registro de sesiones y cupo global.
- **`IGestorConexiones`** — «interface». Sesiones activas en memoria. No persiste nada.
  ```text
  + abrirSesion(c : ICanalCliente, u : UsuarioDTO) : boolean  «false = cupo lleno»
  + cerrarSesion(idSesion : String) : Optional<SesionDTO>
  + usuarioDe(idSesion : String) : Optional<UsuarioDTO>
  + canalesDe(codigo : String) : List<ICanalCliente>
  + canalesActivos() : Collection<ICanalCliente>
  + resumen() : List<SesionDTO>
  + conteoPorUsuario() : List<ConexionUsuarioDTO>
  + cuposLibres() : int
  ```
- **`GestorConexiones`** — «IGestorConexiones» clase. abrirSesion = cupo.tryAcquire(); cerrarSesion = release() una sola vez.
  ```text
  - cupo : Semaphore «servidor.maxSesiones»
  - sesiones : ConcurrentHashMap<String, SesionActiva>
  - porUsuario : ConcurrentHashMap<String, Set<String>>
  ```
- **`SesionActiva`** — «record».
  ```text
  canal : ICanalCliente
  usuario : UsuarioDTO
  desde : LocalDateTime
  + aDTO() : SesionDTO
  ```

#### AlmacenarInformacion  ·  _paquete_
Coordina BD, directorio y caché. Es el único que habla con Persistencia.
- **`IAlmacenarInformacion`** — «interface». Única puerta hacia la persistencia.
  ```text
  + inicializar() : void
  + usuariosRegistrados() : List<UsuarioRegistroDTO>
  + guardarMensaje(m : MensajeDTO, imgs : List<ImagenProcesadaDTO>) : MensajeDTO
  + leerImagen(idMensaje : String, etapa : TipoFiltro) : Optional<byte[]>
  + registrarConexion(s : SesionDTO) : void
  + registrarDesconexion(idSesion : String, fin : LocalDateTime) : void
  + guardarLog(e : EventoLogDTO) : void
  + historialMensajes() : List<MensajeDTO>
  + historialLogs() : List<EventoLogDTO>
  ```
- **`AlmacenarInformacion`** — «IAlmacenarInformacion · write-through» clase. Guarda archivos en ./almacen y filas en BD; usa la caché en las lecturas.
  ```text
  - repos : FabricaRepositorios
  - archivos : IAlmacenamientoArchivos
  - importador : ImportadorUsuarios
  - cacheMensajes : ICache<String, MensajeDTO>
  - cacheImagenes : ICache<String, byte[]>
  ```
- **`ImportadorUsuarios`** — «carga inicial» clase. Lee el archivo plano y calcula SHA-256 de cada contraseña.
  ```text
  - archivo : Path «usuarios.txt»
  - hash : IGeneradorHash
  + leer() : List<UsuarioRegistroDTO>
  ```

##### Cache  ·  _subpaquete_
- **`ICache<K, V>`** — «interface».
  ```text
  + get(k : K) : Optional<V>
  + put(k : K, v : V) : void
  + invalidar(k : K) : void
  ```
- **`FabricaCaches`** — «Factory». Si cache.l2.habilitada=false devuelve solo L1.
  ```text
  + crear(nombre : String, tipo : Class<V>) : ICache<K, V>
  ```
- **`CacheDosNiveles`** — «Decorator · read-through» infraestructura.
  ```text
  - l1 : ICache<K, V>
  - l2 : ICache<K, V>
  + get(k) : L1 → L2 (promueve a L1)
  + put(k, v) : L1 y L2
  ```
- **`CacheL1Caffeine`** — «L1 · JVM» infraestructura.
  ```text
  - cache : Cache<K, V>
  ```
- **`CacheL2Redis`** — «L2 · Redis» infraestructura.
  ```text
  - pool : JedisPool
  - ttl : Duration
  ```

### Capa de Persistencia

#### Persistencia  ·  _paquete_
JDBC portable (MySQL / PostgreSQL), migraciones Flyway y directorio de archivos.
- **`IProveedorConexionBD`** — «interface».
  ```text
  + obtenerConexion() : Connection
  ```
- **`ProveedorConexionJDBC`** — «HikariCP» infraestructura.
  ```text
  - pool : HikariDataSource «bd.properties»
  + migrar() : void  «Flyway: db/migration/{motor}»
  ```
- **`IDialectoSQL`** — «Strategy» interfaz. Diferencias de SQL entre motores.
  ```text
  + carpetaMigraciones() : String
  + limitar(sql : String, n : int) : String
  ```
- **`DialectoMySQL`** — clase.
- **`DialectoPostgreSQL`** — clase.
- **`FabricaRepositorios`** — «Abstract Factory» fábrica.
  ```text
  + usuarios() : IRepositorioUsuarios
  + conexiones() : IRepositorioConexiones
  + mensajes() : IRepositorioMensajes
  + logs() : IRepositorioLogs
  + archivos() : IAlmacenamientoArchivos
  ```
- **`FabricaRepositoriosJDBC`** — fábrica.
  ```text
  - proveedor : IProveedorConexionBD
  - dialecto : IDialectoSQL «bd.motor»
  ```
- **`IRepositorioUsuarios`** — «interface».
  ```text
  + listarTodos() : List<UsuarioRegistroDTO>
  + contar() : int
  + guardarTodos(l : List<UsuarioRegistroDTO>) : void
  ```
- **`RepositorioUsuariosJDBC`** — clase.
- **`IRepositorioConexiones`** — «interface».
  ```text
  + abrir(s : SesionDTO) : void
  + cerrar(idSesion : String, fin : LocalDateTime) : void
  + cerrarHuerfanas(fin : LocalDateTime) : int
  ```
- **`RepositorioConexionesJDBC`** — clase.
- **`IRepositorioMensajes`** — «interface».
  ```text
  + guardar(m : MensajeDTO) : void  «1 transacción»
  + listarTodos() : List<MensajeDTO>
  ```
- **`RepositorioMensajesJDBC`** — clase.
- **`IRepositorioLogs`** — «interface».
  ```text
  + guardar(e : EventoLogDTO) : void
  + listarTodos() : List<EventoLogDTO>
  ```
- **`RepositorioLogsJDBC`** — clase.
- **`IAlmacenamientoArchivos`** — «interface».
  ```text
  + guardar(subdir : String, nombre : String, datos : byte[]) : Path
  + leer(ruta : String) : byte[]
  ```
- **`AlmacenamientoDirectorioLocal`** — clase.
  ```text
  - raiz : Path «archivos.directorio»
  ```

### Transversales

#### DTO-Records  ·  _paquete_
Records inmutables y enums del contrato. Sin lógica.
- **`UsuarioDTO`** — «record».
  ```text
  codigo : String
  nombres : String
  apellidos : String
  programa : String
  rol : Rol
  ```
- **`UsuarioRegistroDTO`** — «record».
  ```text
  codigo : String
  nombres : String
  apellidos : String
  contrasenaHash : String
  programa : String
  rol : Rol
  ```
- **`EstadoUsuarioDTO`** — «record».
  ```text
  usuario : UsuarioDTO
  conectado : boolean
  sesiones : int
  ```
- **`SesionDTO`** — «record».
  ```text
  idSesion : String
  codigo : String
  nombreCompleto : String
  ip : String
  desde : LocalDateTime
  ```
- **`ConexionUsuarioDTO`** — «record».
  ```text
  codigo : String
  nombreCompleto : String
  cantidadSesiones : int
  ```
- **`ContenidoDTO`** — «record».
  ```text
  tipo : TipoMensaje
  texto : String
  nombreArchivo : String
  datosBase64 : String
  ```
- **`PeticionDTO`** — «record».
  ```text
  idPeticion : String
  comando : TipoComando
  parametros : Map<String,String>
  contenido : ContenidoDTO
  ```
- **`RespuestaDTO`** — «record».
  ```text
  idPeticion : String
  exito : boolean
  codigo : CodigoError
  mensaje : String
  datos : JsonNode
  ```
- **`EventoPushDTO`** — «record».
  ```text
  evento : TipoEventoPush
  fecha : LocalDateTime
  datos : JsonNode
  ```
- **`SolicitudMensajeDTO`** — «record».
  ```text
  idMensaje : String
  idPeticion : String
  idSesion : String
  remitente : String
  canal : Canal
  destinatario : String
  contenido : ContenidoDTO
  recibido : LocalDateTime
  ```
- **`MensajeDTO`** — «record».
  ```text
  id : String
  tipo : TipoMensaje
  canal : Canal
  remitente : String
  destinatario : String
  fecha : LocalDateTime
  hash : String
  texto : String
  numCaracteres : int
  numPalabras : int
  nombreArchivo : String
  imagenes : List<ImagenVersionDTO>
  ```
- **`ImagenVersionDTO`** — «record».
  ```text
  orden : int
  etapa : TipoFiltro
  nombreArchivo : String
  rutaArchivo : String
  tamanoBytes : long
  hash : String
  ```
- **`ImagenProcesadaDTO`** — «record».
  ```text
  etapa : TipoFiltro
  datos : byte[]
  hash : String
  ```
- **`ResultadoTextoDTO`** — «record».
  ```text
  hash : String
  numCaracteres : int
  numPalabras : int
  ```
- **`ResultadoImagenDTO`** — «record».
  ```text
  hash : String
  imagenes : List<ImagenProcesadaDTO>
  ```
- **`EventoLogDTO`** — «record».
  ```text
  id : String
  tipo : TipoEvento
  descripcion : String
  usuario : String
  ip : String
  fecha : LocalDateTime
  ```
- **`InformeDTO`** — «record».
  ```text
  tipo : TipoInforme
  generado : LocalDateTime
  generadoPor : String
  filas : List<?>
  ```
- **`Rol`** — «enum».
  ```text
  USUARIO
  ADMIN
  ```
- **`Canal`** — «enum».
  ```text
  FORO
  PRIVADO
  ```
- **`TipoMensaje`** — «enum».
  ```text
  TEXTO
  IMAGEN
  ```
- **`TipoComando`** — «enum».
  ```text
  LOGIN
  LOGOUT
  LISTAR_USUARIOS
  ENVIAR_FORO
  ENVIAR_PRIVADO
  HISTORIAL
  INFORME
  OBTENER_IMAGEN
  ```
- **`TipoEventoPush`** — «enum».
  ```text
  INFO_SERVIDOR
  MENSAJE_NUEVO
  USUARIO_CONECTADO
  USUARIO_DESCONECTADO
  SESION_CERRADA
  CUPO_LLENO
  ```
- **`CodigoError`** — «enum».
  ```text
  OK
  PETICION_INVALIDA
  NO_AUTENTICADO
  CREDENCIALES_INVALIDAS
  CUPO_LLENO
  DESTINO_INEXISTENTE
  CONTENIDO_INVALIDO
  COLA_LLENA
  NO_ENCONTRADO
  ERROR_INTERNO
  ```
- **`TipoEvento`** — «enum».
  ```text
  CONEXION
  DESCONEXION
  LOGIN_OK
  LOGIN_FALLIDO
  CUPO_LLENO
  MENSAJE_FORO
  MENSAJE_PRIVADO
  ERROR
  ```
- **`TipoFiltro`** — «enum».
  ```text
  ORIGINAL
  ESCALA_GRISES
  SEPIA
  ROTAR_180
  BRILLO
  REDUCCION_TAMANO
  ```
- **`TipoInforme`** — «enum».
  ```text
  USUARIOS_REGISTRADOS
  USUARIOS_CONECTADOS
  MENSAJES
  LOGS
  ```

#### UtileriasVarias  ·  _paquete_
Configuración, hash, JSON e imágenes.
- **`ConfiguracionServidor`** — «Singleton» infraestructura. Lee servidor.properties y bd.properties. Falla al arrancar si falta una clave.
  ```text
  - props : Properties
  + cargar(servidor : Path, bd : Path) : ConfiguracionServidor  {static}
  + protocolo() : String  «nombre del protocolo; ServidorApp lo convierte con TipoProtocolo.valueOf»
  + puerto() : int
  + maxSesiones() : int
  + maxTramaBytes() : int
  + vista() : String
  + colaCapacidad() : int
  + hilosConsumidores() : int
  + directorioArchivos() : Path
  + archivoUsuarios() : Path
  + bd() : Properties
  + cache() : Properties
  ```
- **`IGeneradorHash`** — «interface».
  ```text
  + generar(datos : byte[]) : String  «hex 64»
  ```
- **`GeneradorHashSHA256`** — clase.
- **`ISerializador`** — «interface».
  ```text
  + aJson(o : Object) : String
  + desdeJson(s : String, t : Class<T>) : T
  + aArbol(o : Object) : JsonNode
  + convertir(n : JsonNode, t : Class<T>) : T
  ```
- **`SerializadorJSON`** — «Jackson» clase.
  ```text
  - mapper : ObjectMapper «JavaTimeModule»
  ```
- **`UtilImagen`** — clase.
  ```text
  + aBytes(img : BufferedImage, formato : String) : byte[]  {static}
  + desdeBytes(datos : byte[]) : BufferedImage  {static}
  ```

#### Fachada  ·  _paquete_
Contratos compartidos entre capas.
- **`IFachadaServidor`** — «Facade» interfaz. Lo que las vistas pueden pedir al servidor.
  ```text
  + iniciarServidor() : void
  + detenerServidor() : void
  + publicarEnForo(c : ContenidoDTO) : void
  + enviarPrivado(destino : String, c : ContenidoDTO) : void
  + informe(t : TipoInforme, filtros : Map<String,String>) : String
  + listarSesiones() : List<SesionDTO>
  + listarUsuarios() : List<EstadoUsuarioDTO>
  + cerrarSesion(idSesion : String) : void
  + suscribir(o : IObservadorServidor) : void
  + getIpServidor() : String
  ```
- **`IObservadorServidor`** — «Observer» interfaz. Lo implementan las vistas para enterarse de lo que ocurre.
  ```text
  + onMensaje(m : MensajeDTO) : void
  + onEvento(e : EventoLogDTO) : void
  + onSesiones(l : List<SesionDTO>) : void
  ```
- **`ICanalCliente`** — «independiente del transporte» interfaz. Un cliente conectado. Lo implementa el transporte (CanalTCP) y lo guarda ConexionesClientes.
  ```text
  + getId() : String
  + getIpRemota() : String
  + enviar(json : String) : void  «no bloquea»
  + cerrar() : void
  + estaAbierto() : boolean
  ```

## 14. Lista de revisión antes de dar por terminada una parte

- [ ] El `pom.xml` del módulo respeta la matriz de la sección 4.
- [ ] Ningún componente importa clases de otro componente ni de un paquete.
- [ ] `Usuarios` y `ConexionesClientes` no importan `persistencia` ni `almacenar-informacion`.
- [ ] `java.net` solo está en `protocolo-comunicacion` y `java.sql` solo en `persistencia`.
- [ ] Los nombres de clases, métodos y DTO coinciden con el catálogo (sección 13).
- [ ] Los enums del protocolo conservan el orden y los nombres.
- [ ] Hay pruebas unitarias del módulo; `mvn -q verify` pasa.
- [ ] Toda dependencia nueva tiene una versión verificada en Maven Central y está declarada en el `pom.xml` padre.
