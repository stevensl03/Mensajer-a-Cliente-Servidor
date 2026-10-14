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
- MySQL 8 (o PostgreSQL) para el servidor (a partir de la Fase 2).
- Redis, opcional (caché L2; se apaga con `cache.l2.habilitada=false`).

## Compilar y probar el servidor

```bash
cd servidor
mvn verify
```

Las rutas de `config/servidor.properties` (`./config`, `./almacen`, `logs/`) son relativas al directorio de trabajo. Por eso el servidor se ejecuta desde `servidor/`. Las carpetas `almacen/` y `logs/` se crean al ejecutarlo y no se versionan.

## Estado

El servidor se construye por fases según `docs/plan_construccion.md`:

- **Terminadas:** Fase 0 (estructura) y Fase 1 (transversales).
- **Siguiente:** Fase 2 (persistencia).
