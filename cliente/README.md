# Cliente · Mensajería Académica Unillanos

Cliente de escritorio en **Java 21 + JavaFX 21** (MVVM) con persistencia local en **H2**. Habla con el
servidor por **TCP** usando el contrato de `docs/arquitectura/servidor_clases_componentes.md` §7.

- Diseño: `docs/arquitectura/cliente_clases_componentes.md` y `docs/mockups/mockups_cliente.md`.
- Plan, decisiones (D-03…D-19) y estado: `cliente/planCliente.md`.
- Riesgos y auditorías: `cliente/warning.md`.

## Requisitos

- **JDK 21** (el proyecto compila con `release 21`). Si tu JDK por defecto es otro, usa `JAVA_HOME`:
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
- **Maven 3.9** o superior.
- El artefacto `com.universidad:dto-records` del servidor en tu repositorio local de Maven. Se instala una vez
  (y otra vez si cambian los DTO):

  ```bash
  cd servidor
  mvn -q -pl transversales/dto-records -am install -DskipTests
  ```

- Para usarlo, un **servidor en marcha** (ver `README.md` en la raíz).

## Compilar y probar

Desde `cliente/`:

```bash
mvn clean install          # compila los 7 módulos, corre las pruebas e instala los artefactos
mvn -q verify              # solo compilar y probar
```

- Las pruebas de la interfaz que cargan los FXML reales necesitan un entorno gráfico; sin él se omiten solas.
- La **prueba de integración** (`ClienteContraServidorRealTest`) recorre el contrato completo con dos clientes
  (Ana y Luis) contra un servidor real. Está excluida por defecto; con el servidor en marcha:

  ```bash
  mvn verify -Pintegracion -pl presentacion/app-cliente -am -Dcliente.e2e.servidor=127.0.0.1:5000
  ```

  Usa los usuarios de `servidor/config/usuarios.txt`, deja sus mensajes en la BD del servidor (marcados con
  `e2e-…`) y sus bases H2 en una carpeta temporal.

## Ejecutar

Desde `cliente/` (las rutas `config/`, `data/` y `logs/` son relativas a esta carpeta):

```bash
mvn -q install -DskipTests                          # la primera vez, o tras cambiar el código
mvn -q -pl presentacion/app-cliente javafx:run      # abre la ventana de inicio de sesión
```

Para dos usuarios en la misma máquina, abre otra terminal y repite el segundo comando.

### Configuración (`config/cliente.properties`)

| Clave | Valor por defecto | Uso |
|---|---|---|
| `servidor.host` / `servidor.puerto` | `127.0.0.1` / `5000` | Valor inicial del campo «Servidor» de la pantalla de login |
| `servidor.protocolo` | `TCP` | Único protocolo disponible |
| `servidor.maxTramaBytes` | `16777216` | Igual que el servidor |
| `peticion.timeoutSegundos` | `15` | Espera máxima de una respuesta |
| `h2.url` | `jdbc:h2:./data/cliente_{codigo}` | Una base por usuario; `{codigo}` es obligatorio. Para abrir **el mismo usuario dos veces** en una máquina, añade `;AUTO_SERVER=TRUE` |
| `h2.usuario` / `h2.clave` | `sa` / (vacío) | Credenciales de H2 |
| `cache.mensajes.max` | `500` | Conversaciones en la caché de la sesión |

Usuarios de prueba: los de `servidor/config/usuarios.txt` (por ejemplo `20201001` / `clave123`).

## Uso rápido

| Pantalla | Qué se puede hacer | Teclado |
|---|---|---|
| **C1 · Inicio de sesión** | Código, contraseña y `host:puerto` | Enter: Ingresar |
| **C2 · Chat** | Foro y privados, imágenes (png, jpg, jpeg, gif, bmp; máx. 5 MB) con sus 5 filtros, lista de usuarios con su estado | Enter envía · Ctrl+F busca usuarios · ↓ pasa a la lista · Enter abre el privado · Esc limpia la búsqueda · Alt+I Informes · Alt+M Imagen · Alt+S Salir |
| **C3 · Informes** (ventana aparte) | Los 4 informes del servidor, filtros de Logs, paginación de 50 y el JSON tal como llegó | Alt+J Ver JSON · Esc cierra el JSON |

- La barra inferior de C2 muestra «Estoy conectado a: {IP} · TCP :{puerto}» y la sesión local H2.
- Si el ADMIN cierra la sesión o el servidor se detiene, se avisa el motivo y se vuelve al inicio.
- Todo lo que se envía y recibe en la sesión queda en `data/cliente_{codigo}.mv.db` (no se versiona).
- Los informes muestran **todos** los mensajes, incluidos los privados ajenos, y los logs del servidor: es lo
  acordado para el proyecto (servidor §1.2).

## Módulos

| Módulo (`cliente/…`) | Contenido | Conoce |
|---|---|---|
| `transversales/utilerias-cliente` | `ConfiguracionCliente`, `ISerializador`, `SerializadorJSON`, `ExcepcionServidor` | Jackson, DTO |
| `comunicacion/protocolo-comunicacion` | `IConexionServidor`, `ConexionTCP`, `CodificadorTramas` | Sockets (solo aquí) |
| `comunicacion/protocolo-cliente-servidor` | `IProtocoloCliente`, `ClienteProtocoloJSON`, `DespachadorEventos` | JSON del protocolo (solo aquí) |
| `persistencia/persistencia-local` | `ProveedorConexionH2`, repositorios, `schema.sql` | JDBC y H2 (solo aquí) |
| `servicios/servicios-cliente` | `ServicioUsuarios`, `ServicioChat`, `ServicioInformes` | Interfaces de protocolo y persistencia |
| `servicios/fachada-cliente` | `IFachadaCliente`, `FachadaCliente`, `IObservadorCliente` | Interfaces de servicios |
| `presentacion/app-cliente` | `AppCliente` (Composition Root), vistas FXML, ViewModels, CSS | JavaFX (solo aquí) |

Los artefactos de `comunicacion/` se llaman `protocolo-comunicacion-cliente` y `protocolo-cliente-servidor-cliente`
para no sobrescribir los del servidor en `~/.m2`.

## Problemas conocidos

- **Fuentes de emoji:** si el sistema no tiene una fuente de emoji, los emojis de los mensajes no se ven. La
  interfaz no usa emojis por ese motivo.
- **Wayland:** JavaFX 21 corre sobre XWayland. Si la ventana no se dibuja bien, prueba con
  `-Dprism.order=sw`. Las herramientas que inyectan teclas en Wayland (como `wtype`) no llegan bien a la ventana de
  JavaFX; para automatizarla habría que usar una de X11, como `xdotool` (no verificado en este proyecto).
- **Misma cuenta dos veces en una máquina:** H2 bloquea la base del usuario; usa `;AUTO_SERVER=TRUE` en `h2.url`.
