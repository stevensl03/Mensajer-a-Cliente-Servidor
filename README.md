# Mensajería Académica Unillanos

Aplicación cliente/servidor para enviar mensajes de texto e imágenes a la comunidad académica de la universidad. Está escrita en Java 21 y se comunica por TCP/IP. Está organizada en capas y componentes, con inversión e inyección de dependencias y fábricas de objetos.

- **Servidor:**
  - autentica a los usuarios y limita el número de sesiones;
  - procesa los textos (SHA-256, caracteres y palabras) y las imágenes (SHA-256 y 5 filtros en secuencia);
  - guarda la información en MySQL y en disco, y registra los logs;
  - genera los informes;
  - tiene una vista de consola y una de escritorio.
- **Cliente:** muestra a los usuarios conectados y permite mensajes al foro y privados. Su persistencia local usa H2.

## Estructura del repositorio

```
.
├── docs/                      Documentación del diseño
│   ├── plan_construccion.md   Plan por fases (punto de partida)
│   ├── arquitectura/          Diagramas de clases por componentes y modelo relacional (.drawio + .md)
│   └── mockups/               Pantallas del servidor y del cliente (.drawio + .md)
├── servidor/                  Proyecto Maven del servidor
│   ├── pom.xml                POM padre (versiones de dependencias y módulos)
│   ├── config/                servidor.properties, bd.properties, usuarios.txt
│   ├── transversales/         DTO-Records, UtileriasVarias, Fachada
│   ├── interfaces/            Contratos de los 5 componentes
│   ├── componentes/           Implementaciones de los componentes
│   ├── paquetes/              Mensajes, LogDeEventos, ConexionesClientes, AlmacenarInformacion,
│   │                          Persistencia, FachadaDeServicios
│   └── presentacion/          InicioServidor (Composition Root), VistaConsola, VistaEscritorio
└── cliente/                   Proyecto Maven del cliente (Fase 9)
```

`servidor/` y `cliente/` son proyectos **independientes**: cada uno se compila, prueba y ejecuta por separado. Solo comparten el contrato del protocolo, descrito en `docs/arquitectura/servidor_clases_componentes.md` §7, así que un cliente puede escribirse en otro lenguaje.

## Requisitos

- JDK 21 y Maven 3.9 o superior.
- MySQL 8.0.16 o superior (o PostgreSQL) para el servidor.
- Redis, opcional (caché L2; se apaga con `cache.l2.habilitada=false`).

## Compilar y probar el servidor

```bash
cd servidor
mvn verify
```

### Pruebas de integración (base de datos real)

Las pruebas marcadas con `@Tag("integracion")` necesitan una BD real, así que `mvn verify` las excluye. Se activan con el perfil `integracion` y los datos de conexión:

```bash
cd servidor
mvn verify -Pintegracion -pl paquetes/persistencia -am -Dbd.motor=MYSQL -Dbd.url=jdbc:mysql://localhost:3306/mensajeria_test -Dbd.usuario=root -Dbd.clave=secreto
```

- Usa una base de datos **solo para pruebas**: se borran todas sus filas antes de cada prueba.
- Para PostgreSQL usa `-Dbd.motor=POSTGRESQL` y una URL `jdbc:postgresql://…`.
- El esquema lo crea Flyway (`db/migration/{mysql|postgresql}/V1__esquema.sql`).
- Sin `-Dbd.url`, las pruebas se omiten.
- La caché L2 se prueba contra un Redis real con `-pl paquetes/almacenar-informacion -Dredis.puerto=6379` (sin esa propiedad, la prueba se omite).

Para tener una BD de pruebas rápida con Docker:

```bash
docker run -d --name mensajeria-mysql -e MYSQL_ROOT_PASSWORD=secreto -e MYSQL_DATABASE=mensajeria_test -p 3306:3306 mysql:8.4
```

```bash
docker run -d --name mensajeria-redis -p 6379:6379 redis:7.4
```

Las rutas de `config/servidor.properties` (`./config`, `./almacen`, `logs/`) son relativas al directorio de trabajo. Por eso el servidor se ejecuta desde `servidor/`. Las carpetas `almacen/` y `logs/` se crean al ejecutarlo y no se versionan.

## Estado

El servidor se construye por fases según `docs/plan_construccion.md`:

- **Terminadas:** Fase 0 (estructura), Fase 1 (transversales), Fase 2 (persistencia), Fase 3 (AlmacenarInformacion y caché) y Fase 4a (Servicios).
- **Siguiente:** Fase 4b (Usuarios).
