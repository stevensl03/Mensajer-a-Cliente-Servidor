# Mensajería Académica Unillanos

Sistema de mensajería **cliente/servidor** para la comunidad académica de la universidad. El servidor está escrito en **Java 21**, escucha por **TCP** y persiste en **MySQL/PostgreSQL**; el cliente es una aplicación de escritorio **JavaFX 21** con persistencia local en **H2**.

> **Importante:** `servidor/` y `cliente/` son **dos proyectos Maven independientes**. No existe un `pom.xml` en la raíz: cada uno se compila y se ejecuta desde su propia carpeta. El cliente reutiliza los DTO del servidor (`com.universidad:dto-records`) desde el repositorio local `~/.m2`, por eso **el servidor debe compilarse e instalarse primero**.

---

## Contenido

- [Cómo funciona](#cómo-funciona)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Requisitos previos](#requisitos-previos)
- [1. Preparar la base de datos](#1-preparar-la-base-de-datos)
- [2. Compilación y construcción](#2-compilación-y-construcción)
- [3. Ejecución del servidor](#3-ejecución-del-servidor)
- [4. Ejecución del cliente](#4-ejecución-del-cliente)
- [5. Todo en uno con `make`](#5-todo-en-uno-con-make)
- [6. Usuarios de prueba](#6-usuarios-de-prueba)
- [7. Verificación, puertos y logs](#7-verificación-puertos-y-logs)
- [8. Pruebas automáticas](#8-pruebas-automáticas)
- [9. Configuración](#9-configuración)
- [10. Problemas comunes](#10-problemas-comunes)
- [11. Documentación](#11-documentación)
- [Estado del proyecto](#estado-del-proyecto)

---

## Cómo funciona

| Componente | Tecnología | Función |
|---|---|---|
| **Servidor** | Java 21, TCP, HikariCP, Flyway, MySQL/PostgreSQL | Escucha conexiones en el puerto **TCP 5000**, autentica usuarios, procesa textos e imágenes, encola mensajes, persiste la información y genera informes. |
| **Cliente** | Java 21 + JavaFX 21, H2 | Interfaz gráfica de escritorio: inicio de sesión, chat (foro y privados), envío de imágenes con filtros e informes. |
| **Comunicación** | TCP + tramas JSON | El servidor y el cliente intercambian peticiones, respuestas y eventos por el contrato descrito en `docs/arquitectura/servidor_clases_componentes.md` §7. |
| **Persistencia** | MySQL/PostgreSQL (servidor), H2 (cliente) | El servidor guarda usuarios, mensajes, logs, imágenes y archivos; el cliente guarda una base H2 por usuario. |

Flujo resumido:

```text
┌────────────────────┐        TCP :5000        ┌────────────────────┐
│  Cliente JavaFX    │  ◄───────────────────►  │  Servidor Java     │
│  (varias ventanas) │      tramas JSON        │  (consola/Swing)   │
└─────────┬──────────┘                         └─────────┬──────────┘
          │                                              │
     H2 local                                       MySQL/PostgreSQL
 (cliente/data/…)                                (mensajeria_academica)
```

---

## Estructura del repositorio

```text
.
├── Makefile                   Atajos para compilar, ejecutar y probar todo
├── README.md                  Este archivo
├── servidor/                  Proyecto Maven del servidor (22 módulos)
│   ├── pom.xml                POM padre: versiones y módulos
│   ├── config/                servidor.properties, bd.properties, usuarios.txt
│   ├── transversales/         DTO-Records, UtileriasVarias, Fachada
│   ├── interfaces/            Contratos de los 5 componentes
│   ├── componentes/           Implementaciones de los componentes
│   ├── paquetes/              Mensajes, logs, conexiones, persistencia, fachada
│   ├── presentacion/          InicioServidor, VistaConsola, VistaEscritorio
│   ├── logs/                  Registro del servidor (se genera al ejecutar)
│   └── almacen/               Archivos e imágenes (se genera al ejecutar)
├── cliente/                   Proyecto Maven del cliente (7 módulos)
│   ├── pom.xml                POM padre: versiones y módulos
│   ├── config/                cliente.properties
│   ├── data/                  Bases H2 por usuario (se generan al ejecutar)
│   ├── logs/                  Registro del cliente (se genera al ejecutar)
│   ├── comunicacion/          Protocolo TCP y protocolo JSON
│   ├── persistencia/          Persistencia local H2
│   ├── servicios/             Servicios y fachada del cliente
│   └── presentacion/          Aplicación JavaFX (FXML, ViewModels, CSS)
└── docs/                      Arquitectura, mockups, plan y criterios de aceptación
```

---

## Requisitos previos

| Requisito | Versión | Obligatorio | Notas |
|---|---|---|---|
| **JDK** | **21** | Sí | El proyecto compila con `release 21` y usa JavaFX 21. Un JDK superior puede compilar, pero la combinación soportada es JDK 21. |
| **Maven** | **3.9 o superior** | Sí | Se recomienda 3.9+; el proyecto se ha probado con 3.10. |
| **GNU Make** | 3.81 o superior | Opcional | Solo para los atajos de la sección 5. |
| **MySQL** | **8.0.16 o superior** | Sí (servidor) | También se admite **PostgreSQL**. La base debe existir antes de arrancar. |
| **Docker** | 20+ | Opcional | Solo para levantar MySQL/Redis de forma rápida. |
| **Entorno gráfico** | X11/XWayland, macOS o Windows | Sí (cliente) | JavaFX necesita una pantalla para abrir la interfaz. |
| **Redis** | 7+ | Opcional | Caché L2; viene deshabilitada con `cache.l2.habilitada=false`. |

Comprueba las versiones instaladas:

**Comando:**
```bash
java -version
mvn -v
```

**¿Qué hace?** Muestra la versión de Java y de Maven que se usarán. La salida de `java -version` debe indicar `21`.

Si tu JDK por defecto no es el 21, selecciónalo antes de compilar y ejecutar:

**Comando:**
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
```

**¿Qué hace?** Apunta `JAVA_HOME` al JDK 21 y pone sus binarios al frente del `PATH`, de modo que `java` y `mvn` usen esa versión. Ajusta la ruta según tu sistema operativo.

> En macOS suele ser `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`. En Windows, configura `JAVA_HOME` desde las variables de entorno del sistema.

---

## 1. Preparar la base de datos

El servidor necesita una base **MySQL** (o PostgreSQL) ya creada. Flyway crea las tablas automáticamente en el primer arranque.

### Opción A: MySQL con Docker (recomendada para desarrollo)

**Comando:**
```bash
docker run -d --name mensajeria-mysql \
  -e MYSQL_ROOT_PASSWORD=secreto \
  -e MYSQL_DATABASE=mensajeria_academica \
  -e MYSQL_USER=app_mensajeria \
  -e MYSQL_PASSWORD=secreto \
  -p 3306:3306 \
  mysql:8.4
```

**¿Qué hace?** Descarga (si hace falta) la imagen `mysql:8.4`, crea un contenedor llamado `mensajeria-mysql`, crea la base `mensajeria_academica` y el usuario `app_mensajeria` con contraseña `secreto`, y publica el puerto `3306`. Estos valores son los de ejemplo de `servidor/config/bd.properties`; ajusta `bd.clave` si está enmascarada.

Si el contenedor ya existe de una ejecución anterior:

**Comando:**
```bash
docker start mensajeria-mysql
```

**¿Qué hace?** Vuelve a arrancar el contenedor existente sin perder los datos.

Comprueba que MySQL responde:

**Comando:**
```bash
docker ps --filter name=mensajeria-mysql
```

**¿Qué hace?** Lista el contenedor y confirma que está en estado `Up`.

### Opción B: MySQL o PostgreSQL ya instalados

Crea la base y un usuario con permisos (ejemplo para MySQL):

**Comando:**
```sql
CREATE DATABASE mensajeria_academica CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'app_mensajeria'@'localhost' IDENTIFIED BY 'secreto';
GRANT ALL PRIVILEGES ON mensajeria_academica.* TO 'app_mensajeria'@'localhost';
FLUSH PRIVILEGES;
```

**¿Qué hace?** Crea la base `mensajeria_academica` y el usuario `app_mensajeria`, y le otorga permisos sobre esa base. Después edita `servidor/config/bd.properties` si usas otros valores.

Configuración de conexión (`servidor/config/bd.properties`):

```properties
bd.motor=MYSQL
bd.url=jdbc:mysql://localhost:3306/mensajeria_academica
bd.usuario=app_mensajeria
bd.clave=secreto
bd.pool=10
```

Para PostgreSQL, cambia el motor y la URL:

```properties
bd.motor=POSTGRESQL
bd.url=jdbc:postgresql://localhost:5432/mensajeria_academica
```

> **No subas contraseñas reales al repositorio.** El valor `secreto` es solo para desarrollo local. En el repositorio, `bd.clave` aparece enmascarada (`********`); ajusta esa clave a la de tu entorno antes de arrancar.

---

## 2. Compilación y construcción

### 2.1 Compilar e instalar el servidor

**Comando:**
```bash
cd servidor
mvn clean install -DskipTests
```

**¿Qué hace?**

- `clean`: borra la carpeta `target/` de cada módulo para compilar desde cero.
- `install`: compila los **22 módulos** del servidor, ejecuta el empaquetado (`package`) y copia cada artefacto al repositorio local **`~/.m2/repository`**.
- Genera el JAR ejecutable del servidor con todas sus dependencias:
  `servidor/presentacion/inicio-servidor/target/inicio-servidor-1.0.0.jar`.
- Instala `com.universidad:dto-records:1.0.0` en `~/.m2`; el cliente lo necesita para compilar.
- `-DskipTests`: **omite la ejecución de las pruebas**. Si quieres compilar y probar, usa `mvn clean install` sin ese parámetro (tardará más).
- **No arranca el servidor**; solo deja todo compilado e instalado.

### 2.2 Compilar e instalar el cliente

**Comando:**
```bash
cd cliente
mvn clean install -DskipTests
```

**¿Qué hace?**

- Compila los **7 módulos** del cliente, empaqueta sus artefactos y los instala en `~/.m2`.
- Resuelve `dto-records` desde `~/.m2` (por eso el paso 2.1 debe haberse ejecutado antes).
- Genera los JAR de cada módulo, pero el cliente **no se ejecuta como JAR**: se lanza con el plugin de JavaFX (ver sección 4).
- `-DskipTests` omite las pruebas; usa `mvn clean install` para ejecutarlas.

### 2.3 Atajo con `make`

**Comando:**
```bash
make compilar
```

**¿Qué hace?** Desde la raíz, detecta un JDK 21, compila el servidor, instala `dto-records` en `~/.m2` y compila e instala el cliente, todo sin ejecutar pruebas. No hace `clean`; si quieres una compilación desde cero, usa los comandos con `mvn clean install`. Si el JDK 21 está en una ruta no habitual:

**Comando:**
```bash
make compilar JAVA21=/ruta/al/jdk-21
```

**¿Qué hace?** Ejecuta la misma compilación indicando explícitamente la ruta del JDK 21 que debe usar `make`.

---

## 3. Ejecución del servidor

**Antes de arrancar:**

1. La base de datos debe estar levantada (sección 1).
2. `servidor/config/bd.properties` debe apuntar a esa base.
3. El comando debe ejecutarse **desde `servidor/`**, porque las rutas `config/`, `./almacen` y `logs/` son relativas al directorio de trabajo.
4. El servidor debe haberse compilado (sección 2.1).

### Opción A: JAR ejecutable con vista de consola (comando del proyecto)

**Comando:**
```bash
cd servidor
java -jar presentacion/inicio-servidor/target/inicio-servidor-1.0.0.jar --vista=CONSOLA
```

**¿Qué hace?**

- Inicia el **backend** y carga `config/servidor.properties` y `config/bd.properties`.
- Se conecta a MySQL/PostgreSQL con un pool HikariCP y aplica las migraciones de Flyway.
- Importa los usuarios de `config/usuarios.txt` y carga el historial de mensajes y logs.
- Abre un socket **TCP en el puerto `5000`** (configurable con `servidor.puerto`) y queda **a la espera de conexiones de clientes**.
- Admite hasta **10 sesiones simultáneas** (`servidor.maxSesiones`) y procesa los mensajes con 4 hilos consumidores.
- Autentica a cada cliente, atiende peticiones, encola mensajes y persiste todo.
- Muestra el menú de la vista de consola. Pulsa **`0`** para detenerlo de forma ordenada.
- La terminal queda ocupada mientras el servidor está en marcha. Para ejecutarlo en segundo plano, usa `make servidor-fondo` (sección 5).

### Opción B: vista de escritorio

**Comando:**
```bash
cd servidor
java -jar presentacion/inicio-servidor/target/inicio-servidor-1.0.0.jar --vista=ESCRITORIO
```

**¿Qué hace?** Arranca el mismo servidor, pero en lugar del menú de consola abre la vista de escritorio (Swing) para administrar sesiones, ver logs e informes.

### Opción C: atajo con `make`

**Comando:**
```bash
make servidor
```

**¿Qué hace?** Desde la raíz, verifica el JDK 21, arranca el contenedor `mensajeria-mysql` si no está escuchando, compila el servidor si hace falta y lo ejecuta con `--vista=CONSOLA`. Para la vista gráfica:

**Comando:**
```bash
make servidor-escritorio
```

**¿Qué hace?** Ejecuta el mismo servidor, pero con `--vista=ESCRITORIO` para abrir la interfaz de administración.

### Cambiar el puerto

Edita `servidor/config/servidor.properties`:

```properties
servidor.protocolo=TCP
servidor.puerto=5000
```

Si cambias el puerto, actualiza también `servidor.puerto` en `cliente/config/cliente.properties` o escribe el nuevo `host:puerto` en la pantalla de inicio de sesión del cliente.

### Acceso desde otra máquina

El servidor escucha en el puerto TCP configurado. Para que un cliente de otra máquina se conecte:

1. Asegúrate de que el puerto `5000` esté permitido en el cortafuegos del servidor.
2. En el cliente, escribe la IP del servidor y el puerto en la pantalla de inicio de sesión (por ejemplo, `192.168.1.50:5000`).

---

## 4. Ejecución del cliente

**Antes de arrancar:**

1. El servidor debe estar en marcha y escuchando (sección 3).
2. El cliente debe haberse compilado (sección 2.2).
3. El comando debe ejecutarse **desde `cliente/`**, porque `config/`, `data/` y `logs/` son relativas a esa carpeta.
4. Debe haber un entorno gráfico disponible.

### Opción A: JavaFX con Maven (comando del proyecto)

**Comando:**
```bash
cd cliente
mvn -pl presentacion/app-cliente javafx:run
```

**¿Qué hace?**

- Compila y lanza la **aplicación cliente JavaFX** mediante `javafx-maven-plugin`.
- Carga `config/cliente.properties` (servidor `127.0.0.1:5000` por defecto).
- Abre la pantalla **C1 · Inicio de sesión**.
- Al autenticarse, crea/abre la base local H2 del usuario en `data/cliente_{codigo}.mv.db` y muestra el chat **C2**.
- La terminal queda ocupada mientras la ventana esté abierta. Ciérrala para terminar el cliente.

### Opción B: atajo con `make`

**Comando:**
```bash
make cliente
```

**¿Qué hace?** Desde la raíz, verifica el JDK 21, compila el cliente si hace falta y ejecuta el mismo `javafx:run`.

### Lanzar una segunda instancia en paralelo (dos usuarios)

Abre **otra terminal** y repite exactamente el mismo comando:

**Comando:**
```bash
cd cliente
mvn -pl presentacion/app-cliente javafx:run
```

**¿Qué hace?** Abre una segunda ventana del cliente conectada al mismo servidor. Inicia sesión con **otro usuario** (por ejemplo, Ana y Luis) para probar el foro, los mensajes privados y la lista de conectados.

Notas:

- Cada usuario tiene su propia base H2 (`data/cliente_{codigo}.mv.db`), así que dos usuarios distintos no compiten por los archivos.
- Para abrir **el mismo usuario dos veces** en la misma máquina, añade `;AUTO_SERVER=TRUE` a `h2.url` en `cliente/config/cliente.properties`; H2 bloquea la base si no se habilita el modo multiusuario.
- El servidor admite hasta 10 sesiones simultáneas por defecto.
- Para lanzar dos clientes en segundo plano de una vez: `make demo` (sección 5).

---

## 5. Todo en uno con `make`

El `Makefile` de la raíz automatiza el flujo completo. Requiere JDK 21, Maven 3.9+, Docker (si usas MySQL en contenedor) y que el contenedor `mensajeria-mysql` exista (sección 1).

**Comando:**
```bash
make demo
```

**¿Qué hace?**

1. Verifica el JDK 21.
2. Arranca `mensajeria-mysql` si no está escuchando.
3. Compila el servidor y el cliente si hace falta.
4. Arranca el servidor en segundo plano (log en `.ejecucion/servidor.log`).
5. Abre **2 clientes** JavaFX (logs en `.ejecucion/cliente-1.log` y `cliente-2.log`).

Para abrir más clientes:

**Comando:**
```bash
make demo CLIENTES=3
```

**¿Qué hace?** Levanta la base de datos y el servidor, y abre **3** ventanas del cliente en paralelo.

Otros atajos útiles:

| Comando | ¿Qué hace? |
|---|---|
| `make ayuda` | Muestra todos los objetivos disponibles. |
| `make bd` | Arranca el contenedor MySQL y espera a que acepte conexiones. |
| `make compilar` | Compila e instala servidor y cliente sin pruebas. |
| `make servidor` | Servidor con menú de consola en primer plano. |
| `make servidor-escritorio` | Servidor con vista de escritorio (Swing). |
| `make servidor-fondo` | Servidor en segundo plano (sin terminal interactiva). |
| `make cliente` | Abre un cliente JavaFX. |
| `make estado` | Indica si MySQL, el servidor y los clientes están corriendo. |
| `make pruebas` | Ejecuta las pruebas del servidor y del cliente. |
| `make pruebas-integracion` | Dos clientes contra un servidor real en marcha. |
| `make detener` | Cierra el servidor y los clientes lanzados con `make demo`. |
| `make limpiar` | Borra `target/` y `.ejecucion/` (conserva las bases H2 y los datos de MySQL). |

---

## 6. Usuarios de prueba

Los usuarios se importan desde `servidor/config/usuarios.txt` en el primer arranque. Algunos disponibles:

| Código | Nombre | Contraseña | Rol |
|---|---|---|---|
| `20201001` | Ana Pérez | `clave123` | USUARIO |
| `20201002` | Luis Gómez | `clave456` | USUARIO |
| `20201003` | María Rodríguez | `clave789` | USUARIO |
| `ADMIN01` | Servidor Administrador | `admin2026` | ADMIN |

> Los usuarios se cargan en la base la primera vez. Si cambias `usuarios.txt`, revisa la lógica de importación y los datos ya persistidos.

---

## 7. Verificación, puertos y logs

### Comprobar que el servidor escucha

**Comando:**
```bash
ss -ltnp | grep ':5000'
```

**¿Qué hace?** Lista los sockets TCP en escucha y filtra el puerto `5000`. Debe aparecer un proceso Java. En macOS puedes usar `lsof -nP -iTCP:5000 -sTCP:LISTEN`.

### Comprobar el estado con `make`

**Comando:**
```bash
make estado
```

**¿Qué hace?** Indica si MySQL escucha en `3306`, si el servidor escucha en `5000` y cuántos clientes lanzados con `make demo` siguen activos, además del JDK en uso.

### Puertos y archivos relevantes

| Elemento | Valor por defecto | Dónde se configura |
|---|---|---|
| Puerto TCP del servidor | `5000` | `servidor/config/servidor.properties` → `servidor.puerto` |
| Puerto de MySQL | `3306` | `servidor/config/bd.properties` → `bd.url` |
| Servidor al que se conecta el cliente | `127.0.0.1:5000` | `cliente/config/cliente.properties` y pantalla de login |
| Log del servidor | `servidor/logs/servidor.log` | Rotación diaria, 30 días |
| Log del cliente | `cliente/logs/cliente.log` | Rotación diaria |
| Archivos e imágenes del servidor | `servidor/almacen/` | `archivos.directorio` |
| Base local del cliente | `cliente/data/cliente_{codigo}.mv.db` | `h2.url` |
| Logs de `make demo` | `.ejecucion/*.log` | `Makefile` |

---

## 8. Pruebas automáticas

### Servidor

**Comando:**
```bash
cd servidor
mvn verify
```

**¿Qué hace?** Compila los módulos y ejecuta las pruebas unitarias. Las pruebas marcadas con `@Tag("integracion")` se excluyen por defecto porque necesitan una base de datos real.

### Cliente

**Comando:**
```bash
cd cliente
mvn verify
```

**¿Qué hace?** Compila el cliente y ejecuta sus pruebas. Las pruebas de interfaz que cargan FXML se omiten solas si no hay entorno gráfico.

### Pruebas de integración (opcionales)

Con el servidor en marcha:

**Comando:**
```bash
cd cliente
mvn verify -Pintegracion -pl presentacion/app-cliente -am \
  -Dcliente.e2e.servidor=127.0.0.1:5000
```

**¿Qué hace?** Ejecuta la prueba de integración del cliente contra el servidor real, recorriendo el contrato completo con dos usuarios. Deja mensajes marcados con `e2e-…` en la base del servidor.

Para las pruebas de integración del servidor contra una base real:

**Comando:**
```bash
cd servidor
mvn verify -Pintegracion -pl paquetes/persistencia -am \
  -Dbd.motor=MYSQL \
  -Dbd.url=jdbc:mysql://localhost:3306/mensajeria_test \
  -Dbd.usuario=root \
  -Dbd.clave=secreto
```

**¿Qué hace?** Activa el perfil `integracion` y ejecuta las pruebas que necesitan MySQL/PostgreSQL. Usa una base **solo para pruebas**: se borran sus filas antes de cada prueba.

La relación entre requisitos y pruebas está en `docs/aceptacion_servidor.md`.

---

## 9. Configuración

### Servidor (`servidor/config/servidor.properties`)

| Clave | Valor por defecto | Uso |
|---|---|---|
| `servidor.protocolo` | `TCP` | Transporte disponible. |
| `servidor.puerto` | `5000` | Puerto TCP de escucha. |
| `servidor.maxSesiones` | `10` | Cupo global de sesiones simultáneas. |
| `servidor.maxTramaBytes` | `16777216` | Tamaño máximo de una trama (16 MiB). |
| `servidor.vista` | `CONSOLA` | Vista inicial: `CONSOLA` o `ESCRITORIO`. |
| `cola.capacidad` | `1000` | Capacidad de la cola de mensajes entrantes. |
| `cola.hilosConsumidores` | `4` | Hilos que procesan la cola. |
| `mensajes.maxTextoCaracteres` | `4000` | Máximo de caracteres por mensaje de texto. |
| `mensajes.maxImagenBytes` | `5242880` | Tamaño máximo de imagen (5 MiB). |
| `archivos.directorio` | `./almacen` | Carpeta de archivos e imágenes. |
| `usuarios.archivoInicial` | `./config/usuarios.txt` | Usuarios a importar. |
| `cache.l2.habilitada` | `false` | Activa la caché Redis (L2). |

### Base de datos (`servidor/config/bd.properties`)

| Clave | Uso |
|---|---|
| `bd.motor` | `MYSQL` o `POSTGRESQL`. |
| `bd.url` | URL JDBC de la base. |
| `bd.usuario` / `bd.clave` | Credenciales. |
| `bd.pool` | Tamaño del pool de conexiones. |

### Cliente (`cliente/config/cliente.properties`)

| Clave | Valor por defecto | Uso |
|---|---|---|
| `servidor.host` / `servidor.puerto` | `127.0.0.1` / `5000` | Valor inicial del campo «Servidor» del login. |
| `servidor.protocolo` | `TCP` | Único protocolo disponible. |
| `peticion.timeoutSegundos` | `15` | Espera máxima de una respuesta. |
| `h2.url` | `jdbc:h2:./data/cliente_{codigo}` | Una base H2 por usuario. |
| `h2.usuario` / `h2.clave` | `sa` / vacío | Credenciales de H2. |
| `cache.mensajes.max` | `500` | Conversaciones conservadas en la caché de la sesión. |

---

## 10. Problemas comunes

| Problema | Causa probable | Solución |
|---|---|---|
| `mvn` usa Java 17/27 | `JAVA_HOME` apunta a otro JDK | Exporta `JAVA_HOME` al JDK 21 y vuelve a abrir la terminal. |
| `Connection refused` al arrancar el servidor | MySQL no está levantado o la URL no coincide | Ejecuta `make bd` o `docker start mensajeria-mysql` y revisa `bd.properties`. |
| `Access denied for user` | Usuario/contraseña incorrectos | Crea el usuario con los permisos de la sección 1 o ajusta `bd.properties`. |
| `Port 5000 already in use` | Otro servidor o proceso ocupa el puerto | Detén el proceso (`make detener`) o cambia `servidor.puerto`. |
| El cliente no compila: falta `dto-records` | No se instaló el servidor en `~/.m2` | Ejecuta `cd servidor && mvn clean install -DskipTests`. |
| `No se pudo iniciar el cliente`: no encuentra `config/` | Se lanzó desde otra carpeta | Ejecuta `javafx:run` desde `cliente/`. |
| El servidor no encuentra `config/` | Se lanzó desde la raíz | Ejecuta el JAR desde `servidor/` o usa `make servidor`. |
| La ventana JavaFX no se dibuja bien en Wayland | JavaFX 21 corre sobre XWayland | Prueba añadiendo `-Dprism.order=sw` a la ejecución. |
| H2 bloquea la base de un usuario | Se abrió el mismo usuario dos veces | Añade `;AUTO_SERVER=TRUE` a `h2.url`. |
| El cliente de otra máquina no conecta | Cortafuegos o host incorrecto | Abre el puerto `5000` y escribe la IP del servidor en el login. |

---

## 11. Documentación

| Documento | Contenido |
|---|---|
| `docs/plan_construccion.md` | Plan de construcción por fases. |
| `docs/aceptacion_servidor.md` | Requisitos del servidor y pruebas que los demuestran. |
| `docs/arquitectura/servidor_clases_componentes.md` | Arquitectura, dependencias, flujos y protocolo del servidor. |
| `docs/arquitectura/cliente_clases_componentes.md` | Arquitectura del cliente JavaFX, H2 y catálogo de clases. |
| `docs/arquitectura/modelo_relacional.md` | Tablas, DDL y uso de cada tabla. |
| `docs/mockups/mockups_servidor.md` | Pantallas S1–S4 del servidor. |
| `docs/mockups/mockups_cliente.md` | Pantallas C1–C3 del cliente. |
| `cliente/planCliente.md` | Plan, decisiones y estado del cliente. |
| `cliente/warning.md` | Riesgos y auditorías del cliente. |

---

## Estado del proyecto

- **Servidor:** completo (fases 0–8), con vistas de consola y escritorio, persistencia MySQL/PostgreSQL, caché L1/L2 e informes. Probado de punta a punta.
- **Cliente:** completo (fases 9–12), con login, chat, mensajes privados, imágenes con filtros, informes y persistencia H2. Probado contra el servidor real.
- **Integración:** cliente y servidor se comunican por TCP con el contrato JSON documentado; las pruebas de integración se activan con el perfil `integracion`.
