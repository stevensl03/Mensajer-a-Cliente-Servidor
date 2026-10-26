# `warning.md` — Auditoría previa del cliente

> Riesgos, hallazgos y supuestos **antes** de escribir el código del cliente.
> Alcance: `docs/`, `cliente/` y el servidor actual (solo lectura).
> **Revisión 1** (OpenCode, 25-oct-2026): primer análisis y prueba en vivo del protocolo.
> **Revisión 2** (Claude, 25-oct-2026): contraste línea a línea con el código del servidor,
> corrección de recomendaciones, versiones verificadas en Maven Central y hallazgos nuevos.
> **Revisión 3** (Claude, 25-oct-2026): integración de la actualización del servidor (fases 7c–8 del
> compañero, `origin/develop` 9f19799) tras terminar el cliente hasta la fase 11c. Ver §0.2.
> **El código del servidor no se modificó en ninguna de las dos revisiones.**

---

## 0. Veredicto ejecutivo

| Aspecto | Estado |
|---|---|
| Servidor (8 comandos, 5 eventos del contrato) | **Listo.** `mvn -q verify` con JDK 21: **282 pruebas, 0 fallos**. Los 8 manejadores están registrados en `FabricaManejadoresComando` (líneas 52–59) |
| Prueba en vivo del protocolo | La hizo OpenCode (§12). En la revisión 2 **no se repitió**; se confirmó cada resultado leyendo el código correspondiente |
| Cliente | **No existe**: `cliente/` solo tiene `README.md` y estos dos documentos |
| `cliente.md` | **No existe**. Lo equivalente es `docs/arquitectura/cliente_clases_componentes.md` (+ `mockups_cliente.md` y `plan_construccion.md` §4) |
| Stack | Java 21 + JavaFX 21 (LTS) + FXML + MVVM + Maven multimódulo + H2 (confirmado por el usuario) |
| Riesgo global | **Medio**. El servidor está listo; el catálogo del cliente tiene huecos que obligan a pequeñas desviaciones (D-03…D-14 de `planCliente.md`) que hay que aprobar antes de codificar |

**Conclusión:** el cliente se puede construir tal como está documentado sin tocar el servidor.
Antes de empezar hay que aprobar las desviaciones de la §11. La revisión 2 cambia cuatro
recomendaciones de la revisión 1 (R-13, R-14, R-17 y las versiones) para que **cambien menos el
catálogo**, y añade 14 hallazgos que no estaban (marcados con **[nuevo]**).

### 0.1 Qué corrigió la revisión 2

| Punto | Revisión 1 (OpenCode) | Revisión 2 (corrección) |
|---|---|---|
| R-11 | Solo la fachada necesita convertir `JsonNode` | También los **3 servicios** convierten `RespuestaDTO.datos`; el catálogo no les da `ISerializador` |
| R-13 | Crear conexión y protocolo nuevos por login (cambia el catálogo) | Hacer `ConexionTCP` **reconectable** con el mismo `conectar(...)` del catálogo; `CREDENCIALES_INVALIDAS` **no** cierra el socket |
| R-14 | `logout()` pasa a `CompletableFuture<Void>` | Mantener `void` (catálogo): bloqueo acotado por timeout, llamado fuera del hilo FX |
| R-17 | Ampliar el esquema H2 (`ORDEN/HASH/TAMANO`) | **No hace falta**: la UI solo necesita `idMensaje` + `TipoFiltro.values()`. Se mantiene el esquema §8 |
| Versiones | JavaFX 21.0.7, H2 2.3.232 | JavaFX **21.0.13** y H2 **2.5.252** (últimas estables, regla del plan) |
| `ISerializador` | El plan cambiaba la firma a `convertir(JsonNode, Class<T>)` + `aArbol` sin avisarlo | La firma del catálogo del cliente es `convertir(JsonNode, TypeReference<T>)`; se respeta (R-20) |

### 0.2 Revisión 3: integración del servidor actualizado (sin impacto en el contrato)

Se trajeron 18 commits de `origin/develop` (fases 7c y 8 del servidor, arreglos y documentación) y se
integraron en la rama del cliente sin conflictos. Resultado de la auditoría:

| Cambio del servidor | Impacto en el cliente |
|---|---|
| `dto-records`, componentes, interfaces, los 8 manejadores, `ReceptorConexiones`, `ControlSesiones`, `config/` | **Ninguno**: sin cambios (diff vacío). El contrato §7 (comandos, eventos, códigos, tramas) es idéntico |
| `ImagenVersionDTO.rutaArchivo` se guarda siempre con `/` | Ninguno: el cliente no usa `rutaArchivo` (D-07) |
| `LogEventos`: un fallo de la BD al registrar la conexión ya no deja el LOGIN a medias | Positivo: menos `ERROR_INTERNO` posibles en C1 |
| `IFachadaServidor.imagen`, `ILogEventos.suscribir`, `ISerializador.sangrar` y paneles Swing (S1–S3) | Ninguno: son internos del servidor y de su vista de escritorio; el cliente tiene su propio `ISerializador` |
| Servidor §1.2: «el ADMIN puede iniciar sesión desde un cliente y ocupa cupo» | Confirma D-09 y R-42 |
| Servidor §1.2: «cualquier cliente ve los privados en `INFORME MENSAJES`»; «los DTO pueden exponer `rutaArchivo`» | Confirma R-46 y R-82 (ya no son riesgos abiertos: están acordados) |
| `docs/aceptacion_servidor.md` + `ServidorPuntaAPuntaTest` (MySQL 8.4 y PostgreSQL 17) | Confirma en el servidor real cada suposición del cliente: `CUPO_LLENO` y `LOGOUT` cierran el canal tras responder (R-31), `SESION_CERRADA` con `SERVIDOR_DETENIDO`, ADMIN por push, filtro `tipoEvento` de LOGS |
| `mockups_servidor.md` S3: formato del detalle del mensaje | La C3 del cliente usa las **mismas columnas** que S3 (ver R-27) |

**Verificación tras integrar:** servidor `mvn -q verify` con JDK 21 → **316 pruebas, 0 fallos** (antes 282).
Cliente `mvn -q verify` → **137 pruebas, 0 fallos**, sin cambios de código. `dto-records` no cambió, así que
el artefacto instalado en `~/.m2` sigue siendo válido.

### R-27 · **[nuevo, revisión 3]** Columnas de C3 mal copiadas en el plan

El mockup C3 dice «columnas según el informe (las mismas que en el servidor, S3)». `planCliente.md` §9.3
listaba otras columnas para `MENSAJES` (texto, caracteres, palabras…). **Corregido** a las de S3:
Hora, Tipo, Canal, Remitente, Destino (`todos` si es FORO) y Hash abreviado.

---

## 1. Qué se revisó

### 1.1 Documentación

- `docs/plan_construccion.md` (fases 9–12 del cliente y criterios de terminado).
- `docs/arquitectura/cliente_clases_componentes.md` (reglas, módulos, dependencias, flujos, catálogo, H2).
- `docs/arquitectura/servidor_clases_componentes.md` §6, §7, §8 y §9 (flujos, contrato, concurrencia y configuración).
- `docs/mockups/mockups_cliente.md` (C1–C3) y `docs/mockups/mockups_servidor.md` (columnas de S3).
- `cliente/README.md` y `README.md` raíz.

### 1.2 Código del servidor leído (solo lectura)

| Archivo | Qué se confirmó |
|---|---|
| `dto-records/*` (25 tipos) | Campos, validaciones de los constructores y orden de los enums |
| `utilerias/SerializadorJSON`, `ISerializador`, `ConfiguracionServidor` | Jackson, `NON_NULL`, fechas ISO, `protocolo()` devuelve `String` |
| `canal/ProtocoloClienteServidorJSON`, `ValidadorPeticiones` | `idPeticion` obligatorio, `idPeticion:null` forzado en respuestas, límites de contenido |
| `protocolo/tcp/CodificadorTramas`, `ServidorTCP` | Trama `[int32][UTF-8]`, `1 ≤ N ≤ max`; IP del servidor con `InetAddress.getLocalHost()` |
| `fachada/servicios/ReceptorConexiones`, `ControlSesiones`, `EnrutadorMensajes`, `GeneradorInformes`, `FachadaServidor` | Orden de push y respuesta, cierres de canal, filtros de informes, motivos de `SESION_CERRADA` |
| `comandos/*` (8 manejadores + despachador) | Parámetros, errores y permisos de cada comando |
| `log/FiltroLogs` | Semántica exacta de los filtros de `LOGS` |

### 1.3 Verificación en vivo (revisión 1, OpenCode)

OpenCode levantó instancias aisladas (puertos 5001 y 5002, bases `mensajeria_prueba*` que luego
eliminó) y habló el protocolo con un cliente Python. Sus resultados, todos coherentes con el
código leído en la revisión 2:

| Comprobación | Resultado | Respaldo en el código |
|---|---|---|
| `INFO_SERVIDOR` al conectar (antes del login) | OK | `ReceptorConexiones.alConectar` |
| `LOGIN` correcto / incorrecto | OK / `CREDENCIALES_INVALIDAS` | `ControlSesiones.abrir` |
| `LISTAR_USUARIOS` | 10 usuarios, ADMIN siempre `conectado=true` | `ManejadorListarUsuarios.estados` |
| `HISTORIAL` FORO y privado | OK, ordenado, máximo 200 por defecto | `ManejadorHistorial` |
| `ENVIAR_FORO` texto / imagen | `ENCOLADO` + `MENSAJE_NUEVO`; 6 versiones | `ManejadorEnviarForo`, `EnrutadorMensajes` |
| `ENVIAR_PRIVADO` | Llega al destinatario **y** al remitente | `EnrutadorMensajes.entregar` |
| `INFORME` (4 tipos) | OK; `LOGS` con filtros | `GeneradorInformes` |
| `LOGOUT` / `CUPO_LLENO` | Responden y **después** cierran el canal | `ReceptorConexiones.alRecibir` |
| `SESION_CERRADA` | `SERVIDOR_DETENIDO` o `CERRADA_POR_ADMIN` | `FachadaServidor` líneas 110 y 153 |
| Errores (`NO_AUTENTICADO`, `DESTINO_INEXISTENTE`, `CONTENIDO_INVALIDO`, `NO_ENCONTRADO`) | OK | Despachador y manejadores |

**El servidor tiene todo lo que el cliente necesita**, pero son **comandos de un protocolo
TCP/JSON**, no rutas HTTP/REST. No hace falta ningún *gateway*.

---

## 2. Decisiones de alcance (cerradas)

### R-01 · Stack: JavaFX 21 (resuelto)

El usuario confirmó el stack de `/docs`: **Java 21 + JavaFX 21 + FXML + MVVM + Maven + H2**
(cliente §0, §3 y §10; plan fase 11). Las alternativas que se mencionaron (Rush, Rust, web) quedan
descartadas y no se construirá ningún gateway HTTP/WebSocket.

### R-02 · No existe una API HTTP (no aplica)

El servidor expone **TCP con tramas `[int32 big-endian][JSON UTF-8]`** (servidor §7). Un cliente
JavaFX consume ese protocolo directamente: no es un riesgo para este plan.

### R-03 · Archivos de contexto citados que no existen

- Los documentos citan `.claude/contexto.txt` y `.claude/skills/SKILL.md`, que **no existen** (no hay carpeta `.claude/`).
- **Mitigación:** las fuentes de verdad son `cliente_clases_componentes.md`, `mockups_cliente.md`,
  `plan_construccion.md` §4 y el contrato §7 del servidor. No se inventan reglas que no estén allí.

### R-04 · `dto-records` debe estar instalado en `~/.m2`

El cliente reutiliza `com.universidad:dto-records:1.0.0`. **Verificado:** el artefacto y el POM
padre `mensajeria-servidor` ya están en `~/.m2/repository/com/universidad/`. Si cambian los DTO:

```bash
cd servidor
mvn -q -pl transversales/dto-records -am install -DskipTests
```

**[nuevo] Consecuencia:** `dto-records` depende de `jackson-databind` (por `JsonNode` en
`RespuestaDTO.datos` y `EventoPushDTO.datos`) con la versión del servidor (**2.19.2**). El cliente
**debe** declarar la misma versión de `jackson-databind` y `jackson-datatype-jsr310`, o habrá
conflicto de versiones en el classpath.

### R-05 · El cliente no tiene proyecto, configuración ni recursos

Faltan `cliente/pom.xml` y los POM hijos, `config/cliente.properties`, los FXML, `app.css`,
`schema.sql`, `logback.xml` y las pruebas. La fase 9a los crea; nada se da por existente.

---

## 3. Huecos y contradicciones del catálogo del cliente

Cada uno exige una desviación pequeña del catálogo. Según el plan (§5), **primero** se actualizan
`cliente_clases_componentes.md` y su `.drawio`, y después se codifica.

### R-10 · `ConfiguracionCliente.protocolo()` no puede devolver `TipoProtocolo`

- Catálogo: `protocolo() : TipoProtocolo`. Matriz §4: `utilerias-cliente` **no** puede depender de
  `protocolo-comunicacion`, que es donde vive `TipoProtocolo`.
- **Mitigación:** `protocolo()` devuelve `String` y `AppCliente` hace `TipoProtocolo.valueOf(...)`.
  Es exactamente el precedente del servidor (servidor §6.1 paso 14).

### R-11 · Nadie fuera del protocolo puede convertir los `datos` (`JsonNode`) — *corregido*

- `RespuestaDTO.datos` y `EventoPushDTO.datos` son `JsonNode`.
- `ServicioUsuarios` debe sacar `UsuarioDTO` de `datos.usuario`; `ServicioChat`, la lista de
  `datos.mensajes` y los bytes de `datos.datosBase64`; `ServicioInformes`, el `InformeDTO`; y
  `FachadaCliente.onPush`, el `MensajeDTO` y el `motivo`.
- El catálogo **no** da `ISerializador` ni a los servicios ni a la fachada (§5.1 los construye sin él).
- **Mitigación recomendada:** inyectar `ISerializador` en los tres servicios y en `FachadaCliente`.
  `servicios-cliente` ya puede depender de `utilerias-cliente`; a `fachada-cliente` hay que
  agregarle esa dependencia. El *framing* y la correlación siguen siendo exclusivos de `ProtocoloClienteServidor`.

### R-12 · Los ViewModels no pueden ver el usuario actual ni la sesión local

El mockup C2 necesita «Tú» en los mensajes propios, el título `{nombres} {apellidos}`, la cabecera
(iniciales, código, programa) y `Sesión local H2 #{sesionLocal}`. `IServicioUsuarios` tiene
`actual()` y `sesionLocal()`, pero `IFachadaCliente` no.
**Mitigación:** añadir `usuarioActual() : Optional<UsuarioDTO>` y `sesionLocal() : long` a `IFachadaCliente`.

### R-13 · Reconexión después de `LOGOUT` / `CUPO_LLENO` — *corregido*

- El servidor **cierra el canal** después de responder `LOGOUT` correcto o `CUPO_LLENO`
  (`ReceptorConexiones.alRecibir`). Tras `SESION_CERRADA` también lo cierra.
- `CREDENCIALES_INVALIDAS` **no** cierra el canal: se puede reintentar el login por el mismo socket.
- §5.1 crea `conexion` y `protocolo` una sola vez y los inyecta en los servicios y en la fachada.
- **Mitigación recomendada (sin tocar el catálogo):** `ConexionTCP` admite un nuevo
  `conectar(host, puerto, r)` después de `cerrar()`: crea un `Socket`, una cola y dos hilos nuevos.
  `fachada.conectar(...)` solo reconecta si `!estaConectada()`. Las instancias de §5.1 no cambian.
- Se descarta la recomendación de la revisión 1 (crear protocolo nuevo por login): obligaría a
  reconstruir los servicios y la fachada o a inyectar fábricas, lo que sí cambia el catálogo.

### R-14 · `logout()` es `void` — *corregido*

- Se mantiene `void` (catálogo). Implementación: envía `LOGOUT`, espera la respuesta como máximo
  `min(timeout, 3 s)`, cierra la sesión H2 y la conexión. Si el tiempo se agota, cierra igualmente.
- `ChatViewModel` lo llama **fuera** del hilo FX; `AppCliente.stop()` puede llamarlo directamente
  porque la espera está acotada.
- No se cierra el socket antes de que el `LOGOUT` haya salido: el hilo escritor vacía la cola antes de cerrar.

### R-15 · «Ver JSON» debe mostrar la respuesta tal como llegó

`IFachadaCliente.informe(...)` devuelve `CompletableFuture<InformeDTO>` y pierde el JSON original.
**Mitigación:** `ServicioInformes` conserva `RespuestaDTO.datos` (el árbol tal como llegó) y la
fachada lo entrega junto al `InformeDTO` (un record envoltorio o un método `ultimoJsonInforme()`).
Se presenta con sangría (`writerWithDefaultPrettyPrinter`). Hay que elegir una forma (D-08).

### R-16 · `CUPO_LLENO` no trae N/N

La respuesta real es `{"codigo":"CUPO_LLENO","mensaje":"No hay cupos libres en el servidor"}`;
`INFO_SERVIDOR` solo trae `cuposLibres`. El máximo N no viaja nunca.
**Mitigación:** mostrar «⚠ El servidor alcanzó el cupo de sesiones. Intenta más tarde.» (sin N/N).
Mostrar N/N exigiría cambiar el protocolo, lo que está prohibido.

### R-17 · Las versiones de imagen no se reconstruyen desde H2 — *corregido (no bloquea)*

- `ADJUNTO` guarda `MENSAJE_ID`, `ETAPA`, `NOMBRE_ARCHIVO` y `DATOS`; `ImagenVersionDTO` exige además
  `orden`, `hash` y `rutaArchivo`, así que un `MensajeDTO` leído de H2 tendrá `imagenes = []`.
- **No hace falta ampliar el esquema:** para mostrar la miniatura y los 5 filtros basta con
  `idMensaje` + `TipoFiltro` (las 6 etapas son `TipoFiltro.values()`, en orden). `OBTENER_IMAGEN` y
  `repo.adjunto(id, etapa)` usan solo eso. Además, al abrir una conversación se refresca con `HISTORIAL`.
- **Decisión recomendada:** se mantiene el esquema §8 sin cambios. La UI no lee `imagenes` para dibujar.

### R-18 · `conectar()` devuelve la IP, pero `INFO_SERVIDOR` llega por push

**Mitigación:** `ClienteProtocoloJSON.conectar` espera el push `INFO_SERVIDOR` con un
`CompletableFuture<String>` y el timeout de la configuración. Si no llega, usa
`conexion.getIpServidor()` (IP remota del socket). La respuesta de `LOGIN` vuelve a traer `ipServidor`.

### R-19 · `InformeDTO.filas` es `List<?>`

Jackson lo deserializa como `List<LinkedHashMap<String,Object>>`. Para la `TableView<Map<String,Object>>`
es justo lo que se necesita; no se intenta convertirlo a DTOs. En `MENSAJES`, `imagenes` es una
lista anidada: se muestra como contador.

### R-20 · **[nuevo]** Firma de `ISerializador`

- Catálogo del cliente: `aJson(o)`, `desdeJson(s, Class<T>)`, `convertir(n : JsonNode, t : TypeReference<T>)`.
- El servidor usa `convertir(JsonNode, Class<T>)` + `aArbol(o)`. La revisión 1 copió la del servidor sin marcarlo como desviación.
- **Mitigación:** respetar el catálogo del cliente. `TypeReference` es necesario para
  `List<MensajeDTO>` y `List<EstadoUsuarioDTO>`, y también sirve para tipos simples.
  `aArbol` no hace falta en el cliente (las peticiones se serializan como `PeticionDTO`).

### R-21 · **[nuevo]** `historial()` es síncrono, pero debe refrescarse con el servidor

- Catálogo: `IFachadaCliente.historial(conversacion) : List<MensajeDTO>` (síncrono). §5.3: «primero
  lee la caché y H2, y luego actualiza con `HISTORIAL` del servidor». No dice cómo llega esa actualización al ViewModel.
- **Mitigación (sin cambiar la firma):** `historial()` devuelve lo local de inmediato y lanza `HISTORIAL`
  en segundo plano; cada mensaje nuevo se guarda en H2 y en la caché, y se notifica con
  `IObservadorCliente.onMensaje(m)`. El ViewModel deduplica por `id` y ordena por `fecha`.
- `historial()` lee H2, así que el ViewModel lo llama **fuera** del hilo FX.

### R-22 · **[nuevo]** «{n} mensajes guardados» no tiene de dónde salir

La barra de estado de C2 muestra `Sesión local H2 #{sesionLocal} · {n} mensajes guardados`, pero ni
`IRepositorioMensajesLocal` ni `IFachadaCliente` exponen un conteo.
**Mitigación:** añadir `contar() : long` a `IRepositorioMensajesLocal` y `mensajesGuardados() : long`
a la fachada (o a `IServicioChat`). Alternativa sin desviación: quitar el contador del mockup.

### R-23 · **[nuevo]** Los ViewModels no se pueden probar si llaman a `Platform.runLater` directamente

La fase 11 pide pruebas de los ViewModels con una fachada falsa. Sin el toolkit de JavaFX iniciado,
`Platform.runLater` lanza `IllegalStateException`, y en un entorno sin pantalla no se puede iniciar.
**Mitigación:** los ViewModels reciben además un `Executor hiloUI` (`Platform::runLater` en
producción, `Runnable::run` en las pruebas). Las propiedades JavaFX (`StringProperty`,
`ObservableList`) funcionan sin toolkit. Es una desviación del constructor del catálogo.

### R-24 · **[nuevo]** Archivos que no están en el catálogo

El plan dice «solo se crean los archivos que la fase indica; para crear otros, se consulta». La revisión 1
añadía sin consultarlo:

- `inicio/Launcher` (para un jar ejecutable). Con `mvn javafx:run` no hace falta, y la fase 12 no pide
  jar del cliente → **opcional; solo si el usuario lo aprueba**.
- Celdas personalizadas (lista de usuarios, burbujas): se implementan como clases **privadas anidadas**
  dentro de `VistaChat`, sin archivos nuevos.
- Envoltorio del informe (R-15), si se elige esa forma.

### R-25 · **[nuevo, fase 9a]** Los `artifactId` del cliente chocaban con los del servidor

El servidor instala en `~/.m2` `com.universidad:protocolo-comunicacion:1.0.0` y
`com.universidad:protocolo-cliente-servidor:1.0.0`. Si el cliente usara los mismos nombres, un `mvn install`
del cliente **sobrescribiría los artefactos del servidor**.
**Resuelto:** las carpetas siguen la §3 del documento, pero esos dos artefactos se llaman
`protocolo-comunicacion-cliente` y `protocolo-cliente-servidor-cliente`. Los demás (`utilerias-cliente`,
`persistencia-local`, `servicios-cliente`, `fachada-cliente`, `app-cliente`) no chocan.

### R-26 · **[nuevo, fase 9a]** `protocolo-comunicacion` no depende de `utilerias-cliente`

La matriz §4 lo permite, pero esa dependencia trae Jackson y `dto-records` de forma transitiva, y la propia
matriz pide «sin Jackson ni DTO». El módulo no necesita nada de `utilerias-cliente`, así que solo depende de
`slf4j-api` (verificado con `dependency:tree`).

---

## 4. Riesgos de protocolo y concurrencia

| ID | Riesgo | Evidencia en el servidor | Mitigación |
|---|---|---|---|
| R-30 | `USUARIO_CONECTADO` llega **antes** que la respuesta de `LOGIN` (también al propio cliente) | `ControlSesiones.abrir` difunde el push antes de que `ManejadorLogin` devuelva; la cola del canal es FIFO | La fachada ignora los push de usuarios mientras no haya usuario actual. **[nuevo]** Un `MENSAJE_NUEVO` del foro puede llegar antes de que H2 esté abierta: se guarda en un búfer y se vacía después de `iniciarSesion`, o se descarta (llegará con `HISTORIAL`) |
| R-31 | `LOGOUT` y `CUPO_LLENO` cierran el socket **después** de responder | `ReceptorConexiones.alRecibir` | Marcar «cierre esperado» para no mostrar «Desconectado» ni fallar la promesa ya completada |
| R-32 | No hay push de error si falla el procesamiento | `ProcesadorMensajes` registra `ERROR` y no avisa (servidor §6.3.5) | «enviando…» con tiempo límite (el `timeout` de la configuración) y aviso «no se pudo confirmar» |
| R-33 | El remitente solo ve su mensaje por `MENSAJE_NUEVO` | Respuesta `ENCOLADO` (§6.3) | No pintar el mensaje por adelantado; limpiar el campo y mostrar «enviando…» |
| R-34 | `ENVIAR_PRIVADO` a sí mismo se rechaza | `ManejadorEnviarPrivado` (`aSiMismo`) | El doble clic sobre uno mismo no abre pestaña |
| R-35 | Las fechas traen hasta **9** decimales | `LocalDateTime.now()` + Jackson | `JavaTimeModule`; no asumir milisegundos |
| R-36 | Una respuesta puede traer `idPeticion: null` | `ProtocoloClienteServidorJSON.serializar` lo fuerza | Clasificar por **presencia de `evento`** (push) antes que por `idPeticion`; descartar con log las respuestas huérfanas |
| R-37 | `ipServidor` es `InetAddress.getLocalHost()` del servidor | `ServidorTCP.getIpServidor` | Mostrar esa IP (pide el mockup). **[nuevo]** En algunos Linux da `127.0.1.1`; es un dato del servidor, no un error del cliente |
| R-38 | Imagen de 5 MB → Base64 de unos 6,7 MB | `maxImagenBytes=5242880`, `maxTramaBytes=16777216` | No bajar `maxTramaBytes`; probar el peor caso |
| R-39 | El servidor cierra el canal si su cola de salida (256) se llena | `CanalTCP.enviar` | Leer rápido: el hilo lector no hace I/O de H2 pesado de forma bloqueante y prolongada |
| R-40 | No hay reanudación de sesión | Apagado ordenado | Volver a C1; no reintentar credenciales automáticamente |
| R-41 | Un usuario puede tener varias sesiones | Cupo global (§6.2) | `EstadoUsuarioDTO.sesiones` puede ser > 1 |
| R-42 | `ADMIN01` puede iniciar sesión como cliente | `ControlSesiones` no lo impide; **acordado** en servidor §1.2 (revisión 3): esa sesión ocupa cupo | Se permite (D-09). El ADMIN normalmente responde desde la vista del servidor |
| R-43 | `HISTORIAL` privado usa el **código del otro**, no la clave H2 `menor\|mayor` | `ManejadorHistorial` | La fachada recibe `FORO` o un código; H2 calcula su propia clave |
| R-44 | `OBTENER_IMAGEN` de un privado ajeno da `NO_ENCONTRADO` | `ManejadorObtenerImagen` | Solo pedir imágenes de mensajes visibles |
| R-45 | Filtros de `LOGS`: `usuario` es igualdad exacta; `desde`/`hasta` son `LocalDateTime` inclusivos | `FiltroLogs.acepta` | `DatePicker` → `T00:00:00` y `T23:59:59.999999999` |
| R-46 | Los informes exponen **todos** los mensajes (incluidos privados ajenos) y logs | `GeneradorInformes`; **acordado** en servidor §1.2 (revisión 3) | Es por diseño; advertirlo en el README del cliente (fase 12) |
| R-47 | `HISTORIAL` devuelve como máximo 200 por defecto | `LIMITE_POR_DEFECTO` | Aceptar 200 (parámetro `limite` opcional, como cadena) |
| R-48 | **[nuevo]** `MENSAJE_NUEVO` puede llegar **antes** que la respuesta `ENCOLADO` | La respuesta se encola después de que `despachar` vuelve; un consumidor rápido puede adelantarse | Los bytes originales pendientes se asocian por `idMensaje` en ambos órdenes: si el mensaje ya está en H2 cuando llega la respuesta, el `ADJUNTO ORIGINAL` se guarda en ese momento |
| R-49 | **[nuevo]** `SESION_CERRADA` y el cierre del socket producen **dos** avisos de desconexión | `FachadaServidor.cerrarSesion` envía el push y luego cierra el canal | `onDesconexion` idempotente: el primer motivo gana (`CERRADA_POR_ADMIN` / `SERVIDOR_DETENIDO`) |
| R-50 | **[nuevo]** Límites de contenido que el cliente no recibe | `ValidadorPeticiones`: texto ≤ 4000 **code points**; imagen png/jpg/jpeg/gif/bmp ≤ 5 242 880 bytes y legible por `ImageIO` | Validar en el cliente con las mismas reglas (`codePointCount`) como constantes; mostrar el `mensaje` del servidor si responde `CONTENIDO_INVALIDO` |

---

## 5. Riesgos de persistencia local (H2)

| ID | Riesgo | Mitigación |
|---|---|---|
| R-51 | Una BD por usuario: `jdbc:h2:./data/cliente_{codigo}`; se abre tras el login | `ProveedorConexionH2.abrir(codigo)` / `cerrar()`; no se abre antes de autenticar |
| R-52 | `{codigo}` se interpola en una ruta y en una URL JDBC | Validar `[A-Za-z0-9_-]{1,20}`; rechazar `/`, `\`, `..`, `;` (inyección de parámetros de URL H2) |
| R-53 | Un mensaje llega por push y por `HISTORIAL` | `MERGE INTO MENSAJE KEY(ID)` **y** deduplicar la lista observable por `id` |
| R-54 | **[nuevo]** `MERGE` reescribe `SESION_ID` y `PROPIO` cada vez | Conservar el `SESION_ID` de la primera vez (MERGE solo con las columnas del mensaje, o `INSERT … WHERE NOT EXISTS`). Se prueba en la fase 10a |
| R-55 | `ADJUNTO` tiene clave foránea a `MENSAJE` | Guardar el mensaje antes que el adjunto (ver R-48) |
| R-56 | 6 versiones × 5 MB en memoria | Cargar bajo demanda; no tener las 6 en RAM más tiempo del necesario |
| R-57 | **[nuevo]** El mismo usuario abierto dos veces en la misma máquina | H2 en modo archivo bloquea la BD («Database may be already in use»). Mostrar un error claro, o usar `;AUTO_SERVER=TRUE` en `h2.url` (solo configuración, sin código) |
| R-58 | `TIMESTAMP(3)` trunca los nanosegundos | Ordenar por `FECHA` y, en empate, por `ID`; no comparar instantes con precisión de nanosegundos |
| R-59 | H2 2.x: `AUTO_INCREMENT` y `MERGE … KEY(...)` son sintaxis propia | Mantener el SQL en `schema.sql`; probar con `jdbc:h2:mem:` en la versión elegida (R-61) |

---

## 6. Riesgos de UI y entorno (JavaFX)

| ID | Riesgo | Mitigación |
|---|---|---|
| R-60 | JavaFX no viene en el JDK | `javafx-maven-plugin` 0.0.8 (`mvn javafx:run`). `Launcher` solo si se aprueba (R-24) |
| R-61 | **[corregido]** Versiones | Verificadas en Maven Central el 25-oct-2026: JavaFX 21.x más reciente = **21.0.13** (no 21.0.7); H2 más reciente = **2.5.252** (no 2.3.232); `javafx-maven-plugin` 0.0.8 es la última. JavaFX ≥ 24 exige un JDK superior a 21 |
| R-62 | El JDK por defecto es el 27; el proyecto exige Java 21 | `maven.compiler.release=21` y ejecutar con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` (así se corrieron las pruebas del servidor) |
| R-63 | JavaFX en Wayland | Hay XWayland; si falla el render, `-Dprism.order=sw` |
| R-64 | Toda mutación de la UI va en el hilo FX | `Executor hiloUI` (R-23); red y H2 siempre fuera del hilo FX |
| R-65 | `Image` desde bytes | Construir `Image` desde `ByteArrayInputStream` fuera del hilo FX; asignarla al `ImageView` en el hilo FX |
| R-66 | Orden de la lista de usuarios | ADMIN primero, luego conectados, luego el resto; alfabético en cada grupo (C2) |
| R-67 | Pestañas privadas | Un `Tab` por código; no duplicar; cerrar con ✕; la pestaña del ADMIN usa su código |
| R-68 | Paginación de informes | 50 filas por página, en el cliente |
| R-69 | `TableView<Map<String,Object>>` | Columnas por informe (las de S3) con `MapValueFactory` |
| R-70 | Emojis (💬, 📎, ➤, 📊) | Comprobar la fuente; si no se ven, usar texto |

---

## 7. Seguridad y privacidad

| ID | Riesgo | Nota |
|---|---|---|
| R-80 | TCP sin TLS: credenciales y mensajes en claro | Fuera del alcance de las fases 9–12; documentarlo |
| R-81 | `servidor/config/usuarios.txt` tiene contraseñas en claro | El servidor las hashea al importarlas; el cliente nunca las copia ni las registra |
| R-82 | Los informes exponen IPs, códigos y logs a todo usuario autenticado | Es por diseño (R-46) |
| R-83 | H2 local sin cifrar | Los mensajes quedan en `./data/cliente_{codigo}.mv.db` (ya ignorado por `*.mv.db` en `.gitignore`) |
| R-84 | `servidor/config/bd.properties` tiene un cambio local **sin commitear** en `bd.clave` | No hacer commit de ese cambio. El cliente no lo toca |
| R-85 | **[nuevo]** La clave del login en memoria | No registrarla en logs; limpiar `clave` del `LoginViewModel` después del intento |

---

## 8. Documentación desactualizada o incompleta

| ID | Hallazgo |
|---|---|
| R-90 | `README.md` raíz decía «Siguiente: Fase 5». Tras la revisión 3 dice «Siguiente: Fase 9», pero el cliente ya va por la 11c: se actualiza en la fase 12 |
| R-91 | Los docs citan `.claude/contexto.txt` y `.claude/skills/SKILL.md`, que no existen |
| R-92 | El contrato §7 muestra fechas con 3 decimales; el servidor envía hasta 9 |
| R-93 | `cliente/README.md` no describe módulos, build ni configuración |
| R-94 | No existe `cliente.md`; si es un documento distinto, el usuario debe aportarlo |
| R-95 | El plan pide «una fase por sesión»; las fases 9–11 son grandes: se proponen subfases (`planCliente.md` §8) |
| R-96 | **[nuevo]** Las desviaciones aprobadas (D-03…D-14) deben reflejarse en `cliente_clases_componentes.md` y en su `.drawio` **antes** de la fase en que se usan (plan §5, último criterio) |

---

## 9. Supuestos (si no se corrige nada)

1. Cliente **JavaFX 21 + Java 21 + Maven**, como en la documentación.
2. Se reutiliza `com.universidad:dto-records:1.0.0` del servidor (instalado en `~/.m2`) y Jackson 2.19.2.
3. El servidor corre en `127.0.0.1:5000` (configurable en `cliente.properties`).
4. El cliente se ejecuta desde `cliente/` (rutas relativas `./config`, `./data`).
5. **No se modifica el servidor**; lo que exigiría cambiar el protocolo se documenta y queda fuera.
6. Los informes son visibles para todo usuario autenticado (por diseño).
7. El ADMIN opera normalmente desde la vista del servidor; el cliente le escribe y recibe sus respuestas.

---

## 10. Matriz de riesgos (resumen)

| Probabilidad \ Impacto | Alto | Medio | Bajo |
|---|---|---|---|
| **Alta** | R-11 (conversión JSON), R-13 (reconexión) | R-12, R-21, R-23, R-30, R-31, R-49 | R-35, R-66, R-70 |
| **Media** | R-32 (sin push de error), R-48 (orden respuesta/push) | R-10, R-14, R-15, R-16, R-18, R-20, R-22, R-33, R-43, R-54, R-57 | R-41, R-45, R-58 |
| **Baja** | R-40, R-80 | R-19, R-34, R-36, R-38, R-46, R-53 | R-17, R-52, R-59, R-60, R-84 |

---

## 11. Checklist antes de implementar

> Todas las decisiones D-03…D-14 fueron aprobadas por el usuario y aplicadas; D-15…D-19 surgieron al
> implementar (ver `planCliente.md` §0). Se dejan las casillas como registro histórico.

- [x] Stack confirmado: **JavaFX 21**.
- [x] Servidor verificado: 282 pruebas en verde con JDK 21; 8 comandos registrados.
- [x] `dto-records` y el POM padre del servidor instalados en `~/.m2`.
- [ ] D-03 · `ConfiguracionCliente.protocolo()` devuelve `String`.
- [ ] D-04 · `ISerializador` inyectado en los 3 servicios y en `FachadaCliente` (+ dependencia de `fachada-cliente` a `utilerias-cliente`).
- [ ] D-05 · `usuarioActual()` y `sesionLocal()` en `IFachadaCliente`.
- [ ] D-06 · `ConexionTCP` reconectable (sin cambiar el catálogo).
- [ ] D-07 · Esquema H2 **sin cambios** (la UI usa `TipoFiltro.values()`).
- [ ] D-08 · Forma de entregar el JSON crudo del informe.
- [ ] D-09 · Se permite iniciar sesión como `ADMIN01`.
- [ ] D-10 · `Executor hiloUI` en los ViewModels.
- [ ] D-11 · Refresco de `historial()` por `onMensaje`.
- [ ] D-12 · Contador «mensajes guardados» (`contar()`) o quitarlo del mockup.
- [ ] D-13 · Versiones: JavaFX 21.0.13 y H2 2.5.252 (o mantener las de la revisión 1 con justificación).
- [ ] D-14 · `Launcher`: solo si se quiere un jar ejecutable del cliente.
- [ ] Aceptar que el servidor **no** se modifica y que los cambios de protocolo (N/N del cupo, límites en `INFO_SERVIDOR`) quedan fuera.

---

## 12. Evidencia de la verificación en vivo (revisión 1, extractos)

```json
// INFO_SERVIDOR (push al conectar)
{"evento":"INFO_SERVIDOR","fecha":"2026-10-25T19:59:57.1…",
 "datos":{"cuposLibres":10,"protocolo":"TCP","ipServidor":"192.168.20.31","puerto":5001}}

// LOGIN (respuesta)
{"idPeticion":"…","exito":true,"codigo":"OK","mensaje":"OK",
 "datos":{"idSesion":"…","ipServidor":"192.168.20.31",
          "usuario":{"codigo":"20201003","nombres":"María","apellidos":"Rodríguez",
                     "programa":"Ingeniería Electrónica","rol":"USUARIO"}}}

// LISTAR_USUARIOS (primer elemento)
{"usuario":{"codigo":"20201001","nombres":"Ana","apellidos":"Pérez",
            "programa":"Ingeniería de Sistemas","rol":"USUARIO"},
 "conectado":false,"sesiones":0}

// MENSAJE_NUEVO (texto)
{"evento":"MENSAJE_NUEVO","fecha":"2026-10-25T20:00:27.728352929",
 "datos":{"mensaje":{"id":"…","tipo":"TEXTO","canal":"FORO","remitente":"20201003",
                     "fecha":"2026-10-25T20:00:27.720395692","hash":"…",
                     "texto":"Mensaje para ver el JSON","numCaracteres":24,
                     "numPalabras":5,"imagenes":[]}}}

// MENSAJE_NUEVO (imagen): imagenes en orden ORIGINAL, ESCALA_GRISES, SEPIA,
// ROTAR_180, BRILLO, REDUCCION_TAMANO, con orden 0..5 y rutas del servidor.

// INFORME USUARIOS_CONECTADOS (fila)
{"codigo":"20201003","nombreCompleto":"María Rodríguez","cantidadSesiones":1}

// INFORME LOGS (fila; usuario/ip se omiten si son nulos)
{"id":"…","tipo":"CONEXION","descripcion":"CONEXION canal=… ip=127.0.0.1",
 "ip":"127.0.0.1","fecha":"2026-10-25T19:59:57.19195397"}

// CUPO_LLENO (respuesta; después el servidor cierra el canal)
{"idPeticion":"…","exito":false,"codigo":"CUPO_LLENO",
 "mensaje":"No hay cupos libres en el servidor"}

// SESION_CERRADA (push al apagar el servidor)
{"evento":"SESION_CERRADA","fecha":"2026-10-25T20:01:01.509673883",
 "datos":{"motivo":"SERVIDOR_DETENIDO"}}
```

Nota: según la revisión 1, las bases `mensajeria_prueba` y `mensajeria_prueba2` se crearon y
eliminaron para la prueba, y el servidor del puerto 5000 y la base `mensajeria_academica` no se
tocaron. La revisión 2 no ejecutó el servidor contra ninguna base de datos.
