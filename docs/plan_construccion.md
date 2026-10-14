# Plan de construcción: Mensajería Académica Unillanos

> Plan por fases para que un asistente de código construya el servidor y el cliente **por partes y sin desviarse del diseño**.
> Documentos de referencia (rutas relativas a `docs/`):
>
> | Documento | Contenido |
> |---|---|
> | `arquitectura/servidor_clases_componentes.md` | Arquitectura del servidor, dependencias permitidas, flujos, protocolo y catálogo de clases |
> | `arquitectura/cliente_clases_componentes.md` | Arquitectura del cliente JavaFX, flujos, H2 y catálogo de clases |
> | `arquitectura/modelo_relacional.md` | Tablas, DDL (MySQL / PostgreSQL) y qué clase usa cada tabla |
> | `mockups/mockups_servidor.md` | Pantallas S1–S4 del servidor |
> | `mockups/mockups_cliente.md` | Pantallas C1–C3 del cliente |

> **Estructura del repositorio:** `servidor/` y `cliente/` son proyectos Maven independientes y `docs/` guarda la documentación. Las rutas `config/…`, `./almacen` y `logs/` de los documentos son relativas al directorio de trabajo de cada aplicación (`servidor/` o `cliente/`).

## 1. Cómo trabajar con el asistente

**Jerarquía de decisiones:**

1. Diagrama original de paquetes y componentes del servidor (descrito en `servidor_clases_componentes.md` §2).
2. `.claude/contexto.txt`.
3. Las consideraciones acordadas (recogidas en los documentos de arriba).
4. `.claude/skills/SKILL.md`, solo en lo que no contradiga a los anteriores (concurrencia, framing y pruebas).

**Reglas para cada sesión de trabajo:**

- **Una fase (o subfase) por sesión.** No adelantar trabajo de fases siguientes.
- Antes de escribir código, leer la fase completa y las secciones de los documentos que cita.
- Los nombres de clases, métodos, DTO, enums, claves de configuración y comandos se copian **tal cual** del catálogo. Si algo falta o se contradice, **se pregunta**; no se inventa.
- Solo se crean los archivos que la fase indica. Para crear otros, se consulta primero.
- No se inventan versiones de dependencias: se verifica la última estable en Maven Central y se declara en el `pom.xml` padre (`dependencyManagement`).
- Al terminar: `mvn -q verify` en verde, la lista de revisión del documento correspondiente marcada y un resumen de lo hecho y de lo pendiente.

**Texto sugerido para iniciar cada sesión:**

> Lee `docs/plan_construccion.md`, fase **X**, y las secciones de los documentos que esa fase cita. Implementa solo esa fase. Respeta la matriz de dependencias y los nombres del catálogo. Al terminar, ejecuta `mvn -q verify` (desde `servidor/` o `cliente/`) y muéstrame la lista de revisión.

## 2. Estado actual del repositorio (13-oct-2026)

Ya existe un proyecto Maven multimódulo (`pom.xml` raíz `com.universidad:mensajeria-servidor:1.0.0`, Java 21) con los módulos `transversales/*`, `interfaces/*`, `componentes/*`, `paquetes/*` y `presentacion/*`. Tiene unas 4000 líneas, escritas con un diseño **anterior** al vigente. Lo que se puede aprovechar:

- `CodificadorTramas` y su prueba, `ServidorTCP` (base), `GestorConexiones` (semáforo) y su prueba, `PipelineFiltros` y los 5 filtros con sus pruebas, `ProcesadorTexto` y su prueba, `GeneradorHashSHA256`, `SerializadorJSON`, `ConfiguracionServidor`, la mayoría de los DTO y `logback.xml`.

**Desviaciones respecto al diseño vigente** (se corrigen en la fase 0):

| # | Situación actual | Corrección |
|---|---|---|
| D1 | `usuarios` depende de `persistencia` (`ImportadorUsuarios`, `IRepositorioUsuarios`) | `ImportadorUsuarios` pasa a `almacenar-informacion`; `ServicioUsuarios` recibe `List<UsuarioRegistroDTO>` por `FabricaUsuarios` |
| D2 | `protocolo-comunicacion` depende de `conexiones-clientes` e `interfaz-cliente-servidor`, y contiene `CodificadorJSON` | `ICanalCliente` pasa a `transversales/fachada`; el JSON pasa a `protocolo-cliente-servidor`; se agrega `IReceptorConexiones`; se eliminan `ServidorBase` y `CanalBase` (su lógica pasa a `ServidorTCP` y `CanalTCP`, en el paquete `.tcp`) |
| D3 | `protocolo-cliente-servidor` depende de `interfaz-usuarios-disponibles` y `conexiones-clientes`, y contiene los manejadores de comandos | Los manejadores, la fábrica y el despachador pasan a `fachada-de-servicios` (`…fachada.servicios.comandos`); el componente queda como `ProtocoloClienteServidorJSON` + `ValidadorPeticiones` |
| D4 | `gestor-colas-de-mensajes` depende de `mensajes` y guarda mensajes | Pasa a ser una cola de `SolicitudMensajeDTO` con `IConsumidorMensajes`; el almacenamiento pasa a `AlmacenMensajesMemoria` (paquete `mensajes`) |
| D5 | `fachada-de-servicios` depende de `almacenar-informacion` y `persistencia` | Se quitan esas dependencias (matriz §4 del documento del servidor) |
| D6 | `almacenar-informacion` depende de `mensajes` | Se invierte: `mensajes` depende de `almacenar-informacion` |
| D7 | `persistencia` usa `IPersistencia` + DAO, `RepositorioUsuariosMemoria` y `schema.sql` con `ENUM` | Se reemplaza por `FabricaRepositorios`, `IRepositorio*` JDBC, `IDialectoSQL` y migraciones Flyway por motor; se elimina el repositorio en memoria |
| D8 | Transversal `fachada`: `IFachadaDeServicios`, `EscuchaMensajes` | Se reemplaza por `IFachadaServidor`, `IObservadorServidor` e `ICanalCliente` |
| D9 | Interfaces con otros nombres o firmas (`InterfazGestorMensajes`, `IProtocoloComunicacion.iniciar(int)`, `IUsuariosDisponibles.esAdmin`, `TipoInforme` en `interfaz-servicios-disponibles`) | Firmas del catálogo; `TipoInforme` pasa a `dto-records` |
| D10 | DTO distintos al catálogo (`ConexionDTO`, `LogDTO`, `MensajeTextoDTO`, `MensajeImagenDTO`; `RespuestaDTO` sin `codigo`; `EventoPushDTO.tipo`; `SesionDTO.idConexion`; `MensajeDTO` sin `texto` ni `imagenes`) | Se dejan exactamente los records y enums del catálogo (`DTO-Records`); `TipoComando` agrega `OBTENER_IMAGEN` **al final** |
| D11 | `InicioServidor` + `ComponenteFactory` | `ServidorApp` (Composition Root) + `FabricaVistas` + `IVista` + `TipoVista` |
| D12 | `servidor.properties` con el puerto 5050 y claves incompletas; `usuarios.txt` con 3 usuarios | Claves y valores de la §9 del documento del servidor (puerto 5000); unos 10 usuarios y exactamente 1 ADMIN |
| D13 | `gson` en `dependencyManagement` sin uso | Se elimina |

## 3. Fases del servidor

Cada fase deja el proyecto compilando y con sus pruebas en verde.

### Fase 0: Alinear la estructura con el diseño (sin funcionalidad nueva)

- **Leer:** servidor §0, §2, §3 y §4; tabla de desviaciones (§2 de este plan).
- **Tareas:**
  1. Ajustar los `pom.xml` de cada módulo a la matriz de dependencias (D1–D6, D13).
  2. Mover o renombrar clases e interfaces (D1–D4, D8, D9, D11), dejando **esqueletos** que compilen. Los métodos sin implementar lanzan `UnsupportedOperationException("Fase N")`.
  3. Crear los subpaquetes `…componentes.servicios.texto`, `…componentes.servicios.imagen`, `…componentes.protocolo.tcp`, `…soporte.almacenar.cache`, `…persistencia.jdbc` y `…fachada.servicios.comandos`.
  4. Conservar y adaptar las pruebas existentes que siguen siendo válidas.
- **Aceptación:**
  - `mvn -q verify` en verde;
  - `grep` sin resultados: `java.net` fuera de `protocolo-comunicacion`, `java.sql` fuera de `persistencia`, imports de `persistencia` o `almacenar` en `usuarios`, `conexiones-clientes` y `fachada-de-servicios`;
  - ningún componente importa otro componente ni un paquete.

### Fase 1: Transversales

- **Leer:** servidor §7 (contrato), §9 (configuración) y §13 (`DTO-Records`, `UtileriasVarias`, `Fachada`).
- **Tareas:**
  - todos los records y enums de `DTO-Records`, con validación en el constructor compacto (nulos, listas copiadas con `List.copyOf`);
  - `ConfiguracionServidor` con todas las claves de §9 (falla si falta una);
  - `IGeneradorHash` / `GeneradorHashSHA256` (hexadecimal en minúsculas);
  - `ISerializador` / `SerializadorJSON` (Jackson con `JavaTimeModule`, fechas ISO y nulos omitidos);
  - `UtilImagen`;
  - `IFachadaServidor`, `IObservadorServidor` e `ICanalCliente`.
- **Pruebas:** ida y vuelta JSON de **cada** DTO, el hash de un vector conocido, la configuración con una clave faltante.
- **Aceptación:** los nombres y el orden de los enums son idénticos al catálogo.

### Fase 2: Persistencia

- **Leer:** `modelo_relacional.md` completo; servidor §13 (`Persistencia`).
- **Tareas:**
  - `ProveedorConexionJDBC` (HikariCP) con `migrar()` (Flyway, carpeta según `IDialectoSQL`);
  - `DialectoMySQL` y `DialectoPostgreSQL`;
  - `V1__esquema.sql` para los dos motores;
  - los 4 repositorios JDBC y `AlmacenamientoDirectorioLocal`;
  - `FabricaRepositorios` / `FabricaRepositoriosJDBC`;
  - `bd.properties` de ejemplo (sin contraseñas reales en el repositorio).
- **Pruebas:**
  - unitarias de `AlmacenamientoDirectorioLocal` (carpeta temporal);
  - de integración marcadas `@Tag("integracion")` contra un MySQL local, excluidas de `mvn verify` por defecto y documentadas en el README.
- **Aceptación:** `java.sql` solo aparece en este módulo; solo `PreparedStatement`; un mensaje y sus imágenes se guardan en una sola transacción.

### Fase 3: AlmacenarInformacion y Cache

- **Leer:** servidor §6.1 (pasos 3–7), §11 y §13 (`AlmacenarInformacion`, `Cache`); `modelo_relacional.md` §3 y §5.
- **Tareas:**
  - `ImportadorUsuarios` (formato de `usuarios.txt`, SHA-256, exactamente un ADMIN);
  - `AlmacenarInformacion` (archivos primero, luego la transacción; si falla, borra los archivos);
  - `ICache`, `CacheL1Caffeine`, `CacheL2Redis` (Jedis), `CacheDosNiveles` y `FabricaCaches` (con `cache.l2.habilitada=false` devuelve solo L1);
  - `inicializar()` (importa usuarios y cierra conexiones huérfanas).
- **Pruebas:** con repositorios falsos en memoria **solo dentro de las pruebas**: importación, rutas de archivos, borrado si la transacción falla, caché L1 → L2.
- **Aceptación:** `Usuarios` y `ConexionesClientes` siguen sin conocer este módulo.

### Fase 4: Componentes (uno por subfase, en este orden)

| Subfase | Componente | Leer | Pruebas mínimas |
|---|---|---|---|
| 4a | `Servicios` (subcomponentes `ProcesamientoTexto` y `FiltrosImagen`, `ServiciosProcesamiento`, `FabricaServicios`) | servidor §1.1, §13 (Servicios) | Conteo de caracteres y palabras (espacios múltiples, saltos de línea, tildes); hash; 6 imágenes en orden y encadenadas; la entrada no se modifica |
| 4b | `Usuarios` (`ServicioUsuarios`, `FabricaUsuarios`) | servidor §6.2, §13 | Autenticación correcta e incorrecta; `administrador()`; `existe()`; nunca expone `contrasenaHash` |
| 4c | `GestorColasDeMensajes` | servidor §8 | `encolar` devuelve `false` con la cola llena; N consumidores; `detener` vacía la cola dentro del plazo |
| 4d | `ProtocoloClienteServidor` (`ProtocoloClienteServidorJSON`, `ValidadorPeticiones`, `FabricaClienteServidor`) | servidor §7 | JSON válido e inválido; parámetros faltantes por comando; límites de contenido; respuesta y evento serializados según los ejemplos |
| 4e | `ProtocoloComunicacion` / `tcp` (`ServidorTCP`, `ManejadorCliente`, `CanalTCP`, `CodificadorTramas`, `FabricaProtocolos`) | servidor §7 (trama), §8, §12 | Servidor en el puerto 0: conectar, enviar y recibir tramas, trama inválida → cierre; un único escritor; cola de salida llena → cierre; `alDesconectar` se llama una sola vez |

**Aceptación de la fase 4:** cada componente depende solo de su interfaz y de los transversales.

### Fase 5: Paquetes Mensajes, LogDeEventos y ConexionesClientes

- **Leer:** servidor §5, §6.3, §6.6, §8 y §10; catálogo de los tres paquetes.
- **Tareas:**
  - `Mensaje`, `MensajeTexto`, `MensajeImagen`, `FabricaMensajes`, `AlmacenMensajesMemoria` (estructuras + write-through + `cargarHistorial`);
  - `LogEventos` (SLF4J + estructuras + persistencia, formato §10), `FiltroLogs`;
  - `GestorConexiones` (semáforo justo, liberación idempotente) y `SesionActiva`.
- **Pruebas:**
  - consultas de foro y conversación;
  - `claveConversacion("b","a") == "a|b"`;
  - filtros de logs;
  - cupo: N sesiones de un mismo usuario bloquean a los demás, y cerrar libera el cupo.

### Fase 6: FachadaDeServicios

- **Leer:** servidor §6 completo y §7.
- **Tareas:** `FachadaServidor`, `ReceptorConexiones`, `ControlSesiones`, `DespachadorComandos`, `IManejadorComando`, los 8 manejadores, `FabricaManejadoresComando`, `ProcesadorMensajes`, `EnrutadorMensajes`, `GeneradorInformes`, `NotificadorObservadores` y `ExcepcionNegocio`.
- **Pruebas** (con implementaciones falsas de las 5 interfaces y un `ICanalCliente` que graba lo enviado):
  - login correcto, con credenciales inválidas y con cupo lleno (se cierra el canal);
  - comando sin sesión → `NO_AUTENTICADO`;
  - FORO llega a todos y PRIVADO llega al destinatario y al remitente;
  - un mensaje al ADMIN notifica a los observadores;
  - el remitente se toma de la sesión;
  - los 4 informes;
  - el cierre por el administrador envía `SESION_CERRADA`.
- **Aceptación:** el módulo no depende de ningún componente concreto ni de `almacenar-informacion` o `persistencia`.

### Fase 7: Presentación del servidor

- **Leer:** servidor §6.1 y §10; `mockups_servidor.md`.
- **Tareas:**
  - `ServidorApp` (ensamblado exacto de §6.1, apagado ordenado, argumento `--vista`);
  - `FabricaVistas`, `IVista`, `TipoVista`;
  - `VistaConsola`, `MenuConsola`, `ChatAdminConsola` (S4);
  - `VentanaPrincipal`, `PanelMonitor`, `PanelChatAdmin`, `PanelInformes` (S1–S3);
  - `logback.xml`;
  - jar ejecutable.
- **Pruebas:** `MenuConsola` con entrada simulada; las vistas se prueban a mano siguiendo los mockups.

### Fase 8: Prueba de punta a punta del servidor

- Un cliente de prueba en Java (en `src/test`) que habla el protocolo §7: dos usuarios, foro, privado, imagen (6 archivos en `./almacen` y 6 filas), cupo lleno, informe, desconexión.
- Revisar en la BD: `mensajes`, `mensajes_texto`, `imagenes_mensaje`, `logs_eventos` y `conexiones_usuario`.
- **Aceptación:** se cumplen todos los requisitos de la tabla §1.1 del documento del servidor.

## 4. Fases del cliente

### Fase 9: Estructura y comunicación del cliente

- **Leer:** cliente §0–§4, §6 y §9; servidor §7.
- **Tareas:**
  - proyecto Maven en `cliente/` (independiente de `servidor/`) con sus módulos (cliente §3);
  - `utilerias-cliente` (`ConfiguracionCliente`, `ISerializador`, `SerializadorJSON`);
  - `ProtocoloComunicacion` / `tcp` (`IConexionServidor`, `IReceptorServidor`, `ConexionTCP`, `CodificadorTramas`, `FabricaConexiones`, `TipoProtocolo`);
  - `ProtocoloClienteServidor` (`IProtocoloCliente`, `IOyentePush`, `ClienteProtocoloJSON`, `DespachadorEventos`, `FabricaProtocoloCliente`).
- **Pruebas:** contra un servidor TCP falso en el puerto 0: correlación por `idPeticion`, timeout, distinción entre push y respuesta, desconexión que completa los pendientes con error.

### Fase 10: Persistencia local y servicios del cliente

- **Leer:** cliente §5 y §8.
- **Tareas:**
  - `ProveedorConexionH2` + `schema.sql`, `RepositorioSesionH2`, `RepositorioMensajesH2` (MERGE), `FabricaRepositoriosH2`;
  - `ServicioUsuarios`, `ServicioChat` (+ `CacheMensajesSesion`), `ServicioInformes`;
  - `FachadaCliente`, `IObservadorCliente`.
- **Pruebas:** H2 en memoria (`jdbc:h2:mem:`); un mensaje recibido dos veces se guarda una sola vez; los adjuntos; el login abre la BD del usuario.

### Fase 11: Interfaz JavaFX

- **Leer:** `mockups_cliente.md`; cliente §5.
- **Tareas:** `AppCliente`, `FabricaVistas`, `NavegadorVistas`, `TipoVista`, FXML + controladores + ViewModels de C1, C2 y C3, `app.css`.
- **Pruebas:** de los ViewModels con una `IFachadaCliente` falsa; la UI se prueba a mano siguiendo los mockups.

### Fase 12: Integración final

- Servidor y dos clientes reales en la misma red: todos los flujos de §6 del servidor, los informes desde el cliente y la persistencia en H2.
- README en la raíz: requisitos (Java 21, MySQL, Redis opcional), configuración, creación de la BD, cómo ejecutar el servidor (consola y escritorio) y el cliente, y cómo correr las pruebas de integración.

## 5. Criterios de terminado (todas las fases)

- [ ] Compila con Java 21 sin `--enable-preview`.
- [ ] Respeta la matriz de dependencias y los nombres del catálogo.
- [ ] Pruebas nuevas en verde; las anteriores siguen pasando.
- [ ] Sin `System.out` en la lógica (solo en `VistaConsola`), sin contraseñas en logs y sin `catch` vacíos.
- [ ] Toda dependencia nueva tiene una versión verificada y está en el `pom.xml` padre.
- [ ] Si una fase obliga a cambiar el diseño, se actualizan el `.drawio` y su `.md` **antes** de seguir.
