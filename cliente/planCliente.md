# `planCliente.md` — Plan de implementación del cliente

> Proyecto: Mensajería Académica Unillanos · cliente de escritorio.
> Base: `docs/plan_construccion.md` fases 9–12, `docs/arquitectura/cliente_clases_componentes.md`
> (en adelante **«cliente §N»**), `docs/mockups/mockups_cliente.md` y
> `docs/arquitectura/servidor_clases_componentes.md` §7 (**«servidor §7»**).
> **Stack: Java 21 + JavaFX 21 (LTS) + FXML + MVVM + Maven multimódulo + H2.**
> Estado: **en ejecución**. D-03…D-14 aprobadas por el usuario (25-oct-2026) con las recomendaciones de §0. Sesión 0 hecha.
> Fases 9a–11c terminadas (137 pruebas). **Integrada** la actualización del servidor (fases 7c–8, `origin/develop` 9f19799):
> el contrato §7 no cambió y no hubo que tocar el cliente (`warning.md` §0.2).
> El servidor **no se modifica**. Los riesgos citados (R-xx) están en `cliente/warning.md`.

---

## 0. Decisiones del plan

Las decisiones D-03 a D-14 son **desviaciones del catálogo** o puntos que el catálogo no resuelve.
Cada una, una vez aprobada, se refleja **primero** en `cliente_clases_componentes.md` y su `.drawio`
(plan §5) y después se codifica en la subfase indicada.

| ID | Decisión | Recomendación (revisión 2) | Subfase | Riesgo |
|---|---|---|---|---|
| D-01 | Stack | **JavaFX 21 + FXML + MVVM** | — | R-01 (cerrado) |
| D-02 | Vista web / gateway | **Descartada** | — | R-02 (cerrado) |
| D-03 | `ConfiguracionCliente.protocolo()` | Devuelve `String`; `AppCliente` hace `TipoProtocolo.valueOf` (igual que el servidor) | 9a | R-10 |
| D-04 | Conversión de `JsonNode` (`RespuestaDTO.datos`, `EventoPushDTO.datos`) | Inyectar `ISerializador` en `ServicioUsuarios`, `ServicioChat`, `ServicioInformes` y `FachadaCliente`; `fachada-cliente` pasa a depender de `utilerias-cliente` | 10b, 10c | R-11 |
| D-05 | Usuario y sesión para los ViewModels | `usuarioActual() : Optional<UsuarioDTO>` y `sesionLocal() : long` en `IFachadaCliente` | 10c | R-12 |
| D-06 | Reconexión tras `LOGOUT`/`CUPO_LLENO`/`SESION_CERRADA` | `ConexionTCP` admite `conectar(...)` de nuevo tras `cerrar()`; la fachada solo reconecta si `!estaConectada()`. **Sin cambios de firma** | 9b, 10c | R-13 |
| D-07 | Esquema H2 de imágenes | **Sin cambios** al §8: la UI usa `idMensaje` + `TipoFiltro.values()` | 10a | R-17 |
| D-08 | «Ver JSON» con la respuesta tal como llegó | `ServicioInformes` conserva el árbol `datos` y devuelve el record `InformeRecibido(InformeDTO informe, String json)` (paquete `…servicios.informes`), que la fachada reenvía en `informe(...)` | 10b, 10c | R-15 |
| D-09 | ¿Login como `ADMIN01` desde el cliente? | Permitirlo (el servidor no lo impide) | 11b | R-42 |
| D-10 | Hilo de UI en los ViewModels | Los ViewModels reciben `Executor hiloUI` (`Platform::runLater` / `Runnable::run` en pruebas) | 11a | R-23 |
| D-11 | Refresco del historial | `historial()` sigue síncrono (local); el `HISTORIAL` del servidor llega por `onMensaje` | 10b, 10c | R-21 |
| D-12 | «{n} mensajes guardados» | `contar() : long` en `IRepositorioMensajesLocal` y `mensajesGuardados() : long` en `IFachadaCliente` | 10a, 10c | R-22 |
| D-13 | Versiones | JavaFX **21.0.13**, H2 **2.5.252** (últimas estables); Jackson **2.19.2** (obligatorio por `dto-records`); el resto alineado con el servidor | 9a | R-04, R-61 |
| D-14 | `Launcher` / jar ejecutable | Solo si el usuario lo pide; por defecto `mvn javafx:run` | 11a | R-24 |

`logout()` se mantiene `void` (catálogo) con espera acotada (R-14); no es una desviación.

Surgidas al implementar (aplicadas; pendientes de reflejar en `docs/arquitectura/cliente_clases_componentes.md` y su `.drawio`):

| ID | Decisión | Motivo |
|---|---|---|
| D-15 | `IServicioUsuarios.login(codigo, clave, puerto)` | `SESION.PUERTO` es obligatorio y la respuesta de LOGIN solo trae `ipServidor`; la fachada conoce el puerto desde `conectar` |
| D-16 | `ExcepcionServidor(codigo, mensaje)` en `utilerias-cliente`, con `exigirExito(r)` | La UI necesita el `CodigoError` para elegir el texto (C1, envío, informes); es transversal porque la lanzan los servicios y la ve la presentación |
| D-17 | `IServicioChat.sincronizar(conv) : CompletableFuture<List<MensajeDTO>>` (devuelve solo los nuevos) y `cerrarSesion()` | Concreta D-11 sin que un servicio llame a los observadores; tras el logout no se escribe en una base cerrada |
| D-18 | `CacheMensajesSesion.cargar(conv, lista)`, `contiene(conv)` y `limpiar()`; `agregar` solo añade a conversaciones cargadas | Un mensaje suelto en la caché ocultaría el historial de H2 |
| D-19 | `IFachadaCliente.getPuerto() : int` (fase 11c) | La barra de C2 muestra «TCP :{puerto}» y ningún ViewModel conocía el puerto |

Otras adiciones menores al catálogo, también pendientes de `docs/` (detalle en §12.1): `DespachadorEventos.suscribir(o)`,
`IRepositorioMensajesLocal.FORO` / `claveConversacion(...)`, `IServicioChat.mensajesGuardados()`, `TipoVista` con FXML
y tamaño, `FabricaVistas.registrar(...)` / `titulo(t)` y los artefactos `…-cliente` de `comunicacion/` (R-25).

---

## 1. Objetivo y alcance

1. **Fase 9** — proyecto Maven independiente, utilerías, transporte TCP y protocolo JSON.
2. **Fase 10** — persistencia local H2, servicios (usuarios, chat, informes) y fachada.
3. **Fase 11** — interfaz JavaFX (C1 login, C2 chat, C3 informes) con MVVM.
4. **Fase 12** — integración con el servidor real, dos clientes, informes y H2; README.

**Fuera de alcance:** cambios en el servidor o en el protocolo, TLS, gateway web, cifrado de H2.

---

## 2. Arquitectura modular

### 2.1 Capas y sentido de las dependencias

```
┌───────────────────────── app-cliente (Presentación · único que conoce JavaFX) ─────────────────────────┐
│  inicio:     AppCliente (Composition Root) · FabricaVistas · NavegadorVistas · TipoVista               │
│  vistas:     VistaLogin · VistaChat · VistaInformes      (FXML Controllers: solo binding + acciones)   │
│  viewmodels: LoginViewModel · ChatViewModel · InformesViewModel   (estado observable)                 │
└──────────────────────────────────────────────┬──────────────────────────────────────────────────────────┘
                                               │ solo IFachadaCliente / IObservadorCliente
┌──────────────────────────── fachada-cliente ─▼──────────────────────────────────────────────────────────┐
│  FachadaCliente (IFachadaCliente · IOyentePush): orquesta servicios y reparte push a observadores      │
└───────────────┬───────────────────────────────┬──────────────────────────────┬──────────────────────────┘
                │ IServicioUsuarios             │ IServicioChat                │ IServicioInformes
┌───────────────▼─────────────── servicios-cliente ─────────────────────────────▼──────────────────────────┐
│  ServicioUsuarios           ServicioChat + CacheMensajesSesion              ServicioInformes             │
└───────┬────────────────────────────┬──────────────────────────────────────────┬─────────────────────────┘
        │ IProtocoloCliente          │ IRepositorio* / IProveedorConexionBD     │
┌───────▼──── protocolo-cliente-servidor ───┐   ┌──────── persistencia-local ───▼─────────┐
│ ClienteProtocoloJSON · DespachadorEventos │   │ ProveedorConexionH2 · Repositorio*H2    │──JDBC──► H2
└───────┬───────────────────────────────────┘   └─────────────────────────────────────────┘
        │ IConexionServidor / IReceptorServidor
┌───────▼──── protocolo-comunicacion ───────┐
│ ConexionTCP · CodificadorTramas           │──TCP [int32][UTF-8]──► SERVIDOR :5000
└───────────────────────────────────────────┘
Transversales: dto-records (del servidor) · utilerias-cliente (ConfiguracionCliente, ISerializador, SerializadorJSON)
```

### 2.2 Qué conoce cada módulo (y qué no)

| Módulo | Conoce | **No** conoce |
|---|---|---|
| `protocolo-comunicacion` | `java.net`, hilos, tramas | JSON, DTO, H2, JavaFX |
| `protocolo-cliente-servidor` | JSON del protocolo, correlación `idPeticion`, timeouts, push | Sockets, H2, JavaFX |
| `persistencia-local` | `java.sql`, H2, `schema.sql` | Red, JSON del protocolo, JavaFX |
| `servicios-cliente` | Casos de uso; DTO; `ISerializador` para leer `datos` (D-04) | Sockets, JDBC concreto, JavaFX |
| `fachada-cliente` | Orquestación y reparto de push | Clases concretas de servicios, JavaFX |
| `app-cliente` | JavaFX, FXML, CSS y **todas** las clases concretas (solo en `AppCliente`) | — |

### 2.3 Patrones aplicados

| Patrón | Dónde |
|---|---|
| Composition Root + inyección por constructor | `AppCliente.ensamblar(cfg)` |
| Fachada | `IFachadaCliente` / `FachadaCliente` |
| Observer | `IOyentePush` → `DespachadorEventos`; `IObservadorCliente` → ViewModels |
| Factory | `FabricaConexiones`, `FabricaProtocoloCliente`, `FabricaRepositoriosH2`, `FabricaVistas` |
| MVVM | FXML ↔ Controller (binding) ↔ ViewModel ↔ Fachada |
| Strategy de transporte | `IConexionServidor` + `TipoProtocolo` (cliente §9) |
| Cache L1 LRU | `CacheMensajesSesion` (`LinkedHashMap` con `accessOrder=true`) |

---

## 3. Stack y versiones (verificadas en Maven Central el 25-oct-2026)

| Componente | Versión | Nota |
|---|---|---|
| Java | **21** (`maven.compiler.release=21`) | Ejecutar con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` (el JDK por defecto es el 27) |
| Maven | 3.9+ (3.10.0 instalado) | Proyecto independiente de `servidor/` |
| JavaFX | **21.0.13** (`javafx-controls`, `javafx-fxml`) | Última de la línea LTS 21. La revisión 1 decía 21.0.7, que existe pero no es la última. JavaFX ≥ 24 exige un JDK superior |
| `javafx-maven-plugin` | **0.0.8** | Última publicada |
| Jackson | **2.19.2** (`databind`, `datatype-jsr310`) | **Obligatorio**: `dto-records` trae `jackson-databind` 2.19.2 de forma transitiva |
| H2 | **2.5.252** | Última estable. Si alguna prueba de `MERGE … KEY` falla, volver a 2.3.232 y documentarlo |
| SLF4J | 2.0.17 | Igual que el servidor |
| Logback | 1.5.18 (`runtime`) | Igual que el servidor |
| JUnit Jupiter | 5.10.2 | Igual que el servidor (la última 5.x es 5.14.4: se alinea por coherencia del repositorio) |
| Surefire | 3.5.4 | Igual que el servidor |
| `dto-records` | `com.universidad:dto-records:1.0.0` | Instalado en `~/.m2` (verificado) |

> El cliente **no** hereda del POM del servidor: declara sus versiones en `cliente/pom.xml`
> (`dependencyManagement`), como pide el plan.

---

## 4. Estructura del proyecto

```
cliente/
├── pom.xml                                  # padre com.universidad:mensajeria-cliente:1.0.0
├── README.md · warning.md · planCliente.md
├── config/cliente.properties
├── transversales/utilerias-cliente/
│   └── …/cliente/utilerias/ ConfiguracionCliente · ISerializador · SerializadorJSON
├── comunicacion/
│   ├── protocolo-comunicacion/
│   │   └── …/cliente/comunicacion/ IConexionServidor · IReceptorServidor · FabricaConexiones · TipoProtocolo
│   │       └── tcp/ ConexionTCP · CodificadorTramas
│   └── protocolo-cliente-servidor/
│       └── …/cliente/protocolo/ IProtocoloCliente · IOyentePush · ClienteProtocoloJSON
│                                DespachadorEventos · FabricaProtocoloCliente
├── persistencia/persistencia-local/
│   ├── …/cliente/persistencia/ IProveedorConexionBD · ProveedorConexionH2 · IRepositorioSesion
│   │                           RepositorioSesionH2 · IRepositorioMensajesLocal · RepositorioMensajesH2
│   │                           FabricaRepositoriosH2
│   └── src/main/resources/schema.sql
├── servicios/
│   ├── servicios-cliente/
│   │   └── …/cliente/servicios/ usuarios/(IServicioUsuarios, ServicioUsuarios)
│   │                            chat/(IServicioChat, ServicioChat, CacheMensajesSesion)
│   │                            informes/(IServicioInformes, ServicioInformes, InformeRecibido)
│   └── fachada-cliente/
│       └── …/cliente/fachada/ IFachadaCliente · IObservadorCliente · FachadaCliente
└── presentacion/app-cliente/
    ├── …/cliente/inicio/      AppCliente · FabricaVistas · NavegadorVistas · TipoVista  [Launcher solo con D-14]
    ├── …/cliente/vistas/      VistaLogin · VistaChat · VistaInformes
    ├── …/cliente/viewmodels/  LoginViewModel · ChatViewModel · InformesViewModel
    └── src/main/resources/    fxml/(login, chat, informes).fxml · css/app.css · logback.xml
```

Paquete base: `com.universidad.mensajeria.cliente`. Los artefactos de `comunicacion/` se llaman `protocolo-comunicacion-cliente` y `protocolo-cliente-servidor-cliente` para no sobrescribir los del servidor en `~/.m2` (R-25). Lo que aparece entre corchetes solo se crea si
se aprueba la decisión correspondiente; cualquier otro archivo se consulta antes (plan §1).

---

## 5. Matriz de dependencias

| Módulo | Puede depender de |
|---|---|
| `utilerias-cliente` | `dto-records`, `jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api` |
| `protocolo-comunicacion` | `utilerias-cliente`, `slf4j-api` (**sin** Jackson ni DTO) |
| `protocolo-cliente-servidor` | `protocolo-comunicacion` (solo interfaces), `dto-records`, `utilerias-cliente`, `slf4j-api` |
| `persistencia-local` | `dto-records`, `utilerias-cliente`, `h2` (`runtime`), `slf4j-api` |
| `servicios-cliente` | `protocolo-cliente-servidor` (solo `IProtocoloCliente`/`IOyentePush`), `persistencia-local` (solo interfaces), `dto-records`, `utilerias-cliente` |
| `fachada-cliente` | `servicios-cliente` (interfaces), `protocolo-cliente-servidor` (interfaces), `dto-records`, **`utilerias-cliente` (D-04)** |
| `app-cliente` | todos (Composition Root), `javafx-controls`, `javafx-fxml`, `logback-classic` (`runtime`) |

**Comprobaciones (grep) al cerrar cada subfase:**

- `java.net` solo en `protocolo-comunicacion`; `java.sql` solo en `persistencia-local`; `javafx.` solo en `app-cliente`.
- `new ConexionTCP|new ClienteProtocoloJSON|new ServicioChat|new FachadaCliente|new ProveedorConexionH2` solo en `AppCliente` (y en pruebas).
- Ningún ViewModel importa `…cliente.servicios`, `…cliente.protocolo` ni `…cliente.persistencia`.

---

## 6. Consumo del protocolo (la «API» del servidor)

### 6.1 Comandos (cliente → servidor)

Todos los `parametros` son `Map<String,String>` (también `limite`). Los nulos se omiten.

| Comando | Sesión | `parametros` | `contenido` | `datos` de la respuesta | Errores que la UI traduce |
|---|---|---|---|---|---|
| `LOGIN` | no | `codigo`, `clave` | — | `usuario`, `idSesion`, `ipServidor` | `CREDENCIALES_INVALIDAS`, `CUPO_LLENO` (cierra el canal) |
| `LOGOUT` | sí | — | — | — | (cierra el canal) |
| `LISTAR_USUARIOS` | sí | — | — | `usuarios: EstadoUsuarioDTO[]` | — |
| `ENVIAR_FORO` | sí | — | obligatorio | `idMensaje`, `estado:"ENCOLADO"` | `CONTENIDO_INVALIDO`, `COLA_LLENA` |
| `ENVIAR_PRIVADO` | sí | `destino` | obligatorio | `idMensaje`, `estado:"ENCOLADO"` | `DESTINO_INEXISTENTE`, `CONTENIDO_INVALIDO`, `COLA_LLENA` |
| `HISTORIAL` | sí | `conversacion` (`FORO` o código), `limite?` | — | `mensajes: MensajeDTO[]` (≤ 200) | `DESTINO_INEXISTENTE` |
| `OBTENER_IMAGEN` | sí | `idMensaje`, `etapa` | — | `idMensaje`, `etapa`, `nombreArchivo`, `datosBase64` | `NO_ENCONTRADO` |
| `INFORME` | sí | `tipo`, `tipoEvento?`, `usuario?`, `desde?`, `hasta?` | — | `InformeDTO` | `PETICION_INVALIDA` |

Cualquier comando puede responder `PETICION_INVALIDA`, `NO_AUTENTICADO` o `ERROR_INTERNO`:
la UI muestra el `mensaje` del servidor.

### 6.2 Eventos push (servidor → cliente)

| Evento | Cuándo | `datos` | Qué hace el cliente |
|---|---|---|---|
| `INFO_SERVIDOR` | al conectar | `ipServidor`, `puerto`, `protocolo`, `cuposLibres` | Completa `conectar()` con la IP (R-18) |
| `MENSAJE_NUEVO` | tras procesar un mensaje | `mensaje: MensajeDTO` | H2 + caché + `onMensaje` (también para los propios) |
| `USUARIO_CONECTADO` | login de alguien (también el propio, **antes** de su respuesta) | `codigo`, `nombreCompleto`, `sesiones` | `LISTAR_USUARIOS` → `onUsuarios` (solo con sesión, R-30) |
| `USUARIO_DESCONECTADO` | fin de una sesión | `codigo`, `sesiones` | Igual que el anterior |
| `SESION_CERRADA` | admin o apagado | `motivo`: `CERRADA_POR_ADMIN` · `SERVIDOR_DETENIDO` | `onDesconexion(motivo)` una sola vez (R-49) → C1 |
| `CUPO_LLENO` | reservado, no se envía en v1 | — | Se ignora |

### 6.3 Reglas de lectura de tramas (`ClienteProtocoloJSON.alRecibir`)

1. Leer el JSON como `JsonNode`. Si está mal formado → log `WARN` y seguir (no cerrar).
2. Si tiene `evento` → `EventoPushDTO` → `DespachadorEventos.despachar` (y `INFO_SERVIDOR` completa el futuro de `conectar`).
3. Si no, es respuesta → `RespuestaDTO`; buscar `pendientes.remove(idPeticion)`. Si `idPeticion` es `null` o no está pendiente → log `WARN` y descartar (R-36).
4. Ninguna operación bloqueante larga en el hilo lector (R-39): las escrituras H2 de un push son cortas; las conversiones de imagen se hacen en el ejecutor de fondo.

### 6.4 Tramas y JSON

- Trama `[int32 big-endian = N][N bytes UTF-8]`, `1 ≤ N ≤ servidor.maxTramaBytes` (16 MiB). Fuera de rango → cerrar.
- Fechas ISO-8601 **sin zona**, hasta 9 decimales (R-35). Enums por nombre. Nulos omitidos.
- `SerializadorJSON` igual al del servidor: `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS=false`,
  `FAIL_ON_UNKNOWN_PROPERTIES=false`, `NON_NULL`.

### 6.5 Mapa UI → ViewModel → fachada → servicio → comando

| Acción de UI | ViewModel | `IFachadaCliente` | Servicio | Comando |
|---|---|---|---|---|
| Ingresar | `LoginViewModel.ingresar` | `conectar` + `login` | `ServicioUsuarios.login` | `LOGIN` |
| Entrar a C2 | `ChatViewModel.cargar` | `usuarios`, `historial("FORO")` | `listar`, `historial` | `LISTAR_USUARIOS`, `HISTORIAL` |
| Enviar texto | `enviarForo` / `enviarPrivado` | `enviarForo` / `enviarPrivado` | `ServicioChat.enviar` | `ENVIAR_FORO` / `ENVIAR_PRIVADO` |
| Enviar imagen | `enviarImagen(destino, f)` | igual que el texto, con `ContenidoDTO(IMAGEN, …)` | `ServicioChat.enviar` | igual |
| Abrir privado | `abrirPrivado(codigo)` | `historial(codigo)` | `ServicioChat.historial` | `HISTORIAL` |
| Miniatura / 5 filtros | `imagen(m, etapa)` | `imagen(id, etapa)` | `ServicioChat.imagen` | `OBTENER_IMAGEN` (si no está en H2) |
| Informes | `InformesViewModel.cargar` | `informe(t, filtros)` | `ServicioInformes.obtener` | `INFORME` |
| Salir | `ChatViewModel.salir` | `logout` | `ServicioUsuarios.logout` | `LOGOUT` |

---

## 7. Configuración y recursos

### 7.1 `config/cliente.properties` (claves de cliente §7)

```properties
servidor.host=127.0.0.1
servidor.puerto=5000
servidor.protocolo=TCP
servidor.maxTramaBytes=16777216
peticion.timeoutSegundos=15
h2.url=jdbc:h2:./data/cliente_{codigo}
h2.usuario=sa
h2.clave=
cache.mensajes.max=500
```

- `ConfiguracionCliente.cargar(Path)` falla con la lista de claves faltantes y valida los números
  (mismo estilo que `ConfiguracionServidor`). `h2.clave` puede estar vacía.
- Para dos instancias del mismo usuario en una máquina se puede añadir `;AUTO_SERVER=TRUE` a `h2.url` (R-57).
- El cliente se ejecuta **desde `cliente/`**. `data/` y `logs/` no se versionan (`*.mv.db`, `logs/` ya están en `.gitignore`).

### 7.2 Constantes del cliente (no van en `cliente.properties`)

Reflejan los valores por defecto del servidor, que no viajan por el protocolo (R-50):
texto ≤ 4000 *code points*; imagen ≤ 5 242 880 bytes con extensión png/jpg/jpeg/gif/bmp. Si el
servidor responde `CONTENIDO_INVALIDO`, se muestra su `mensaje`.

### 7.3 `schema.sql`

Copia literal de cliente §8 (D-07: sin columnas nuevas). Si se aprueba D-12, `contar()` es un
`SELECT COUNT(*) FROM MENSAJE` y no cambia el esquema.

### 7.4 `logback.xml`

`ConsoleAppender` + archivo `logs/cliente.log`; nivel `INFO`; nunca se registran claves ni el Base64 de las imágenes.

---

## 8. Fases de implementación (una subfase por sesión)

Cada subfase termina con `mvn -q verify` en verde desde `cliente/` (con JDK 21), las comprobaciones
de §5 y un resumen de lo hecho y de lo pendiente.

### Fase 9a · Esqueleto Maven + `utilerias-cliente`

- **Archivos:** `cliente/pom.xml` (padre, `dependencyManagement`, 7 módulos), los 7 `pom.xml` hijos
  (los módulos sin código aún compilan vacíos), `ConfiguracionCliente`, `ISerializador`,
  `SerializadorJSON`, `config/cliente.properties`.
- **Tareas:**
  1. Versiones de §3 en `dependencyManagement`; `maven.compiler.release=21`.
  2. `ConfiguracionCliente`: todas las claves de §7.1; `protocolo()` devuelve `String` (**D-03**).
  3. `ISerializador` **con la firma del catálogo**: `aJson(Object)`, `desdeJson(String, Class<T>)`,
     `convertir(JsonNode, TypeReference<T>)` (R-20).
  4. `SerializadorJSON` con la configuración de §6.4.
- **Pruebas:** clave faltante → excepción con la lista; ida y vuelta de `MensajeDTO`, `UsuarioDTO`,
  `EventoPushDTO` y `RespuestaDTO`; fechas con 3 y 9 decimales; `convertir` a `List<EstadoUsuarioDTO>`.

### Fase 9b · `protocolo-comunicacion` (TCP)

- **Archivos:** `IConexionServidor`, `IReceptorServidor`, `FabricaConexiones`, `TipoProtocolo`, `tcp/CodificadorTramas`, `tcp/ConexionTCP`.
- **Tareas:**
  1. `CodificadorTramas`: `[int32][bytes]`, `readFully`, `1..maxTramaBytes` (igual que el del servidor).
  2. `ConexionTCP`: `conectar` crea socket, cola acotada, **un** hilo lector y **un** hilo escritor;
     `enviar()` solo encola (`offer`; si la cola está llena → `IllegalStateException`);
     `alDesconectar` **una sola vez por conexión**; `cerrar()` vacía la cola de salida antes de cerrar.
  3. **D-06:** tras `cerrar()` (o caída), un nuevo `conectar(...)` abre una conexión nueva con hilos
     nuevos; llamar a `conectar` estando conectada → `IllegalStateException`.
  4. `FabricaConexiones.crear(TipoProtocolo, maxTramaBytes)` con `case TCP`.
- **Pruebas** (`ServerSocket` en el puerto 0): enviar y recibir tramas; trama inválida → cierre y
  `alDesconectar`; `alDesconectar` exactamente una vez; reconectar después de `cerrar()`; `getIpServidor()`.

### Fase 9c · `protocolo-cliente-servidor` (JSON)

- **Archivos:** `IProtocoloCliente`, `IOyentePush`, `ClienteProtocoloJSON`, `DespachadorEventos`, `FabricaProtocoloCliente`.
- **Tareas:**
  1. `solicitar(c, params, contenido)`: UUID → `pendientes.put` → `conexion.enviar(json)` →
     `orTimeout(timeout)`; al vencer, se quita de `pendientes`.
  2. `alRecibir(json)`: reglas de §6.3.
  3. `conectar(host, puerto)`: espera `INFO_SERVIDOR` (timeout) y devuelve `ipServidor`; si no llega, `conexion.getIpServidor()` (R-18).
  4. `alDesconectar(motivo)`: completa **todos** los pendientes con excepción y llama a `despachador.desconexion(motivo)`.
  5. `desconectar()`: `conexion.cerrar()`.
- **Pruebas** (servidor falso en el puerto 0 que habla el contrato): dos respuestas fuera de orden;
  timeout; push frente a respuesta; respuesta con `idPeticion:null` descartada; `INFO_SERVIDOR`
  antes del login; desconexión que falla los pendientes.

### Fase 10a · `persistencia-local` (H2)

- **Archivos:** `IProveedorConexionBD`, `ProveedorConexionH2`, `IRepositorioSesion`, `RepositorioSesionH2`,
  `IRepositorioMensajesLocal`, `RepositorioMensajesH2`, `FabricaRepositoriosH2`, `schema.sql`.
- **Tareas:**
  1. `abrir(codigo)`: valida `[A-Za-z0-9_-]{1,20}` (R-52), reemplaza `{codigo}`, cierra la BD anterior y ejecuta `schema.sql`.
  2. `RepositorioSesionH2.abrir/cerrar` (`INICIO`/`FIN`).
  3. `RepositorioMensajesH2`: `guardar` con `MERGE … KEY(ID)` conservando el primer `SESION_ID` (R-54);
     `conversacion` = `'FORO'` o `menor|mayor`, ordenada por `FECHA, ID`; `guardarAdjunto` (único por
     `MENSAJE_ID, ETAPA`); `adjunto`; **D-12:** `contar()`.
  4. Lectura de `MensajeDTO` desde H2 con `imagenes = []` (D-07).
- **Pruebas** (`jdbc:h2:mem:`): un mensaje guardado dos veces → una fila y el mismo `SESION_ID`;
  adjunto ORIGINAL y filtros; FK (adjunto sin mensaje falla); orden; código inválido rechazado.

### Fase 10b · `servicios-cliente`

- **Archivos:** `usuarios/IServicioUsuarios`, `usuarios/ServicioUsuarios`, `chat/IServicioChat`,
  `chat/ServicioChat`, `chat/CacheMensajesSesion`, `informes/IServicioInformes`, `informes/ServicioInformes`, `informes/InformeRecibido`.
- **Tareas:**
  1. Todos reciben `ISerializador` (**D-04**) para leer `RespuestaDTO.datos`.
  2. `ServicioUsuarios`: `login` → `LOGIN`; si `exito`: `proveedor.abrir(codigo)` → `sesiones.abrir(u, ip, puerto)`;
     si falla: `CompletableFuture` excepcional con el `CodigoError` (una excepción del propio módulo,
     p. ej. clase anidada pública `ServicioUsuarios.ErrorServidor`, o `CompletionException` con el código en el mensaje: **a elegir en la sesión**);
     `logout` con espera acotada (R-14); `listar`; `actual`; `sesionLocal`.
  3. `ServicioChat`:
     - `iniciarSesion` vacía el búfer de mensajes que llegaron antes de abrir H2 (R-30);
     - `enviar`: `ENVIAR_FORO`/`ENVIAR_PRIVADO`; para IMAGEN guarda los bytes originales pendientes por `idMensaje` (R-48);
     - `recibir(m)`: H2 (`propio = remitente == yo`) + caché; si hay bytes pendientes de ese id → `ADJUNTO ORIGINAL`;
     - `historial(conv)`: caché → H2 (devuelve ya) y lanza `HISTORIAL` en segundo plano (**D-11**);
     - `imagen(id, etapa)`: H2 → si no, `OBTENER_IMAGEN` → `guardarAdjunto` → bytes.
  4. `CacheMensajesSesion`: LRU de `cache.mensajes.max` conversaciones, sin duplicados por `id`.
  5. `ServicioInformes.obtener`: `INFORME`; conserva el JSON de `datos` (**D-08**).
- **Pruebas** (protocolo falso + H2 en memoria): login correcto, credenciales, cupo; mensaje dos veces → una fila;
  historial local primero y luego remoto; imagen de H2 sin red; respuesta y push en ambos órdenes (R-48).

### Fase 10c · `fachada-cliente`

- **Archivos:** `IFachadaCliente`, `IObservadorCliente`, `FachadaCliente`.
- **Tareas:**
  1. Métodos del catálogo + **D-05** (`usuarioActual`, `sesionLocal`) + **D-12** (`mensajesGuardados`).
  2. `conectar`: si `!estaConectada()` → `protocolo.conectar` (**D-06**); guarda `ipServidor` y `puerto`.
  3. `onPush`: tabla §6.2. Los push de usuarios sin sesión se ignoran (R-30).
  4. `onDesconexion` idempotente por sesión (R-49); si el cierre era esperado (LOGOUT, CUPO_LLENO), no se notifica como caída (R-31).
  5. Los mensajes que llegan del refresco de `historial` se notifican con `onMensaje` (**D-11**).
- **Pruebas:** push de mensaje → H2 + observador; push de usuario → `LISTAR_USUARIOS`; `SESION_CERRADA`
  + cierre de socket → un solo `onDesconexion`; logout no reporta caída; segundo login tras logout reconecta.

### Fase 11a · `app-cliente`: Composition Root y navegación

- **Archivos:** `AppCliente`, `FabricaVistas`, `NavegadorVistas`, `TipoVista`, `logback.xml`, `css/app.css` (base).
- **Tareas:**
  1. `AppCliente.ensamblar(cfg)` exactamente como cliente §5.1, con D-03/D-04 aplicadas.
  2. `FabricaVistas.crear(t)`: `FXMLLoader` con *controller factory* que entrega a cada controlador su
     ViewModel construido con la fachada y el `Executor hiloUI` (**D-10**).
  3. `NavegadorVistas.ir(t)`: una sola `Stage`; INFORMES puede abrirse en una ventana secundaria (mockup).
  4. Un `ExecutorService` de fondo (hilos *daemon*) para red y H2, creado en `AppCliente` e inyectado en los ViewModels junto a `hiloUI`.
  5. `stop()`: `fachada.logout()` si hay sesión, cierra el ejecutor; idempotente y ≤ 3 s.
- **Pruebas:** `ConfiguracionCliente` faltante → error claro. **Aceptación:** `mvn -q -pl presentacion/app-cliente javafx:run` abre C1.

### Fase 11b · C1 · Login

- **Archivos:** `fxml/login.fxml`, `VistaLogin`, `LoginViewModel`.
- **Tareas:** §9.1 y §10.1. Textos de error: `CREDENCIALES_INVALIDAS` → «Código o contraseña incorrectos.»;
  `CUPO_LLENO` → «⚠ El servidor alcanzó el cupo de sesiones. Intenta más tarde.» (R-16); red o timeout →
  «No fue posible conectar con {host}:{puerto}.»; `host:puerto` mal escrito → «Servidor inválido (host:puerto).»
- **Pruebas:** ViewModel con fachada falsa: éxito, credenciales, cupo, red, formato de servidor, `ocupado`.

### Fase 11c · C2 · Chat

- **Archivos:** `fxml/chat.fxml`, `VistaChat` (con celdas como clases privadas anidadas, R-24), `ChatViewModel`.
- **Tareas:** §9.2 y §10.2.
- **Pruebas:** ViewModel con fachada falsa: orden de usuarios, filtro de búsqueda, mensaje nuevo en
  foro y en privado, deduplicación, «enviando…» con timeout, desconexión, no abrir privado propio.

### Fase 11d · C3 · Informes

- **Archivos:** `fxml/informes.fxml`, `VistaInformes`, `InformesViewModel`.
- **Tareas:** §9.3 y §10.3.
- **Pruebas:** ViewModel con fachada falsa: 4 tipos, filtros → parámetros, paginación (0, 50, 51, 120 filas), JSON con sangría, resumen.

### Fase 11e · Estilos y pulido

`app.css` con la paleta del mockup (`#FAFAFA`, `#37474F`, `#1565C0`, `#C62828` sobre `#FFEBEE`,
propios `#E3F2FD`); estados vacíos, foco inicial, Enter para enviar, tamaños mínimos (C1 420×560),
textos en español. Prueba manual contra los mockups, sin excepciones en consola.

### Fase 12 · Integración final

1. Servidor + dos clientes: login, foro, privado, imagen y sus 6 versiones, informes, cierre por el
   ADMIN, cupo lleno (`servidor.maxSesiones` bajo en una instancia de prueba) y apagado del servidor.
2. H2: `SESION`, `MENSAJE`, `ADJUNTO`; una fila por `id`.
3. `README.md` raíz y `cliente/README.md`: requisitos, instalación de `dto-records`, ejecución y pruebas.
4. `mvn -q verify` en `servidor/` y `cliente/`.

---

## 9. Componentes de UI por pantalla

### 9.1 C1 · `login.fxml` (420 × 560, `Mensajería Académica — Iniciar sesión`)

```
VBox.login
├── Label 💬 · Label «Mensajería Académica Unillanos» · Label «Ingresa con tu código institucional»
├── TextField txtCodigo        ⇄ vm.codigo
├── PasswordField txtClave     ⇄ vm.clave
├── HBox «Servidor (cliente.properties)»
│   ├── TextField txtServidor  ⇄ vm.servidor        (precargado host:puerto)
│   └── ComboBox cmbProtocolo  = TipoProtocolo.values(), disable = true
├── Button Ingresar (primario) disable ⇐ codigo vacío ∨ clave vacía ∨ ocupado
└── Label lblError (rojo)      ⇐ vm.error; visible/managed ⇐ error no vacío
```

### 9.2 C2 · `chat.fxml` (`Mensajería Académica — {nombres} {apellidos}`)

```
BorderPane
├── top:    HBox cabecera  [avatar iniciales] [Nombre · código / programa] ··· [📊 Informes] [Salir (peligro)]
├── left:   VBox (≈260 px)
│           ├── Label «Usuarios (k conectados)»          ⇐ vm.conectados
│           ├── TextField 🔍 Buscar                       ⇄ vm.filtro
│           ├── ListView<EstadoUsuarioDTO> lstUsuarios    ⇐ vm.usuariosVisibles (FilteredList + SortedList)
│           │      celda: punto (ámbar ADMIN / verde / gris) + nombre en negrita + programa
│           └── Label «Doble clic: abrir conversación privada»
├── center: TabPane tabs
│           ├── Tab «# Foro» (no cerrable) → ListView<MensajeDTO> ⇐ vm.foro
│           └── Tab «Nombre ✕» por privado  → ListView<MensajeDTO> ⇐ vm.privados[codigo]
│                  celda burbuja: otros izquierda/blanco «Nombre · HH:mm»; propios derecha/#E3F2FD «Tú · HH:mm»
│                  imagen: ImageView miniatura ORIGINAL + Button «Ver 5 filtros» → diálogo con 6 ImageView en orden
├── bottom: VBox
│           ├── HBox escritura: TextField txtMensaje (prompt según pestaña) · Button 📎 Imagen · Button Enviar ➤
│           │                    Label «enviando…» ⇐ vm.enviando
│           └── HBox estado: ● «Estoy conectado a: {ip} · TCP :{puerto}»  ···  «Sesión local H2 #{n} · {m} mensajes guardados»
```

### 9.3 C3 · `informes.fxml` (`Mensajería Académica — Informes del servidor`)

```
BorderPane
├── top:    HBox tarjetas resumen: sesiones activas · usuarios conectados · mensajes de hoy · errores
├── center: TabPane (Usuarios registrados | Usuarios conectados | Mensajes | Logs)
│           ├── HBox filtros (solo Logs): ComboBox Tipo (Todos + TipoEvento) · TextField Usuario · DatePicker Desde/Hasta
│           │                              · Button Filtrar · Button { } Ver JSON
│           └── TableView<Map<String,Object>> tblDatos  (columnas por informe, MapValueFactory)
└── bottom: HBox «Fuente: comando INFORME → JSON del servidor · {desde}-{hasta} de {total} registros» · ◀ Anterior · Siguiente ▶
```

Columnas por informe (las mismas que el servidor, mockup S3; corregido en la revisión 3, R-27):
**Usuarios registrados** → Código, Nombres, Apellidos, Programa, Rol; **Usuarios conectados** → Código,
Nombre, Cantidad de sesiones; **Mensajes** → Hora, Tipo, Canal, Remitente, Destino (`todos` si es FORO),
Hash (abreviado); **Logs** → Hora, Tipo (CUPO_LLENO ámbar, ERROR rojo), Usuario, IP, Descripción.
Las filas llegan como `Map<String,Object>` (R-19): cada columna lee su clave (`codigo`, `nombreCompleto`,
`cantidadSesiones`, `fecha`, `remitente`, `destinatario`, `hash`, `tipo`, `usuario`, `ip`, `descripcion`…).

---

## 10. Manejo de estado (MVVM)

### 10.1 `LoginViewModel`

| Estado | Tipo | Regla |
|---|---|---|
| `codigo`, `clave`, `servidor` | `StringProperty` | `servidor` se precarga con `host:puerto` de la configuración |
| `error` | `StringProperty` | Vacío = oculto |
| `ocupado` | `BooleanProperty` | `true` durante `ingresar()` |
| `puedeIngresar` | `BooleanBinding` | `!codigo.isEmpty && !clave.isEmpty && !ocupado` |

`ingresar()`: valida `host:puerto` → `ocupado=true` → en el ejecutor de fondo `conectar` + `login` →
en `hiloUI`: éxito → `NavegadorVistas.ir(CHAT)`; error → `error = texto`; siempre `ocupado=false` y `clave=""` (R-85).
La navegación se hace con un callback (`Runnable alIngresar`) que `FabricaVistas` le pasa al ViewModel,
para que no conozca `Stage` ni `NavegadorVistas`.

### 10.2 `ChatViewModel` (implementa `IObservadorCliente`)

| Estado | Tipo | Fuente |
|---|---|---|
| `usuarios` | `ObservableList<EstadoUsuarioDTO>` | `onUsuarios`; vista filtrada y ordenada (ADMIN → conectados → resto, alfabético) |
| `filtro` | `StringProperty` | Buscar por nombre o código |
| `conectados` | `IntegerBinding` | Cantidad con `conectado = true` |
| `foro` | `ObservableList<MensajeDTO>` | `historial("FORO")` + `onMensaje` (canal FORO) |
| `privados` | `ObservableMap<String, ObservableList<MensajeDTO>>` | Clave = código del **otro**; `onMensaje` (PRIVADO) abre la lista si no existe |
| `usuarioActual`, `sesionLocal`, `mensajesGuardados` | propiedades | D-05 / D-12 |
| `estadoConexion` | `StringProperty` | «Estoy conectado a: {ip} · TCP :{puerto}» o «Desconectado: {motivo}» |
| `conectado` | `BooleanProperty` | Punto verde o rojo |
| `enviando` | `BooleanProperty` | `true` desde el envío hasta el `MENSAJE_NUEVO` de ese `idMensaje` o el timeout (R-32/R-33) |

Reglas:

- Toda mutación de propiedades u `ObservableList` pasa por `hiloUI` (**D-10**); red y H2, por el ejecutor de fondo.
- **Deduplicación por `id`** y orden por `fecha` en cada lista (`onMensaje` puede repetir un mensaje: push + `HISTORIAL`).
- Clave de un privado: `m.remitente()` si no es mío; si no, `m.destinatario()`.
- `abrirPrivado(codigo)`: ignorado si `codigo` es el propio (R-34); crea la pestaña si no existe y carga `historial(codigo)`.
- `enviarImagen(destino, f)`: valida extensión y tamaño (R-50), lee los bytes y los codifica en Base64 en el ejecutor de fondo.
- `imagen(m, etapa)`: devuelve `CompletableFuture<Image>` construida fuera del hilo FX (R-65).
- `onDesconexion(motivo)`: `conectado=false`, `estadoConexion = "Desconectado: " + motivo`, aviso y vuelta a C1.

### 10.3 `InformesViewModel`

| Estado | Tipo | Regla |
|---|---|---|
| `tipo` | `ObjectProperty<TipoInforme>` | Cambia al cambiar de pestaña → `cargar` |
| `todas` | `List<Map<String,Object>>` | Filas completas del último informe |
| `filas` | `ObservableList<Map<String,Object>>` | Página actual (50) |
| `pagina`, `total` | `IntegerProperty` | Paginación en el cliente |
| `json` | `StringProperty` | JSON crudo con sangría (D-08) |
| `tipoEvento`, `usuario`, `desde`, `hasta` | propiedades | Solo LOGS → `tipoEvento`, `usuario`, `desde=yyyy-MM-ddT00:00:00`, `hasta=yyyy-MM-ddT23:59:59.999999999` (R-45) |
| tarjetas | `IntegerProperty` × 4 | Sesiones activas = Σ `cantidadSesiones`; usuarios conectados = filas de USUARIOS_CONECTADOS; mensajes de hoy; errores = LOGS con tipo ERROR |

---

## 11. Concurrencia y ciclo de vida

| Hilo | Qué corre |
|---|---|
| Hilo FX | Solo binding, eventos de controles y mutación de observables (vía `hiloUI`) |
| Ejecutor de fondo (`AppCliente`) | `conectar`, `login`, `historial`, lectura de archivos, Base64, `Image` |
| Lector de `ConexionTCP` | `alRecibir` → `onPush` → servicios (H2 corto) → observadores → `hiloUI` |
| Escritor de `ConexionTCP` | Único que escribe en el socket |
| Hilo de `orTimeout` | Vence peticiones pendientes |

Ciclo de vida:

1. **Login:** conectar (si hace falta) → `LOGIN` → abrir H2 → `iniciarSesion` → C2.
2. **Credenciales inválidas:** el socket sigue abierto; se puede reintentar.
3. **Logout:** `LOGOUT` (espera ≤ 3 s) → cerrar sesión H2 → `proveedor.cerrar()` → `conexion.cerrar()` → C1. Sin aviso de «Desconectado».
4. **CUPO_LLENO:** error en C1; el servidor ya cerró el canal (cierre esperado).
5. **SESION_CERRADA / caída:** un solo `onDesconexion` → cerrar sesión H2 → C1 con el motivo. No se reintenta solo.
6. **Cierre de la ventana:** `stop()` idempotente, ≤ 3 s.

---

## 12. Estrategia de pruebas

| Nivel | Qué | Herramientas |
|---|---|---|
| Utilerías | configuración, JSON, fechas | JUnit 5 |
| Transporte | servidor TCP falso en el puerto 0, reconexión | `ServerSocket` + hilos |
| Protocolo | correlación, timeout, push vs respuesta, `idPeticion:null`, desconexión | JUnit 5 + servidor falso |
| Persistencia | MERGE, adjuntos, FK, orden, `contar` | `jdbc:h2:mem:` |
| Servicios | protocolo falso + H2 en memoria; orden respuesta/push | JUnit 5 |
| Fachada | push, logout, doble desconexión, reconexión | fakes |
| ViewModels | fachada falsa + `Executor` directo (D-10), **sin** iniciar JavaFX | JUnit 5 |
| UI | manual contra los mockups | checklist |
| Integración | servidor real + 2 clientes | manual + logs |

Comando: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -q verify` desde `cliente/`.

### 12.1 Ajustes surgidos al implementar (fases 9b–9c)

- `DespachadorEventos.suscribir(o)`: el catálogo solo tenía `despachar` y `desconexion`, pero `IProtocoloCliente.suscribir` necesita registrar oyentes. Pendiente de reflejar en `docs/arquitectura/cliente_clases_componentes.md` §10.
- `ConexionTCP.conectar` espera (≤ 2 s) a que el lector de la conexión anterior termine su `alDesconectar`. Sin esto, el aviso tardío de una conexión cerrada podía fallar las peticiones y el `INFO_SERVIDOR` de la conexión nueva (reconexión rápida, D-06).
- `ClienteProtocoloJSON` se crea solo con `FabricaProtocoloCliente` (constructor de paquete).
- Una respuesta con formato inválido (p. ej. `exito=true` con un código de error) falla solo su petición, con `IllegalStateException`; una trama ilegible o un evento desconocido se registran y se descartan.
- **10a** · `RepositorioMensajesH2.guardar` usa `INSERT` e ignora la clave duplicada (SQLState `23505`) en lugar de `MERGE … KEY(ID)`: así conserva el primer `SESION_ID` y `PROPIO` (R-54) y es atómico si push e `HISTORIAL` llegan a la vez. Los adjuntos sí usan `MERGE … KEY(MENSAJE_ID, ETAPA)`. Pendiente de reflejar en cliente §8.
- **10a** · `IRepositorioMensajesLocal` expone `FORO` y `claveConversacion(a, b)` / `claveConversacion(m)` como miembros estáticos (como el servidor), para que los servicios calculen la clave `menor|mayor`.
- **10a** · `ProveedorConexionH2` mantiene una conexión de guarda mientras la base está abierta; cada operación pide la suya. El esquema se ejecuta con `RUNSCRIPT FROM 'classpath:/schema.sql'`. Un adjunto sin su mensaje lanza `IllegalArgumentException` (R-55); los demás errores SQL, `IllegalStateException`.
- **10b** · `ServicioUsuarios.login` abre H2 con `thenApplyAsync` (fuera del hilo lector, R-39). Si la base local no abre, envía LOGOUT para liberar el cupo del servidor y falla con `IllegalStateException`.
- **10b** · `ServicioChat` valida el contenido con las reglas de `ValidadorPeticiones` del servidor (R-50) antes de enviar; nada inválido llega a la red. El ORIGINAL de una imagen enviada se guarda con la respuesta ENCOLADO o con el push, el que llegue último (R-48), y se descarta si la sesión se cerró.
- **10c** · `IServicioChat.mensajesGuardados()` (D-12): la fachada no puede ver el repositorio, así que el conteo pasa por el servicio de chat.
- **10c** · `FachadaCliente` termina la sesión una sola vez (R-49): `logout()` no avisa; `SESION_CERRADA` o la caída avisan un único `onDesconexion`, después de cerrar lo local en otro hilo (el LOGOUT de cortesía no debe bloquear al hilo lector que entrega la desconexión). `logout()` sin sesión cierra solo la conexión, para que `AppCliente.stop()` pueda llamarlo siempre.
- **10c** · Un observador que falla no impide avisar a los demás; el primer error se relanza para que `DespachadorEventos` lo registre (la fachada no tiene SLF4J en la matriz).
- **11a** · `FabricaVistas` lleva un registro `registrar(Clase, Contexto → controlador)`; cada pantalla (11b–11d) se registra en `AppCliente.registrarVistas` sin tocar la fábrica. `Contexto` = fachada + `hiloUI` + `fondo` (D-10) + `host:puerto` inicial + `navegar`. Una pantalla aún no construida muestra un error claro.
- **11a** · `TipoVista` lleva su FXML y su tamaño inicial; `FabricaVistas.titulo(t)` da el título de cada ventana (C2 con el nombre del usuario). INFORMES se abre en una ventana secundaria, que se cierra al volver a LOGIN.
- **11a** · `AppCliente.ensamblar(cfg)` es de paquete (no privado) para probar la pila completa sin JavaFX; `fondo` = hilos virtuales; `stop()` llama siempre a `fachada.logout()`.
- **11a** · Verificado con `javafx:run` en este equipo (Wayland + JDK 21 + JavaFX 21.0.13): arranca, carga `config/cliente.properties` y muestra el error esperado hasta que exista C1.
- **11b** · `LoginViewModel` recibe la lista de protocolos (nombres de `TipoProtocolo`) desde `AppCliente`: el ViewModel no puede importar `comunicacion`. El texto de `CUPO_LLENO` no lleva N/N (R-16) y un `host:puerto` mal escrito se rechaza sin tocar la red.
- **11b** · El ícono 💬 se dibuja con un `SVGPath` (la fuente de emoji no está disponible en este equipo, R-70). El formulario tiene ancho fijo y C1 no es redimensionable (en Hyprland queda flotante a 420×560).
- **11b** · `FabricaVistasFxmlTest` carga el FXML real con su controlador (se omite sin entorno gráfico). Detectó que FXMLLoader oculta la causa al fallar: `FabricaVistas` ahora informa la causa raíz.
- **11c** · **D-19** `IFachadaCliente.getPuerto()`: la barra de C2 muestra «TCP :{puerto}» y ningún ViewModel conocía el puerto. Pendiente de reflejar en `docs/`.
- **11c** · Un único `ChatViewModel` para toda la aplicación (lo crea `AppCliente` y se suscribe una vez): la fachada no tiene `desuscribir`, y un ViewModel por login seguiría recibiendo avisos. `iniciar()` lo deja en cero en cada sesión; fuera de una sesión ignora los avisos.
- **11c** · Sin emoji en C2 (📎, 📊, ➤, 🔍; R-70): textos «Imagen», «Informes», «Enviar» y el botón de cierre propio de las pestañas. Los motivos `CERRADA_POR_ADMIN` / `SERVIDOR_DETENIDO` se muestran en palabras.
- **11c** · Cada pestaña privada se crea una vez y se conserva al cerrarla: si llega un mensaje de esa conversación, vuelve a mostrarse. Un privado nuevo de otra persona abre su pestaña sin quitar el foco.
- **11c** · «enviando…» se apaga con el `MENSAJE_NUEVO` propio, con el rechazo del servidor o a los 15 s con «No se pudo confirmar el envío» (R-32). Las imágenes (miniatura y 5 filtros) se construyen fuera del hilo de JavaFX y se guardan en memoria por sesión.
- **11d** · `InformesViewModel` nuevo en cada apertura de C3 (no recibe avisos). Columnas de S3 (R-27) con su formato en el ViewModel (`Columna(titulo, valor)`): hora `dd/MM HH:mm:ss`, hash abreviado a 12, `todos` como destino del foro; filas de LOGS en ámbar (`CUPO_LLENO`) y rojo (`ERROR`).
- **11d** · Respuestas viejas descartadas por número de solicitud (cambiar de pestaña o filtrar dos veces); al cambiar de informe la tabla se vacía al instante. Las tarjetas se calculan con los informes sin filtrar (`USUARIOS_CONECTADOS`, `MENSAJES`, `LOGS`) que se piden al abrir.
- **11d** · «Ver JSON» y «Actualizar» se muestran en las 4 pestañas (el mockup C3 los pone junto a los filtros de Logs; S3 los ofrece para todos los informes). Los filtros siguen visibles solo en Logs. Las fechas inválidas («Desde» > «Hasta») no llegan al servidor.
- **11d** · El ViewModel importa `InformeRecibido` (paquete `servicios.informes`) solo porque es el tipo que devuelve `IFachadaCliente.informe` (D-08), igual que un DTO; no usa ningún servicio.

---

## 13. Criterios de terminado

- [ ] Compila con Java 21 (`release 21`), sin `--enable-preview`.
- [ ] `mvn -q verify` en `cliente/` en verde; el servidor sigue en verde y **sin cambios**.
- [ ] Matriz de dependencias respetada (comprobaciones de §5).
- [ ] Nombres idénticos al catálogo, salvo las desviaciones aprobadas y ya documentadas en `cliente_clases_componentes.md` y `.drawio`.
- [ ] Los ViewModels solo dependen de `IFachadaCliente` (y de `Executor`).
- [ ] Ningún control JavaFX se modifica fuera del hilo FX.
- [ ] Todo `MensajeDTO` recibido queda en H2, y también cada imagen enviada o descargada.
- [ ] Barra de estado: «Estoy conectado a: {ip}».
- [ ] Los 4 informes se ven y se filtran; «Ver JSON» muestra el JSON recibido.
- [ ] Login, foro, privado, imagen (6 versiones), logout, cupo lleno y `SESION_CERRADA` probados contra el servidor real.
- [ ] Sin `System.out` en la lógica, sin contraseñas en los logs y sin `catch` vacíos.
- [ ] README actualizados; `warning.md` y `planCliente.md` vigentes.

---

## 14. Orden de ejecución

| Sesión | Subfase | Depende de | Entregable |
|---|---|---|---|
| 0 | Documentación ✅ | aprobación de D-03…D-14 | `cliente_clases_componentes.md` + `.drawio` actualizados |
| 1 | 9a ✅ | 0 | POM padre + utilerías + configuración (13 pruebas en verde) |
| 2 | 9b ✅ | 9a | TCP + tramas + reconexión (20 pruebas; 33 en total) |
| 3 | 9c ✅ | 9b | Protocolo JSON + push (17 pruebas; 52 en total) |
| 4 | 10a ✅ | 9a | H2 + repositorios + esquema (22 pruebas; 74 en total) |
| 5 | 10b ✅ | 9c, 10a | Servicios (26 pruebas; 100 en total) |
| 6 | 10c ✅ | 10b | Fachada + observadores (13 pruebas; 113 en total) |
| 7 | 11a ✅ | 10c | AppCliente + navegación (1 prueba de pila completa; 114 en total) |
| 8 | 11b ✅ | 11a | C1 (9 pruebas; 123 en total) |
| 9 | 11c ✅ | 11b | C2 (14 pruebas; 137 en total) |
| — | Integración ✅ | 11c | Pull del servidor (fases 7c–8): contrato sin cambios, 316 pruebas del servidor y 137 del cliente en verde |
| 10 | 11d ✅ | 11b | C3 (9 pruebas; 145 en total) |
| 11 | 11e | 11c, 11d | CSS y pulido |
| 12 | 12 | todas | Integración + README |

---

## 15. Qué NO se hará

- No se modifica `servidor/` (ni código, ni configuración, ni protocolo). No se hace commit del cambio local de `servidor/config/bd.properties` (R-84).
- No se añaden comandos ni eventos.
- No se implementa TLS, gateway web ni cifrado de H2.
- No se crean clases, archivos ni versiones fuera del catálogo y de las decisiones aprobadas.
- No se adelanta trabajo de una subfase posterior.

---

## 16. Anexo · Esqueleto de archivos

### `cliente/pom.xml`

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.universidad</groupId>
  <artifactId>mensajeria-cliente</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <dto-records.version>1.0.0</dto-records.version>
    <javafx.version>21.0.13</javafx.version>
    <javafx-plugin.version>0.0.8</javafx-plugin.version>
    <jackson.version>2.19.2</jackson.version>
    <h2.version>2.5.252</h2.version>
    <slf4j.version>2.0.17</slf4j.version>
    <logback.version>1.5.18</logback.version>
    <junit.version>5.10.2</junit.version>
    <surefire.version>3.5.4</surefire.version>
  </properties>
  <modules>
    <module>transversales/utilerias-cliente</module>
    <module>comunicacion/protocolo-comunicacion</module>
    <module>comunicacion/protocolo-cliente-servidor</module>
    <module>persistencia/persistencia-local</module>
    <module>servicios/servicios-cliente</module>
    <module>servicios/fachada-cliente</module>
    <module>presentacion/app-cliente</module>
  </modules>
  <!-- dependencyManagement con las versiones de arriba (incluidos los módulos propios) -->
</project>
```

### `presentacion/app-cliente/pom.xml` (plugin)

```xml
<plugin>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-maven-plugin</artifactId>
  <version>${javafx-plugin.version}</version>
  <configuration>
    <mainClass>com.universidad.mensajeria.cliente.inicio.AppCliente</mainClass>
  </configuration>
</plugin>
```

### Comandos

```bash
# 1) solo si cambian los DTO (desde servidor/)
mvn -q -pl transversales/dto-records -am install -DskipTests

# 2) compilar y probar el cliente (desde cliente/)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -q verify

# 3) ejecutar la UI (desde cliente/; el servidor debe estar en marcha)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -q install -DskipTests
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -q -pl presentacion/app-cliente javafx:run
```

---

**Fin del plan.** No se ha escrito código del cliente. Siguiente paso, cuando el usuario lo
confirme: aprobar D-03…D-14, actualizar la documentación (sesión 0) y empezar por la **fase 9a**.
