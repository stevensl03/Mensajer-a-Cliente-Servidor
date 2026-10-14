# Mockups del servidor

> Fuente: `docs/mockups/mockups_servidor.drawio` (4 pantallas: S1–S4).
> Describe cada pantalla para implementarla sin ver el diagrama. Las clases mencionadas están en `servidor_clases_componentes.md`. Las vistas **solo** usan `IFachadaServidor` y reciben avisos como `IObservadorServidor`.

## Reglas comunes

- Se elige la vista con `servidor.vista = CONSOLA | ESCRITORIO`; `FabricaVistas` crea la que corresponda.
- La vista se suscribe con `fachada.suscribir(this)` en `iniciar()`. Los avisos (`onMensaje`, `onEvento`, `onSesiones`) llegan desde hilos de fondo:
  - Swing: todo cambio de UI va dentro de `SwingUtilities.invokeLater`;
  - consola: se imprime con un bloqueo para que las líneas no se mezclen con el menú.
- El ADMIN no inicia sesión por red: quien opera la vista **es** el ADMIN (`IUsuariosDisponibles.administrador()`).
- Los datos de ejemplo de los mockups (nombres, IP, horas) son ilustrativos.

## Vista de escritorio (Swing): `VentanaPrincipal`

**Título de la ventana:** `Servidor de Mensajería · Unillanos — ADMIN`.

**Barra de estado superior** (igual en S1, S2 y S3), con fondo verde claro mientras el servidor está activo:

- texto: `Activo · Protocolo TCP · Puerto {puerto} · IP {fachada.getIpServidor()} · Motor BD {bd.motor} · Sesiones {activas} / {maxSesiones}`;
- botón **⟳ Actualizar**: vuelve a pedir `listarSesiones()` y `listarUsuarios()`;
- botón **■ Detener** (rojo): pide confirmación, luego `fachada.detenerServidor()` y cierra la ventana.

**Pestañas:** `Monitor` · `Chat ADMIN` · `Informes`. La activa se ve en azul y subrayada.

### S1 · Monitor (`PanelMonitor`)

Dos columnas:

1. **Sesiones activas (cupo global maxSesiones = N)**
   - Tabla con las columnas `ID` (los primeros 4 caracteres de `idSesion`), `Código`, `Nombre`, `IP`, `Desde` (HH:mm) y, por fila, un botón **Cerrar** (rojo).
   - Fuente de datos: `fachada.listarSesiones()` y el aviso `onSesiones(lista)`.
   - **Cerrar** pide confirmación y llama a `fachada.cerrarSesion(idSesion)`.
   - Un mismo código puede aparecer en varias filas (varias sesiones del mismo usuario).
   - Debajo hay una **barra de ocupación del cupo** (ámbar), proporcional a `activas / maxSesiones`, y el texto `7 de 10 sesiones · Juan Martínez tiene 2 sesiones abiertas` (el usuario con más sesiones, si tiene más de una).
2. **Mensajes entrantes (en vivo)**
   - Una tarjeta por evento, la más reciente arriba, con un máximo de 200 tarjetas en memoria.
   - `onMensaje(m)` → línea 1: `HH:mm:ss · FORO|PRIVADO · {remitente} → {todos | destinatario}` (FORO en azul, PRIVADO en verde); línea 2: `TEXTO · {n} car · {p} pal` o `IMAGEN · 5 filtros ✓`; línea 3: `SHA-256 {primeros 4}…{últimos 4}`.
   - `onEvento(e)` de tipo `LOGIN_OK` → `HH:mm:ss · LOGIN · {codigo} · {ip}`, con `LOGIN_OK` y `Sesión {activas} de {max}`.
   - `onEvento(e)` de tipo `CUPO_LLENO` → en rojo: `CUPO · {codigo} · {ip}`, con `CUPO_LLENO (rechazado)` y `Sesiones {max} de {max}`.

### S2 · Chat ADMIN (`PanelChatAdmin`)

- **Columna izquierda**: «Conectados (n usuarios)», campo de búsqueda y lista con punto verde, nombre y `k sesión(es)`. Fuente: `fachada.listarUsuarios()` filtrada por `conectado`. Al hacer clic en un usuario se abre o selecciona su pestaña privada.
- **Pestañas**: `# Foro` y una por conversación privada (`Nombre ✕`, se cierra con la ✕).
- **Conversación**:
  - burbujas a la izquierda (blancas) para los demás y a la derecha (azul claro) para «ADMIN (tú)»; encabezado `Nombre · HH:mm`;
  - las imágenes muestran la miniatura ORIGINAL y una casilla «+5 filtros» que abre un diálogo con las 6 imágenes;
  - el historial se carga desde `fachada.informe(MENSAJES, …)` filtrando por la conversación, y los nuevos llegan por `onMensaje`.
- **Barra de escritura**: combo `Para: {destino} ▾` (por defecto, la pestaña activa; «Foro» publica en el foro), campo «Escribe un mensaje…», botón **📎 Imagen** (abre `JFileChooser` y valida extensión y tamaño) y botón **Enviar ➤** (azul).
  - FORO → `fachada.publicarEnForo(ContenidoDTO)`.
  - Privado → `fachada.enviarPrivado(codigo, ContenidoDTO)`.
  - El mensaje propio aparece cuando llega por `onMensaje`, no al pulsar Enviar.

### S3 · Informes (`PanelInformes`)

- **Botones de informe**: `Usuarios registrados`, `Usuarios conectados`, `Mensajes` y `Logs` (el activo en azul), más **⟳ Actualizar** y **{ } Ver JSON** (abre un diálogo con el JSON de `fachada.informe(tipo, filtros)` con sangría).
- **Tabla**: columnas según el informe:
  - `USUARIOS_REGISTRADOS`: Código, Nombres, Apellidos, Programa, Rol.
  - `USUARIOS_CONECTADOS`: Código, Nombre, Cantidad de sesiones.
  - `MENSAJES`: Hora, Tipo, Canal, Remitente, Destino (`todos` si es FORO), Hash (abreviado).
  - `LOGS`: Hora, Tipo, Usuario, IP, Descripción.
- **Detalle del mensaje seleccionado** (solo en `MENSAJES`), a la derecha:
  - ID, Tipo · Canal, Remitente (código — nombre), Fecha (`yyyy-MM-dd HH:mm:ss.SSS`), SHA-256 y Archivo (nombre y tamaño);
  - **Imágenes generadas (filtros secuenciales)**: 6 miniaturas en orden ORIGINAL, GRISES, SEPIA, ROTAR 180, BRILLO y REDUCCIÓN, leídas de las rutas de `MensajeDTO.imagenes`;
  - línea `Rutas: ./almacen/originales · ./almacen/filtradas`;
  - si el mensaje es de texto, en lugar de las imágenes se muestra el texto y sus conteos.

## S4 · Vista de consola (`VistaConsola` + `MenuConsola` + `ChatAdminConsola`)

Se ejecuta con `java -jar servidor.jar` y `servidor.vista=CONSOLA` (o con el argumento `--vista=CONSOLA`, que tiene prioridad).

```
Mensajería Académica Unillanos · Servidor v1.0 · TCP :5000 · MySQL · maxSesiones=10
──────────────────────────────────────────────────────────────
  1) Ver sesiones activas          5) Publicar en el foro (ADMIN)
  2) Informe: usuarios registrados 6) Mensaje privado (ADMIN)
  3) Informe: usuarios conectados  7) Cerrar una sesión
  4) Informe: mensajes / logs      0) Detener servidor
──────────────────────────────────────────────────────────────
```

| Opción | Acción |
|---|---|
| 1 | `imprimirSesiones(fachada.listarSesiones())` en una tabla de texto |
| 2 | `imprimirInforme(fachada.informe(USUARIOS_REGISTRADOS, {}))` |
| 3 | `imprimirInforme(fachada.informe(USUARIOS_CONECTADOS, {}))` |
| 4 | Pregunta `m` (mensajes) o `l` (logs). Para logs pide filtros opcionales (tipo, usuario) e imprime el informe |
| 5 | Pide el texto → `chat.publicarEnForo(texto)` |
| 6 | Pide el código del destino y el texto → `chat.enviarPrivado(codigo, texto)` |
| 7 | Muestra las sesiones numeradas, pide una → `fachada.cerrarSesion(idSesion)` |
| 0 | Pide confirmación → `fachada.detenerServidor()` y termina el proceso |

- Entre opciones se imprimen las líneas de Logback con color por nivel: INFO verde (MENSAJE_* y LOGIN_OK), WARN ámbar (CUPO_LLENO) y ERROR rojo. Ejemplo: `10:44:20.551 WARN  [tcp-8] CUPO_LLENO 160004570 @ 192.168.1.50 (10/10)`.
- `onMensaje(m)` imprime el detalle de **cada mensaje que llega** (requisito de `contexto.txt`): remitente, canal, destino, tipo, texto o nombre de archivo, hash y conteos.
- Los informes se imprimen en JSON con sangría (`{ "tipo": "USUARIOS_CONECTADOS", "filas": [ … ] }`).
- Una opción inválida muestra «Opción no válida» y vuelve a mostrar el menú.
