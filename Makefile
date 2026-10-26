# Mensajería Académica Unillanos: compilar, ejecutar y probar el servidor y el cliente.
#
#   make            muestra la ayuda
#   make demo       levanta la BD, el servidor y dos clientes de una vez
#   make detener    cierra lo que levantó «make demo»
#
# Pensado para Linux y macOS (bash, GNU Make). Requiere JDK 21, Maven 3.9+ y, para la BD, Docker.

SHELL := /bin/bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := ayuda

# --- Configuración (se puede cambiar al invocar: make demo CLIENTES=3) ---------------------------
# JDK 21: el proyecto compila con release 21 y JavaFX 21 (ver cliente/README.md).
JAVA21 ?= $(firstword $(wildcard /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-21-openjdk-amd64 \
          /usr/lib/jvm/java-21* /Library/Java/JavaVirtualMachines/*21*/Contents/Home))
ifneq ($(JAVA21),)
export JAVA_HOME := $(JAVA21)
JAVA := $(JAVA21)/bin/java
else
JAVA := java
endif

MVN            := mvn -q
CONTENEDOR_BD  ?= mensajeria-mysql
PUERTO_BD      ?= 3306
PUERTO         ?= 5000
CLIENTES       ?= 2
SERVIDOR_JAR   := servidor/presentacion/inicio-servidor/target/inicio-servidor-1.0.0.jar
CLIENTE_JAR    := cliente/presentacion/app-cliente/target/app-cliente-1.0.0.jar
# PID y logs de lo que se lanza en segundo plano (no se versiona).
EJECUCION      := .ejecucion

.PHONY: ayuda bd compilar compilar-servidor compilar-cliente servidor servidor-escritorio servidor-fondo \
        cliente clientes demo detener estado pruebas pruebas-servidor pruebas-cliente pruebas-integracion \
        limpiar verificar-java

ayuda:
	@echo "Mensajería Académica Unillanos"
	@echo ""
	@echo "  Todo en uno"
	@echo "    make demo                 BD + servidor en segundo plano + $(CLIENTES) clientes (make demo CLIENTES=3)"
	@echo "    make detener              cierra el servidor y los clientes lanzados con «make demo»"
	@echo "    make estado               muestra qué está corriendo"
	@echo ""
	@echo "  Paso a paso (cada uno en su terminal)"
	@echo "    make bd                   arranca el contenedor de MySQL ($(CONTENEDOR_BD))"
	@echo "    make compilar             compila e instala servidor y cliente (sin pruebas)"
	@echo "    make servidor             servidor con el menú de consola (0 lo detiene)"
	@echo "    make servidor-escritorio  servidor con la vista de escritorio (Swing)"
	@echo "    make cliente              abre un cliente (repítelo en otra terminal para otro usuario)"
	@echo ""
	@echo "  Pruebas"
	@echo "    make pruebas              pruebas del servidor y del cliente"
	@echo "    make pruebas-integracion  dos clientes contra el servidor en marcha"
	@echo ""
	@echo "  Otros"
	@echo "    make limpiar              borra lo compilado (target/) y los registros de ejecución"
	@echo ""
	@echo "  Usuarios de prueba: 20201001/clave123 (Ana), 20201002/clave456 (Luis), 20201003/clave789 (María)"
	@echo "  JDK usado: $(if $(JAVA21),$(JAVA21),java del PATH — instala el JDK 21 o usa make JAVA21=/ruta)"

verificar-java:
	@version=$$("$(JAVA)" -version 2>&1 | head -1)
	if [[ "$$version" != *'"21'* ]]; then
	  echo "Se necesita el JDK 21 y se encontró: $$version"
	  echo "Instálalo o indica la ruta: make $(MAKECMDGOALS) JAVA21=/ruta/al/jdk-21"
	  exit 1
	fi

# --- Base de datos ----------------------------------------------------------------------------------
bd:
	@if (echo > /dev/tcp/127.0.0.1/$(PUERTO_BD)) 2>/dev/null; then
	  echo "MySQL ya escucha en el puerto $(PUERTO_BD)."
	  exit 0
	fi
	if ! command -v docker >/dev/null; then
	  echo "No hay MySQL en el puerto $(PUERTO_BD) ni Docker para arrancarlo (ver README.md)."
	  exit 1
	fi
	if ! docker ps -a --format '{{.Names}}' | grep -qx '$(CONTENEDOR_BD)'; then
	  echo "No existe el contenedor $(CONTENEDOR_BD). Créalo como indica README.md y configura servidor/config/bd.properties."
	  exit 1
	fi
	echo "Arrancando el contenedor $(CONTENEDOR_BD)…"
	docker start $(CONTENEDOR_BD) >/dev/null
	for i in $$(seq 1 60); do
	  (echo > /dev/tcp/127.0.0.1/$(PUERTO_BD)) 2>/dev/null && { echo "MySQL listo."; exit 0; }
	  sleep 1
	done
	echo "MySQL no respondió en 60 s."
	exit 1

# --- Compilación ------------------------------------------------------------------------------------
compilar: compilar-servidor compilar-cliente

compilar-servidor: verificar-java
	@echo "Compilando el servidor…"
	cd servidor
	$(MVN) package -DskipTests
	# El cliente reutiliza los DTO del servidor desde el repositorio local de Maven.
	$(MVN) -pl transversales/dto-records -am install -DskipTests
	echo "Servidor listo: $(SERVIDOR_JAR)"

compilar-cliente: verificar-java
	@[[ -f ~/.m2/repository/com/universidad/dto-records/1.0.0/dto-records-1.0.0.jar ]] || $(MAKE) --no-print-directory compilar-servidor
	echo "Compilando el cliente…"
	cd cliente
	$(MVN) install -DskipTests
	echo "Cliente listo."

$(SERVIDOR_JAR):
	@$(MAKE) --no-print-directory compilar-servidor

$(CLIENTE_JAR):
	@$(MAKE) --no-print-directory compilar-cliente

# --- Ejecución --------------------------------------------------------------------------------------
servidor: verificar-java bd $(SERVIDOR_JAR)
	@cd servidor
	"$(JAVA)" -jar presentacion/inicio-servidor/target/inicio-servidor-1.0.0.jar --vista=CONSOLA

servidor-escritorio: verificar-java bd $(SERVIDOR_JAR)
	@cd servidor
	"$(JAVA)" -jar presentacion/inicio-servidor/target/inicio-servidor-1.0.0.jar --vista=ESCRITORIO

cliente: verificar-java $(CLIENTE_JAR)
	@cd cliente
	$(MVN) -pl presentacion/app-cliente javafx:run

# Servidor sin terminal: la consola necesita una entrada abierta, si no se apagaría al leer fin de archivo.
servidor-fondo: verificar-java bd $(SERVIDOR_JAR)
	@mkdir -p $(EJECUCION)
	if (echo > /dev/tcp/127.0.0.1/$(PUERTO)) 2>/dev/null; then
	  echo "Ya hay un servidor en el puerto $(PUERTO)."
	  exit 0
	fi
	echo "Arrancando el servidor en segundo plano (log: $(EJECUCION)/servidor.log)…"
	raiz=$$(pwd)
	cd servidor
	nohup bash -c 'exec "$(JAVA)" -jar presentacion/inicio-servidor/target/inicio-servidor-1.0.0.jar --vista=CONSOLA < <(tail -f /dev/null)' \
	  > "$$raiz/$(EJECUCION)/servidor.log" 2>&1 &
	echo $$! > "$$raiz/$(EJECUCION)/servidor.pid"
	for i in $$(seq 1 60); do
	  (echo > /dev/tcp/127.0.0.1/$(PUERTO)) 2>/dev/null && { echo "Servidor listo en el puerto $(PUERTO)."; exit 0; }
	  kill -0 $$(cat "$$raiz/$(EJECUCION)/servidor.pid") 2>/dev/null || { echo "El servidor terminó al arrancar:"; tail -20 "$$raiz/$(EJECUCION)/servidor.log"; exit 1; }
	  sleep 1
	done
	echo "El servidor no abrió el puerto $(PUERTO) en 60 s (ver $(EJECUCION)/servidor.log)."
	exit 1

clientes: verificar-java $(CLIENTE_JAR)
	@mkdir -p $(EJECUCION)
	raiz=$$(pwd)
	cd cliente
	for n in $$(seq 1 $(CLIENTES)); do
	  nohup $(MVN) -pl presentacion/app-cliente javafx:run > "$$raiz/$(EJECUCION)/cliente-$$n.log" 2>&1 &
	  echo $$! > "$$raiz/$(EJECUCION)/cliente-$$n.pid"
	  echo "Cliente $$n abierto (log: $(EJECUCION)/cliente-$$n.log)."
	  sleep 2
	done

demo: servidor-fondo clientes
	@echo ""
	echo "Listo: entra en cada ventana con un usuario distinto, por ejemplo"
	echo "  20201001 / clave123 (Ana)   y   20201002 / clave456 (Luis)"
	echo "Cuando termines: make detener"

detener:
	@shopt -s nullglob
	for archivo in $(EJECUCION)/cliente-*.pid; do
	  pid=$$(cat "$$archivo")
	  # El cliente es un Maven que lanzó la JVM de JavaFX: se cierran los dos.
	  pkill -TERM -P "$$pid" 2>/dev/null || true
	  kill -TERM "$$pid" 2>/dev/null && echo "Cliente cerrado ($$archivo)." || true
	  rm -f "$$archivo"
	done
	if [[ -f $(EJECUCION)/servidor.pid ]]; then
	  pid=$$(cat $(EJECUCION)/servidor.pid)
	  # El «tail» que mantiene abierta la entrada de la consola es hijo del servidor: se anota antes de detenerlo.
	  tails=$$(pgrep -P "$$pid" tail || true)
	  # SIGTERM: el servidor avisa SESION_CERRADA a los clientes y se apaga en orden.
	  if kill -TERM "$$pid" 2>/dev/null; then
	    for i in $$(seq 1 15); do kill -0 "$$pid" 2>/dev/null || break; sleep 1; done
	    echo "Servidor detenido (apagado ordenado)."
	  else
	    echo "El servidor ya no estaba corriendo."
	  fi
	  [[ -z "$$tails" ]] || kill -TERM $$tails 2>/dev/null || true
	  rm -f $(EJECUCION)/servidor.pid
	else
	  echo "No hay un servidor lanzado con «make demo»."
	fi

estado:
	@abierto() { (echo > /dev/tcp/127.0.0.1/$$1) 2>/dev/null; }
	abierto $(PUERTO_BD) && echo "MySQL:    escuchando en $(PUERTO_BD)" || echo "MySQL:    detenido"
	abierto $(PUERTO) && echo "Servidor: escuchando en $(PUERTO)" || echo "Servidor: detenido"
	shopt -s nullglob
	n=0; for archivo in $(EJECUCION)/cliente-*.pid; do kill -0 $$(cat "$$archivo") 2>/dev/null && n=$$((n+1)); done
	echo "Clientes lanzados con make demo: $$n"
	echo "JDK: $(JAVA)"

# --- Pruebas ----------------------------------------------------------------------------------------
pruebas: pruebas-servidor pruebas-cliente

pruebas-servidor: verificar-java
	@echo "Pruebas del servidor…"
	cd servidor
	$(MVN) verify
	echo "Servidor: pruebas en verde."

pruebas-cliente: verificar-java
	@[[ -f ~/.m2/repository/com/universidad/dto-records/1.0.0/dto-records-1.0.0.jar ]] || $(MAKE) --no-print-directory compilar-servidor
	echo "Pruebas del cliente…"
	cd cliente
	$(MVN) verify
	echo "Cliente: pruebas en verde."

pruebas-integracion: verificar-java $(CLIENTE_JAR)
	@if ! (echo > /dev/tcp/127.0.0.1/$(PUERTO)) 2>/dev/null; then
	  echo "El servidor no está corriendo: make servidor-fondo (o make servidor en otra terminal)."
	  exit 1
	fi
	cd cliente
	$(MVN) verify -Pintegracion -pl presentacion/app-cliente -am -Dcliente.e2e.servidor=127.0.0.1:$(PUERTO)
	echo "Integración con el servidor real: en verde."

# --- Limpieza ---------------------------------------------------------------------------------------
limpiar:
	@cd servidor && $(MVN) clean
	cd ../cliente && $(MVN) clean
	cd .. && rm -rf $(EJECUCION)
	echo "Limpio (las bases H2 de cliente/data y los datos de MySQL se conservan)."
