# Mockups del cliente

> Fuente: `docs/mockups/mockups_cliente.drawio` (3 pantallas: C1–C3).
> Describe cada pantalla para implementarla en JavaFX (FXML + ViewModel) sin ver el diagrama. Las clases están en `cliente_clases_componentes.md`.

## Reglas comunes

- Cada pantalla tiene un archivo FXML (`login.fxml`, `chat.fxml`, `informes.fxml`), un controlador (`VistaLogin`, `VistaChat`, `VistaInformes`) y un ViewModel. El controlador **solo** enlaza propiedades y reenvía acciones; la lógica está en el ViewModel.
- La navegación pasa por `NavegadorVistas.ir(TipoVista)` en una sola `Stage`. Informes puede abrirse en una ventana secundaria.
- Estilo: fondo `#FAFAFA`, barra de título `#37474F`, botón primario azul `#1565C0`, botón de peligro rojo `#C62828` sobre `#FFEBEE`, propios en azul claro `#E3F2FD`. Se define en `css/app.css`.
- Los datos de ejemplo (nombres, IP, horas) son ilustrativos.

## C1 · Inicio de sesión (`VistaLogin` + `LoginViewModel`)

Ventana de 420 × 560 con el título `Mensajería Académica — Iniciar sesión`. De arriba abajo:

1. Ícono 💬 y los textos «Mensajería Académica Unillanos» e «Ingresa con tu código institucional».
2. **Código** (`TextField`) ↔ `codigo`.
3. **Contraseña** (`PasswordField`) ↔ `clave`.
4. **Servidor (cliente.properties)**:
   - campo `host : puerto`, precargado con `servidor.host` y `servidor.puerto` ↔ `servidor`;
   - combo **TCP ▾** con una sola opción hoy (`TipoProtocolo.values()`), deshabilitado mientras solo exista TCP.
5. Botón **Ingresar** (primario), deshabilitado si código o contraseña están vacíos o si `ocupado` es verdadero. Ejecuta `LoginViewModel.ingresar()`.
6. **Área de error** (roja, oculta si `error` está vacío). Textos:

| Situación | Texto |
|---|---|
| `CUPO_LLENO` | `⚠ El servidor alcanzó el cupo de sesiones. Intenta más tarde.` (el protocolo no envía N/N) |
| `CREDENCIALES_INVALIDAS` | `Código o contraseña incorrectos.` |
| Sin conexión o tiempo agotado | `No fue posible conectar con {host}:{puerto}.` |

Si el login funciona, se navega a C2.

## C2 · Chat (`VistaChat` + `ChatViewModel`)

Título de la ventana: `Mensajería Académica — {nombres} {apellidos}`.

**Cabecera**: avatar circular con las iniciales, `**Nombre** · código` y el programa académico. A la derecha:

- **📊 Informes** → `NavegadorVistas.ir(INFORMES)`;
- **Salir** (peligro) → `fachada.logout()` y vuelta a C1.

**Columna izquierda: «Usuarios (k conectados)»**

- Campo 🔍 Buscar, que filtra por nombre o código.
- Lista de **todos** los usuarios registrados (`EstadoUsuarioDTO`). Cada ítem lleva un punto de color (ámbar = ADMIN, verde = conectado, gris = desconectado), el nombre en negrita y el programa debajo. Primero el ADMIN, luego los conectados y después los demás, cada grupo en orden alfabético.
- Doble clic abre la pestaña privada (`abrirPrivado(codigo)`). Debajo, la ayuda «Doble clic: abrir conversación privada».
- Se actualiza con `onUsuarios(lista)`.

**Zona central**

- Pestañas `# Foro` (fija) y una por conversación privada (`Nombre ✕`). En la pestaña ADMIN, el destino es el código del administrador.
- Mensajes como burbujas:
  - de otros: a la izquierda, blancas, con encabezado `Nombre · HH:mm`;
  - propios: a la derecha, azul claro, con encabezado `Tú · HH:mm`.
- **Imagen**: muestra la miniatura ORIGINAL (si no está en H2, se pide con `OBTENER_IMAGEN`) y el botón **Ver 5 filtros**, que abre un diálogo con las 6 versiones en orden (ORIGINAL → REDUCCIÓN). Cada versión se pide la primera vez y queda guardada en H2.
- Al abrir una pestaña se carga el historial (caché → H2 → `HISTORIAL`) y se hace scroll al final.

**Barra de escritura**

- Campo «Escribe un mensaje para el foro…» (el texto cambia según la pestaña: «… para {nombre}»). **Enter** envía.
- **📎 Imagen** abre `FileChooser` (png, jpg, jpeg, gif, bmp; máximo 5 MB) y envía de inmediato.
- **Enviar ➤** (primario). Está deshabilitado si el texto está vacío.
- El mensaje enviado aparece cuando llega `MENSAJE_NUEVO`. Mientras tanto, el campo se limpia y se muestra un indicador «enviando…».

**Barra de estado (inferior)**

- Izquierda: punto verde y **«Estoy conectado a: {ipServidor}»** · `TCP :{puerto}`. Si se pierde la conexión, el punto pasa a rojo y el texto a «Desconectado: {motivo}».
- Derecha: `Sesión local H2 #{sesionLocal} · {n} mensajes guardados`.

## C3 · Informes (`VistaInformes` + `InformesViewModel`)

Título de la ventana: `Mensajería Académica — Informes del servidor`.

- **Pestañas**: `Usuarios registrados`, `Usuarios conectados`, `Mensajes` y `Logs`. Al cambiar de pestaña se llama a `cargar(tipo, filtros)`.
- **Filtros** (solo en Logs): combo `Tipo: Todos ▾` (valores de `TipoEvento`), campo `Usuario (código)`, fechas `Desde` y `Hasta` (`DatePicker`), botón **Filtrar** (primario) y botón **{ } Ver JSON**, que muestra el JSON recibido con sangría.
- **Tabla**: columnas según el informe (las mismas que en el servidor, `mockups_servidor.md` S3). Para Logs: Hora, Tipo (CUPO_LLENO en ámbar, ERROR en rojo), Usuario, IP y Descripción.
- **Paginación**: texto `Fuente: comando INFORME → JSON del servidor · {desde}-{hasta} de {total} registros` y botones **◀ Anterior** / **Siguiente ▶**, de 50 filas por página. La paginación se hace en el cliente.
- **Resumen** (tarjetas): sesiones activas, usuarios conectados, mensajes de hoy y errores. Se calculan con los informes `USUARIOS_CONECTADOS`, `MENSAJES` y `LOGS` ya descargados.
