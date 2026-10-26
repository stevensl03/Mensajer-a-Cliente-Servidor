# Cliente: diagrama de clases por componentes

> Fuente: `docs/arquitectura/cliente_clases_componentes.drawio`.
> Este documento describe ese diagrama en texto para que un asistente de código lo implemente **sin ver el .drawio**. El contrato de red es el de `servidor_clases_componentes.md`, sección 7, y es obligatorio.

## 0. Reglas que no se negocian

**Jerarquía (en caso de conflicto gana el de arriba):**

1. `.claude/contexto.txt`.
2. El contrato del protocolo definido por el servidor (`servidor_clases_componentes.md` §7).
3. Consideraciones acordadas con el usuario (§1.2).
4. `.claude/skills/SKILL.md`: solo sus reglas de concurrencia, framing y pruebas que no contradigan lo anterior.

**Reglas de estructura:**

- Arquitectura en capas: Presentación → Servicios de aplicación → Comunicación / Persistencia local. Ninguna capa conoce a una superior.
- Las vistas (FXML) solo enlazan controles con su **ViewModel**. Los ViewModels solo conocen `IFachadaCliente`.
- **Solo `ProtocoloComunicacion` conoce sockets.** Hoy implementa TCP. Para cambiar de protocolo se toca solo ese componente.
- **Solo `ProtocoloClienteServidor` conoce el JSON del protocolo.**
- **Solo `PersistenciaLocal` conoce JDBC y H2.**
- **Solo `AppCliente` instancia clases concretas** (Composition Root).
- Toda modificación de la UI ocurre en el hilo de JavaFX (`Platform.runLater`).
- Java 21 + JavaFX. No inventar versiones de librerías: verificarlas en Maven Central.
- Las desviaciones aprobadas respecto al diagrama original están en la §12 (D-03…D-14) y ya están reflejadas en este documento y en el `.drawio`.

## 1. Qué debe hacer el cliente

### 1.1 Requisitos de `contexto.txt`

| Requisito | Dónde se cumple |
|---|---|
| Ver qué usuarios están conectados; todos los usuarios son visibles | `LISTAR_USUARIOS` + eventos `USUARIO_CONECTADO` / `USUARIO_DESCONECTADO` → `ChatViewModel.usuarios` |
| Intercambiar mensajes (texto o archivos) con un usuario específico | Pestañas privadas de `VistaChat` → `ENVIAR_PRIVADO` (texto o imagen) |
| Persistencia local en H2 con configuración en archivo | `PersistenciaLocal` + `cliente.properties` |
| Aplicación de escritorio con capas y componentes | JavaFX + MVVM, en capas |
| Protocolo TCP/IP | `ProtocoloComunicacion` / `tcp` |
| Puede estar en otro lenguaje | Se cumple porque el contrato es JSON sobre tramas de longitud prefijada. Este cliente en Java es la implementación de referencia. |

### 1.2 Consideraciones acordadas

- Además del chat privado, existe el **foro** (FORO), y se puede escribir por privado al **ADMIN**.
- El cliente **ve todos los informes** del servidor (4 tipos, en JSON).
- La barra de estado muestra **«Estoy conectado a: <IP del servidor>»**.
- **Lo que el usuario genera y recibe en su sesión se guarda en H2.** Hay una base por usuario, en `./data/cliente_{codigo}`.
- Hoy solo **TCP**; el selector de protocolo de la pantalla de login muestra solo TCP.

## 2. Estructura

```
Presentación (JavaFX · MVVM)                Servicios de aplicación                 Comunicación                         Externos
InicioCliente  (AppCliente, FabricaVistas,  FachadaCliente ─► IServicioUsuarios ─○ Usuarios ─┐                        
               NavegadorVistas, TipoVista)       │        ─► IServicioChat     ─○ Chat ─────┼─► IProtocoloCliente ─○ ProtocoloClienteServidor
Vistas (FXML) ─binding─► ViewModels ─► IFachadaCliente ─► IServicioInformes ─○ Informes ─┘          │
                                                                                                    └─► IConexionServidor ─○ ProtocoloComunicacion ─TCP─► SERVIDOR
                                                    Usuarios y Chat ─► PersistenciaLocal (IProveedorConexionBD, repositorios) ─JDBC─► H2
Transversales: DTO-Records (módulo dto-records del servidor) · UtileriasVarias (ConfiguracionCliente, ISerializador)
```

## 3. Módulos Maven y paquetes

El cliente es un proyecto Maven multimódulo **aparte**, en la carpeta `cliente/`, con su propio `pom.xml` padre (`com.universidad:mensajeria-cliente`). Reutiliza el artefacto `com.universidad:dto-records` del servidor; antes de compilar el cliente, ejecutar `mvn -pl transversales/dto-records -am install` en la raíz del servidor. Paquete base: `com.universidad.mensajeria.cliente`.

| Contenedor | Módulo (`cliente/…`) | Paquete |
|---|---|---|
| `UtileriasVarias` | `transversales/utilerias-cliente` | `…cliente.utilerias` |
| `ProtocoloComunicacion` (+ `tcp`) | `comunicacion/protocolo-comunicacion` | `…cliente.comunicacion`, `…cliente.comunicacion.tcp` |
| `ProtocoloClienteServidor` | `comunicacion/protocolo-cliente-servidor` | `…cliente.protocolo` |
| `PersistenciaLocal` | `persistencia/persistencia-local` | `…cliente.persistencia` (+ `src/main/resources/schema.sql`) |
| Componentes `Usuarios`, `Chat`, `Informes` y sus interfaces | `servicios/servicios-cliente` | `…cliente.servicios.usuarios`, `….chat`, `….informes` |
| `FachadaCliente` | `servicios/fachada-cliente` | `…cliente.fachada` |
| `InicioCliente`, `Vistas`, `ViewModels` | `presentacion/app-cliente` | `…cliente.inicio`, `…cliente.vistas` (+ `src/main/resources/fxml/*.fxml`, `css/`), `…cliente.viewmodels` |

## 4. Dependencias permitidas

| Módulo | Puede depender de |
|---|---|
| `utilerias-cliente` | `dto-records`, `jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api` |
| `protocolo-comunicacion` | `utilerias-cliente`, `slf4j-api` (**sin** Jackson ni DTO del protocolo) |
| `protocolo-cliente-servidor` | `protocolo-comunicacion` (solo `IConexionServidor` / `IReceptorServidor`), `dto-records`, `utilerias-cliente`, `slf4j-api` |
| `persistencia-local` | `dto-records`, `utilerias-cliente`, `h2` (`runtime`), `slf4j-api` |
| `servicios-cliente` | `protocolo-cliente-servidor` (solo `IProtocoloCliente` / `IOyentePush`), `persistencia-local` (solo interfaces), `dto-records`, `utilerias-cliente` |
| `fachada-cliente` | `servicios-cliente` (solo interfaces), `protocolo-cliente-servidor` (solo `IProtocoloCliente` / `IOyentePush`), `dto-records`, `utilerias-cliente` (D-04) |
| `app-cliente` | todos (Composition Root), `javafx-controls`, `javafx-fxml`, `logback-classic` (`runtime`) |

Las clases concretas se usan **solo** en `AppCliente`. Las demás clases reciben interfaces por constructor.

## 5. Flujos

### 5.1 Arranque

1. `AppCliente.start(stage)`:
   - `cfg = ConfiguracionCliente.cargar(Path.of("config/cliente.properties"))`;
   - `json = new SerializadorJSON()`;
   - `conexion = new FabricaConexiones().crear(TipoProtocolo.valueOf(cfg.protocolo()), cfg.maxTramaBytes())` (D-03);
   - `protocolo = new FabricaProtocoloCliente().crear(conexion, json, Duration.ofSeconds(cfg.timeoutSegundos()))`;
   - `proveedor = new ProveedorConexionH2(cfg.urlH2(), cfg.usuarioH2(), cfg.claveH2())`;
   - `repos = new FabricaRepositoriosH2(proveedor)`;
   - crea `ServicioUsuarios(protocolo, proveedor, repos.sesiones(), json)`, `ServicioChat(protocolo, repos.mensajes(), new CacheMensajesSesion(cfg.cacheMax()), json)` y `ServicioInformes(protocolo, json)` (D-04);
   - crea `FachadaCliente(usuarios, chat, informes, protocolo, json)` y llama a `protocolo.suscribir(fachada)`;
   - crea el ejecutor de fondo (hilos *daemon*) y `hiloUI = Platform::runLater` para los ViewModels (D-10).
2. `NavegadorVistas.ir(LOGIN)`. `FabricaVistas` carga el FXML con un *controller factory* que inyecta el ViewModel, y el ViewModel recibe `IFachadaCliente`, `hiloUI` y el ejecutor de fondo.
3. `stop()`: si hay sesión, `fachada.logout()`; después se cierra la conexión y la BD.

### 5.2 Login (pantalla C1)

1. `LoginViewModel.ingresar()` separa `servidor` en `host:puerto` (el valor inicial viene de `cliente.properties`).
2. `fachada.conectar(host, puerto)` → (solo si `!conexion.estaConectada()`) `protocolo.conectar` → `conexion.conectar(host, puerto, receptor)` → el servidor envía `INFO_SERVIDOR` y se guarda `ipServidor`. `ConexionTCP` admite un nuevo `conectar` después de `cerrar()` (D-06); `CREDENCIALES_INVALIDAS` no cierra el canal.
3. `fachada.login(codigo, clave)` → `ServicioUsuarios.login` → `protocolo.solicitar(LOGIN, {codigo, clave}, null)`.
4. Si `exito`:
   - `proveedor.abrir(codigo)` crea o abre `./data/cliente_{codigo}` y ejecuta `schema.sql` si las tablas no existen;
   - `sesionLocal = repoSesion.abrir(usuario, ipServidor, puerto)`;
   - `chat.iniciarSesion(usuario, sesionLocal)`;
   - se navega a CHAT.
5. Si falla, `error` muestra un texto según el código:
   - `CREDENCIALES_INVALIDAS` → «Código o contraseña incorrectos»;
   - `CUPO_LLENO` → «El servidor alcanzó el cupo de sesiones. Intenta más tarde.» (el protocolo no envía N/N);
   - error de red → «No fue posible conectar con host:puerto».

### 5.3 Chat (pantalla C2)

- Al entrar: `fachada.usuarios()` (`LISTAR_USUARIOS`) y `fachada.historial("FORO")` (`HISTORIAL`). Lo recibido se guarda en H2 y en la caché.
- **Enviar texto**: `enviarForo(texto)` o `enviarPrivado(codigo, texto)` → `ServicioChat.enviar(canal, destino, ContenidoDTO(TEXTO, …))` → `ENVIAR_FORO` / `ENVIAR_PRIVADO`. La respuesta `ENCOLADO` **no** pinta el mensaje: este aparece cuando llega el push `MENSAJE_NUEVO`, que el servidor también envía al remitente.
- **Enviar imagen**: se lee el archivo, se valida tamaño y extensión, se envía con `ContenidoDTO(IMAGEN, nombreArchivo, datosBase64)`. Los bytes originales se guardan en memoria con el `idMensaje` de la respuesta y pasan a `ADJUNTO` (etapa `ORIGINAL`) cuando llega su `MENSAJE_NUEVO`.
- **Recibir** (`FachadaCliente.onPush`):
  - `MENSAJE_NUEVO` → `chat.recibir(m)` guarda en H2 (MERGE por id, con `propio = remitente == yo`) y en la caché; después `observadores.onMensaje(m)`;
  - `USUARIO_CONECTADO` / `USUARIO_DESCONECTADO` → se vuelve a pedir `LISTAR_USUARIOS` → `onUsuarios`;
  - `SESION_CERRADA` → `onDesconexion(motivo)` y se vuelve a LOGIN.
- **Abrir privado**: doble clic en un usuario → `abrirPrivado(codigo)` crea la pestaña y carga `historial(codigo)`. Primero lee la caché y H2 (lo devuelve de inmediato), y luego actualiza con `HISTORIAL` del servidor: cada mensaje recibido así se guarda y se notifica con `onMensaje` (D-11).
- **Ver 5 filtros**: `imagen(m, etapa)` → primero `repoMensajes.adjunto(id, etapa)`; si no está, `OBTENER_IMAGEN` y se guarda como `ADJUNTO`.
- **Barra de estado**: `Estoy conectado a: {ipServidor} · TCP :{puerto} · Sesión local H2 #{sesionLocal} · {n} mensajes guardados` (`sesionLocal()` y `mensajesGuardados()` de la fachada, D-05 y D-12).

### 5.4 Informes (pantalla C3)

`InformesViewModel.cargar(tipo, filtros)` → `fachada.informe` → `INFORME {tipo, tipoEvento?, usuario?, desde?, hasta?}`. Las `filas` se convierten en `Map<String,Object>` para la `TableView` (columnas = claves del primer elemento). El botón «Ver JSON» muestra la respuesta tal como llegó: `informe` devuelve `InformeRecibido(informe, json)` (D-08).

### 5.5 Salida o desconexión

- «Salir»: `fachada.logout()` → `LOGOUT` (espera acotada a 3 s; `logout()` sigue siendo `void`) → `repoSesion.cerrar(sesionLocal)` → `conexion.cerrar()` → LOGIN. Se llama fuera del hilo de JavaFX.
- Si se pierde la conexión (`IReceptorServidor.alDesconectar`): todas las peticiones pendientes se completan con excepción, se cierra la sesión local y la UI muestra el motivo.

## 6. Concurrencia

- `ConexionTCP`: un **hilo lector** (lee tramas y llama a `receptor.alRecibir`) y un **hilo escritor único** con una cola acotada; `enviar()` solo encola.
- `ClienteProtocoloJSON.solicitar`: genera `idPeticion` (UUID), guarda un `CompletableFuture` en `pendientes` y lo completa al llegar la respuesta con el mismo id. Usa `orTimeout(timeoutSegundos)`.
- Lo que llega desde la red se procesa en el hilo lector. Ningún ViewModel toca controles fuera de `Platform.runLater`.
- Las escrituras en H2 se hacen fuera del hilo de JavaFX.

## 7. Configuración

`config/cliente.properties`:

| Clave | Por defecto |
|---|---|
| `servidor.host` | `127.0.0.1` |
| `servidor.puerto` | `5000` |
| `servidor.protocolo` | `TCP` |
| `servidor.maxTramaBytes` | `16777216` |
| `peticion.timeoutSegundos` | `15` |
| `h2.url` | `jdbc:h2:./data/cliente_{codigo}` (se reemplaza `{codigo}`) |
| `h2.usuario` / `h2.clave` | `sa` / (vacío) |
| `cache.mensajes.max` | `500` (conversaciones en la LRU) |

## 8. Esquema H2 (`schema.sql`, idempotente)

```sql
CREATE TABLE IF NOT EXISTS SESION (
  ID             BIGINT AUTO_INCREMENT PRIMARY KEY,
  CODIGO_USUARIO VARCHAR(20)  NOT NULL,
  IP_SERVIDOR    VARCHAR(45)  NOT NULL,
  PUERTO         INT          NOT NULL,
  INICIO         TIMESTAMP(3) NOT NULL,
  FIN            TIMESTAMP(3)
);
CREATE TABLE IF NOT EXISTS MENSAJE (
  ID             VARCHAR(36)  PRIMARY KEY,
  SESION_ID      BIGINT       NOT NULL REFERENCES SESION(ID),
  CANAL          VARCHAR(10)  NOT NULL,
  REMITENTE      VARCHAR(20)  NOT NULL,
  DESTINATARIO   VARCHAR(20),
  CONVERSACION   VARCHAR(41)  NOT NULL,   -- 'FORO' o 'menor|mayor'
  TIPO           VARCHAR(10)  NOT NULL,
  TEXTO          CLOB,
  NOMBRE_ARCHIVO VARCHAR(255),
  HASH           CHAR(64)     NOT NULL,
  NUM_CARACTERES INT,
  NUM_PALABRAS   INT,
  FECHA          TIMESTAMP(3) NOT NULL,
  PROPIO         BOOLEAN      NOT NULL
);
CREATE INDEX IF NOT EXISTS IX_MENSAJE_CONV ON MENSAJE(CONVERSACION, FECHA);
CREATE TABLE IF NOT EXISTS ADJUNTO (
  ID             BIGINT AUTO_INCREMENT PRIMARY KEY,
  MENSAJE_ID     VARCHAR(36)  NOT NULL REFERENCES MENSAJE(ID),
  ETAPA          VARCHAR(20)  NOT NULL,
  NOMBRE_ARCHIVO VARCHAR(255) NOT NULL,
  DATOS          BLOB         NOT NULL,
  CONSTRAINT UQ_ADJUNTO UNIQUE (MENSAJE_ID, ETAPA)
);
```

El esquema no guarda las 6 `ImagenVersionDTO`: un `MensajeDTO` leído de H2 tiene `imagenes = []` y la UI usa `idMensaje` + `TipoFiltro.values()` (D-07). Se usa `MERGE INTO MENSAJE KEY(ID)` (conservando el `SESION_ID` de la primera vez) para no duplicar un mensaje que llega por push y también por `HISTORIAL`. El ADJUNTO ORIGINAL de una imagen propia se guarda cuando llega su `MENSAJE_NUEVO`, porque `MENSAJE` debe existir antes por la clave foránea.

## 9. Cambiar de protocolo en el cliente

Igual que en el servidor: subpaquete nuevo en `comunicacion/protocolo-comunicacion` con `ConexionXxx implements IConexionServidor`, el valor nuevo en `TipoProtocolo`, el `case` en `FabricaConexiones` y `servidor.protocolo` en `cliente.properties`. `ProtocoloClienteServidor`, los servicios y la UI no cambian.

## 10. Catálogo de clases (generado desde el diagrama)

Notación: `+` público, `-` privado, `«…»` estereotipo o nota. Los DTO son los del servidor (`servidor_clases_componentes.md` §13, paquete `DTO-Records`).

### Capa de Presentación (JavaFX · MVVM)

#### InicioCliente  ·  _paquete_
Arranque y navegación.
- **`AppCliente`** — «Composition Root · extends Application» clase. Único punto que conoce clases concretas del cliente; crea todo con fábricas e inyecta IFachadaCliente en los ViewModels.
  ```text
  + start(stage : Stage) : void
  + stop() : void
  - ensamblar(cfg : ConfiguracionCliente) : IFachadaCliente
  ```
- **`FabricaVistas`** — «Factory». Carga el FXML y entrega al controlador su ViewModel ya construido.
  ```text
  - fachada : IFachadaCliente
  - hiloUI, fondo : Executor  «D-10»
  + crear(t : TipoVista) : Parent  «FXMLLoader + ViewModel»
  ```
- **`NavegadorVistas`** — clase.
  ```text
  - stage : Stage
  - fabrica : FabricaVistas
  + ir(t : TipoVista) : void
  ```
- **`TipoVista`** — «enum».
  ```text
  LOGIN
  CHAT
  INFORMES
  ```

#### Vistas (FXML)  ·  _paquete_
Controladores FXML: solo enlazan controles con el ViewModel.
- **`VistaLogin`** — «FXML Controller» clase. Pantalla C1.
  ```text
  - vm : LoginViewModel
  - txtCodigo : TextField
  - txtClave : PasswordField
  - txtServidor : TextField
  - cmbProtocolo : ComboBox «solo TCP»
  - lblError : Label
  + onIngresar() : void
  ```
- **`VistaChat`** — «FXML Controller» clase. Pantalla C2.
  ```text
  - vm : ChatViewModel
  - lstUsuarios : ListView<EstadoUsuarioDTO>
  - tabs : TabPane «Foro + privados»
  - txtMensaje : TextField
  - lblEstado : Label «Estoy conectado a: IP»
  + onEnviar() : void
  + onAdjuntarImagen() : void
  + onAbrirPrivado(u : EstadoUsuarioDTO) : void
  + onVerFiltros(m : MensajeDTO) : void
  + onInformes() : void
  + onSalir() : void
  ```
- **`VistaInformes`** — «FXML Controller» clase. Pantalla C3.
  ```text
  - vm : InformesViewModel
  - tabs : TabPane «4 informes»
  - tblDatos : TableView<Map<String,Object>>
  - cmbTipoEvento : ComboBox
  - txtUsuario : TextField
  - dpDesde, dpHasta : DatePicker
  + onFiltrar() : void
  + onVerJson() : void
  + onPagina(n : int) : void
  ```

#### ViewModels  ·  _paquete_
Estado observable de cada pantalla.
- **`LoginViewModel`** — clase.
  ```text
  - codigo : StringProperty
  - clave : StringProperty
  - servidor : StringProperty «host:puerto»
  - error : StringProperty
  - ocupado : BooleanProperty
  - fachada : IFachadaCliente
  - hiloUI, fondo : Executor  «D-10»
  + ingresar() : CompletableFuture<Boolean>
  ```
- **`ChatViewModel`** — «IObservadorCliente» clase. Recibe los avisos de la fachada y los pasa a la UI con Platform.runLater.
  ```text
  - usuarios : ObservableList<EstadoUsuarioDTO>
  - foro : ObservableList<MensajeDTO>
  - privados : ObservableMap<String, ObservableList<MensajeDTO>>
  - estadoConexion : StringProperty
  - fachada : IFachadaCliente
  - hiloUI, fondo : Executor  «D-10»
  + enviarForo(texto : String) : void
  + enviarPrivado(codigo : String, texto : String) : void
  + enviarImagen(destino : String, f : File) : void
  + abrirPrivado(codigo : String) : void
  + imagen(m : MensajeDTO, etapa : TipoFiltro) : CompletableFuture<Image>
  + onMensaje(m : MensajeDTO) : void
  + onUsuarios(l : List<EstadoUsuarioDTO>) : void
  + onDesconexion(motivo : String) : void
  ```
- **`InformesViewModel`** — clase.
  ```text
  - tipo : ObjectProperty<TipoInforme>
  - filas : ObservableList<Map<String,Object>>
  - json : StringProperty
  - fachada : IFachadaCliente
  - hiloUI, fondo : Executor  «D-10»
  + cargar(t : TipoInforme, filtros : Map<String,String>) : void
  ```

### Capa de Servicios de aplicación

#### FachadaCliente  ·  _paquete_
Fachada del cliente.
- **`IFachadaCliente`** — «Facade» interfaz. Lo único que ven los ViewModels.
  ```text
  + conectar(host : String, puerto : int) : String  «ip servidor»
  + login(codigo : String, clave : String) : CompletableFuture<UsuarioDTO>
  + logout() : void
  + usuarios() : CompletableFuture<List<EstadoUsuarioDTO>>
  + enviarForo(c : ContenidoDTO) : CompletableFuture<Void>
  + enviarPrivado(destino : String, c : ContenidoDTO) : CompletableFuture<Void>
  + historial(conversacion : String) : List<MensajeDTO>
  + imagen(idMensaje : String, etapa : TipoFiltro) : CompletableFuture<byte[]>
  + informe(t : TipoInforme, filtros : Map<String,String>) : CompletableFuture<InformeRecibido>  «D-08»
  + getIpServidor() : String
  + usuarioActual() : Optional<UsuarioDTO>  «D-05»
  + sesionLocal() : long  «D-05»
  + mensajesGuardados() : long  «D-12»
  + suscribir(o : IObservadorCliente) : void
  ```
- **`FachadaCliente`** — «Facade · IOyentePush» clase principal del componente. Orquesta los servicios y reparte los eventos push a los observadores.
  ```text
  - usuarios : IServicioUsuarios
  - chat : IServicioChat
  - informes : IServicioInformes
  - protocolo : IProtocoloCliente
  - json : ISerializador  «D-04»
  - observadores : CopyOnWriteArrayList<IObservadorCliente>
  + onPush(e : EventoPushDTO) : void
  + onDesconexion(motivo : String) : void
  ```
- **`IObservadorCliente`** — «Observer» interfaz.
  ```text
  + onMensaje(m : MensajeDTO) : void
  + onUsuarios(l : List<EstadoUsuarioDTO>) : void
  + onDesconexion(motivo : String) : void
  ```
- **`IServicioUsuarios`** — «interface».
  ```text
  + login(codigo : String, clave : String) : CompletableFuture<UsuarioDTO>
  + logout() : void
  + listar() : CompletableFuture<List<EstadoUsuarioDTO>>
  + actual() : Optional<UsuarioDTO>
  + sesionLocal() : long
  ```

#### Usuarios  ·  _componente_
- **`ServicioUsuarios`** — «IServicioUsuarios» clase principal del componente. LOGIN/LOGOUT/LISTAR_USUARIOS; tras el login abre la BD H2 del usuario y registra la sesión local.
  ```text
  - protocolo : IProtocoloCliente
  - proveedor : IProveedorConexionBD
  - sesiones : IRepositorioSesion
  - json : ISerializador  «D-04»
  - actual : UsuarioDTO
  - sesionLocal : long
  ```
- **`IServicioChat`** — «interface».
  ```text
  + iniciarSesion(u : UsuarioDTO, sesionLocal : long) : void
  + enviar(canal : Canal, destino : String, c : ContenidoDTO) : CompletableFuture<Void>
  + recibir(m : MensajeDTO) : void
  + historial(conversacion : String) : List<MensajeDTO>
  + imagen(idMensaje : String, etapa : TipoFiltro) : CompletableFuture<byte[]>
  ```

#### Chat  ·  _componente_
- **`ServicioChat`** — «IServicioChat» clase principal del componente. Envía y recibe mensajes; todo lo enviado y recibido se guarda en H2.
  ```text
  - protocolo : IProtocoloCliente
  - repo : IRepositorioMensajesLocal
  - cache : CacheMensajesSesion
  - json : ISerializador  «D-04»
  - originalesPendientes : Map<String, byte[]>
  - usuario : UsuarioDTO
  - sesionLocal : long
  ```
- **`CacheMensajesSesion`** — «L1 · LRU en memoria» infraestructura.
  ```text
  - mapa : LinkedHashMap<String, List<MensajeDTO>>
  - max : int
  + get(conversacion : String) : List<MensajeDTO>
  + agregar(m : MensajeDTO) : void
  ```
- **`IServicioInformes`** — «interface».
  ```text
  + obtener(t : TipoInforme, filtros : Map<String,String>) : CompletableFuture<InformeRecibido>  «D-08»
  ```
- **`InformeRecibido`** — «record» (D-08), mismo paquete `…servicios.informes`.
  ```text
  + informe : InformeDTO
  + json : String  «datos tal como llegaron, con sangría»
  ```

#### Informes  ·  _componente_
- **`ServicioInformes`** — «IServicioInformes» clase principal del componente. Comando INFORME; el cliente ve los 4 informes.
  ```text
  - protocolo : IProtocoloCliente
  - json : ISerializador  «D-04»
  ```

### Capa de Comunicación
- **`IProtocoloCliente`** — «InterfazClienteServidor» interfaz. Petición/respuesta asíncrona correlacionada por idPeticion + eventos push.
  ```text
  + conectar(host : String, puerto : int) : String
  + solicitar(c : TipoComando, params : Map<String,String>, contenido : ContenidoDTO) : CompletableFuture<RespuestaDTO>
  + suscribir(o : IOyentePush) : void
  + desconectar() : void
  ```
- **`IOyentePush`** — «callback» interfaz.
  ```text
  + onPush(e : EventoPushDTO) : void
  + onDesconexion(motivo : String) : void
  ```

#### ProtocoloClienteServidor  ·  _componente_
Formato JSON del protocolo (mismo contrato que el servidor).
- **`ClienteProtocoloJSON`** — «IProtocoloCliente · IReceptorServidor» clase principal del componente. JSON ↔ DTO; no conoce sockets.
  ```text
  - conexion : IConexionServidor
  - json : ISerializador
  - pendientes : ConcurrentHashMap<String, CompletableFuture<RespuestaDTO>>
  - despachador : DespachadorEventos
  - timeout : Duration
  + alRecibir(json : String) : void  «respuesta si trae idPeticion, push si trae evento»
  + alDesconectar(motivo : String) : void
  ```
- **`DespachadorEventos`** — «Observer · sujeto» clase.
  ```text
  - oyentes : CopyOnWriteArrayList<IOyentePush>
  + despachar(e : EventoPushDTO) : void
  + desconexion(motivo : String) : void
  ```
- **`FabricaProtocoloCliente`** — «Factory».
  ```text
  + crear(c : IConexionServidor, json : ISerializador, timeout : Duration) : IProtocoloCliente
  ```
- **`IConexionServidor`** — «InterfazProtocoloComunicacion» interfaz. Transporte. Hoy: TCP.
  ```text
  + conectar(host : String, puerto : int, r : IReceptorServidor) : void
  + enviar(json : String) : void
  + cerrar() : void
  + getIpServidor() : String
  + estaConectada() : boolean
  ```
- **`IReceptorServidor`** — «callback» interfaz.
  ```text
  + alRecibir(json : String) : void
  + alDesconectar(motivo : String) : void
  ```

#### ProtocoloComunicacion  ·  _componente_
Transporte. Cambiar de protocolo solo toca este componente.
- **`FabricaConexiones`** — «Factory». Punto de extensión para otro protocolo.
  ```text
  + crear(t : TipoProtocolo, maxTramaBytes : int) : IConexionServidor
  ```
- **`TipoProtocolo`** — «enum».
  ```text
  TCP
  ```

##### tcp  ·  _subpaquete_
- **`ConexionTCP`** — «IConexionServidor» clase principal del componente.
  ```text
  - socket : Socket
  - lector : Thread
  - salida : BlockingQueue<String>
  - escritor : Thread «único escritor»
  - tramas : CodificadorTramas
  - receptor : IReceptorServidor
  «reconectable: conectar() de nuevo tras cerrar() · D-06»
  ```
- **`CodificadorTramas`** — «[int32 longitud][UTF-8]» clase.
  ```text
  - maxTramaBytes : int
  + leer(in : DataInputStream) : byte[]
  + escribir(out : DataOutputStream, b : byte[]) : void
  ```

### Capa de Persistencia local (H2)

#### PersistenciaLocal  ·  _paquete_
Base de datos H2 embebida, una por usuario.
- **`IProveedorConexionBD`** — «interface».
  ```text
  + abrir(codigoUsuario : String) : void
  + obtenerConexion() : Connection
  + cerrar() : void
  ```
- **`ProveedorConexionH2`** — «embebido» infraestructura.
  ```text
  - url : String «jdbc:h2:./data/cliente_{codigo}»
  - usuario : String
  - clave : String
  - inicializarEsquema() : void  «schema.sql»
  ```
- **`IRepositorioSesion`** — «interface».
  ```text
  + abrir(u : UsuarioDTO, ipServidor : String, puerto : int) : long
  + cerrar(id : long) : void
  ```
- **`RepositorioSesionH2`** — clase.
- **`IRepositorioMensajesLocal`** — «interface».
  ```text
  + guardar(m : MensajeDTO, sesion : long, propio : boolean) : void
  + guardarAdjunto(idMensaje : String, etapa : TipoFiltro, nombre : String, datos : byte[]) : void
  + adjunto(idMensaje : String, etapa : TipoFiltro) : Optional<byte[]>
  + conversacion(clave : String) : List<MensajeDTO>
  + contar() : long  «D-12»
  ```
- **`RepositorioMensajesH2`** — clase.
- **`FabricaRepositoriosH2`** — «Factory».
  ```text
  - proveedor : IProveedorConexionBD
  + sesiones() : IRepositorioSesion
  + mensajes() : IRepositorioMensajesLocal
  ```

### Transversales

#### DTO-Records  ·  _paquete_
- **`dto-records (módulo del servidor)`** — «dependencia Maven» record. El cliente reutiliza el mismo módulo de DTOs: un solo contrato.
  ```text
  UsuarioDTO · EstadoUsuarioDTO · ContenidoDTO
  PeticionDTO · RespuestaDTO · EventoPushDTO
  MensajeDTO · ImagenVersionDTO · InformeDTO
  Rol · Canal · TipoMensaje · TipoFiltro
  TipoComando · TipoEventoPush · CodigoError · TipoInforme
  ```

#### UtileriasVarias  ·  _paquete_
- **`ConfiguracionCliente`** — «Singleton» infraestructura. Lee cliente.properties.
  ```text
  - props : Properties
  + cargar(ruta : Path) : ConfiguracionCliente  {static}
  + host() : String
  + puerto() : int
  + protocolo() : String  «D-03»
  + maxTramaBytes() : int
  + urlH2() : String
  + usuarioH2() : String
  + claveH2() : String
  + cacheMax() : int
  + timeoutSegundos() : int
  ```
- **`ISerializador`** — «interface».
  ```text
  + aJson(o : Object) : String
  + desdeJson(s : String, t : Class<T>) : T
  + convertir(n : JsonNode, t : TypeReference<T>) : T
  ```
- **`SerializadorJSON`** — «Jackson» clase.
  ```text
  - mapper : ObjectMapper «JavaTimeModule»
  ```

## 11. Lista de revisión

- [ ] Los ViewModels solo dependen de `IFachadaCliente`; las vistas no llaman a servicios.
- [ ] `java.net` solo está en `protocolo-comunicacion` y `java.sql` solo en `persistencia-local`.
- [ ] Ningún control de JavaFX se modifica fuera de `Platform.runLater`.
- [ ] Todo `MensajeDTO` recibido queda en H2, y también cada imagen descargada o enviada.
- [ ] La barra de estado muestra «Estoy conectado a: IP».
- [ ] Las pruebas pasan (`mvn -q verify` en `cliente/`).

## 12. Desviaciones aprobadas (D-03…D-14)

Aprobadas para la implementación del cliente (detalle en `cliente/planCliente.md` §0 y `cliente/warning.md`). Ya están aplicadas en este documento y en el `.drawio`.

| ID | Cambio | Motivo |
|---|---|---|
| D-03 | `ConfiguracionCliente.protocolo()` devuelve `String`; `AppCliente` hace `TipoProtocolo.valueOf` | `utilerias-cliente` no puede depender de `protocolo-comunicacion` (mismo precedente que el servidor) |
| D-04 | `ISerializador` en `ServicioUsuarios`, `ServicioChat`, `ServicioInformes` y `FachadaCliente`; `fachada-cliente` depende de `utilerias-cliente` | `RespuestaDTO.datos` y `EventoPushDTO.datos` son `JsonNode` |
| D-05 | `usuarioActual()` y `sesionLocal()` en `IFachadaCliente` | C2 muestra «Tú», la cabecera y la sesión local |
| D-06 | `ConexionTCP` se puede reconectar con el mismo `conectar(...)` | El servidor cierra el canal tras `LOGOUT`, `CUPO_LLENO` y `SESION_CERRADA` |
| D-07 | El esquema H2 no cambia; `imagenes = []` al leer de H2 | La UI solo necesita `idMensaje` + `TipoFiltro` |
| D-08 | `informe(...)` devuelve `InformeRecibido(informe, json)` | «Ver JSON» muestra la respuesta tal como llegó |
| D-09 | Se permite iniciar sesión como `ADMIN01` desde el cliente | El servidor no lo impide |
| D-10 | Los ViewModels y `FabricaVistas` reciben `Executor hiloUI` y `Executor fondo` | Probar los ViewModels sin iniciar JavaFX |
| D-11 | `historial()` sigue síncrono; el refresco del servidor llega por `onMensaje` | §5.3 no definía cómo llegaba la actualización |
| D-12 | `contar()` en `IRepositorioMensajesLocal` y `mensajesGuardados()` en `IFachadaCliente` | «{n} mensajes guardados» de la barra de estado |
| D-13 | JavaFX 21.0.13, H2 2.5.252, Jackson 2.19.2 (el de `dto-records`) | Últimas estables verificadas en Maven Central |
| D-14 | Sin `Launcher` ni jar ejecutable del cliente; se ejecuta con `mvn javafx:run` | La fase 12 no pide jar del cliente |
