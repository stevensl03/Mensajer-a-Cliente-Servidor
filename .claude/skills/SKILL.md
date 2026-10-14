---
name: java-chat-client-server
description: Arquitectura y buenas prácticas (SOLID) para el chat cliente-servidor multiprotocolo en Java 21 sin frameworks, con transportes TCP, UDP y gRPC. Úsala al crear o modificar mensajes del protocolo, códecs, sockets, sesiones, el servicio de chat, clientes o tests, y al revisar código de red o concurrencia en este proyecto.
---

# Chat cliente-servidor multiprotocolo (TCP · UDP · gRPC) en Java 21

Proyecto profesional. Objetivo: **un único núcleo de chat independiente del transporte**, con adaptadores TCP, UDP y gRPC intercambiables. Un usuario conectado por UDP debe poder chatear en la misma sala con otro conectado por TCP o gRPC.

## Restricciones no negociables

- **Java 21 LTS** (`maven.compiler.release=21`). Prohibido usar APIs *preview* o posteriores a 21:
  - ❌ variables/patrones sin nombre `_` (Java 22), `StructuredTaskScope`, `ScopedValue`, string templates, Stream Gatherers (24), `import module`, cuerpos de constructor flexibles (25), patrones primitivos.
  - ✅ `record`, record patterns, `sealed`, `switch` con pattern matching, hilos virtuales, `SequencedCollection` (`reversed()`, `getFirst()`), text blocks, `var`.
- **Sin frameworks.** Solo JDK, salvo:
  - gRPC/Protobuf (`grpc-netty-shaded`, `grpc-protobuf`, `grpc-stub`, `protobuf-java`), **confinados al módulo `transport-grpc`**.
  - JUnit 5 en ámbito `test`.
  - Logging con `System.Logger` (sin SLF4J/Log4j).
- **Nunca inventes números de versión** de dependencias o plugins: verifica la última estable en Maven Central o pregunta.
- **Nunca uses serialización nativa de Java** (`ObjectInputStream`) en la red: es un vector de ejecución remota de código.

## Estructura de módulos (Maven multi-módulo)

```
chat/
├── pom.xml                 padre: release 21, versiones centralizadas
├── chat-protocol/          mensajes (sealed + records), Limits, ErrorCode, BinaryCodec, framing   — sin dependencias
├── chat-transport-api/     puertos: Session, ChatHandler, ServerTransport, ClientTransport,
│                           MessageSink, DefaultSession                                            — depende de protocol
├── chat-core/              ChatService (salas, usuarios, deduplicación)                           — protocol + transport-api
├── transport-tcp/          TcpServerTransport, TcpClientTransport                                 — protocol + transport-api
├── transport-udp/          UdpServerTransport, UdpClientTransport                                 — protocol + transport-api
├── transport-grpc/         chat.proto, GrpcMapper, GrpcServerTransport, GrpcClientTransport       — + gRPC/Protobuf
├── chat-server/            raíz de composición del servidor (main)
└── chat-client/            ChatClient + CLI, raíz de composición del cliente
```

**Regla de dependencias (DIP):** `chat-core` nunca depende de `transport-*`, y `transport-*` nunca depende de `chat-core`. Ambos se conocen solo a través de `chat-transport-api`. Únicamente `chat-server` y `chat-client` ven todo y lo cablean.

```
            chat-protocol
                 ▲
         chat-transport-api
          ▲               ▲
     chat-core     transport-tcp / -udp / -grpc
          ▲               ▲
          └── chat-server ┘     (chat-client análogo)
```

## Comparativa de transportes (diseña según esto)

| Aspecto | TCP | UDP | gRPC (HTTP/2) |
|---|---|---|---|
| Fiabilidad / orden | Garantizados | **Ninguno**: pérdida, duplicados, desorden | Garantizados |
| Delimitación de mensajes | Manual: prefijo de longitud | 1 datagrama = 1 mensaje | La da Protobuf |
| Sesión | La conexión | **Inventada**: dirección remota + `Join` + TTL | El stream bidireccional |
| Detección de desconexión | EOF / timeout de lectura | Solo por inactividad (heartbeat) | `onError` / `onCompleted` |
| Tamaño máximo | Limitado por nosotros | ≤ 1200 bytes (evitar fragmentación IP) | `maxInboundMessageSize` |
| Cifrado | TLS (`SSLServerSocket`, JDK) | DTLS (`SSLEngine`, complejo) | TLS integrado |

Consecuencia: **los límites de tamaño del contrato los fija el transporte más restrictivo (UDP)**, para que todos los transportes acepten exactamente los mismos mensajes (ver LSP).

## 1. Contrato del protocolo (`chat-protocol`)

Mensajes separados por dirección: el compilador impide que el servidor “envíe un Join” o que el cliente envíe un `TextDelivered`.

```java
public sealed interface ClientMessage {
    record Join(String user, String room) implements ClientMessage {
        public Join {
            Limits.requireText(user, "user", Limits.MAX_USER_BYTES);
            Limits.requireText(room, "room", Limits.MAX_ROOM_BYTES);
        }
    }
    record Leave(String room) implements ClientMessage {
        public Leave { Limits.requireText(room, "room", Limits.MAX_ROOM_BYTES); }
    }
    record SendText(String messageId, String room, String body) implements ClientMessage {
        public SendText {
            Limits.requireText(messageId, "messageId", Limits.MAX_ID_BYTES);
            Limits.requireText(room, "room", Limits.MAX_ROOM_BYTES);
            Limits.requireText(body, "body", Limits.MAX_BODY_BYTES);
        }
    }
    record Ping(long nonce) implements ClientMessage {}
}

public sealed interface ServerMessage {
    record Welcome(String sessionId) implements ServerMessage {}
    record TextDelivered(String messageId, String room, String from, String body, long sentAtEpochMs)
            implements ServerMessage {}
    record Ack(String messageId) implements ServerMessage {}
    record Pong(long nonce) implements ServerMessage {}
    record UserJoined(String room, String user) implements ServerMessage {}
    record UserLeft(String room, String user) implements ServerMessage {}
    record Failure(ErrorCode code, String detail) implements ServerMessage {}
}
```

Límites centralizados (medidos en **bytes UTF-8**, no en caracteres):

```java
public final class Limits {
    private Limits() {}

    public static final int MAX_USER_BYTES  = 32;
    public static final int MAX_ROOM_BYTES  = 64;
    public static final int MAX_ID_BYTES    = 64;
    public static final int MAX_BODY_BYTES  = 900;
    public static final int MAX_FRAME_BYTES = 1200; // cabe en un datagrama UDP sin fragmentar

    public static String requireText(String value, String field, int maxBytes) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " vacío");
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > maxBytes) throw new IllegalArgumentException(field + " excede " + maxBytes + " bytes");
        return value;
    }
}
```

Códigos de error con **valor de cable explícito** (nunca `ordinal()`: reordenar el enum rompería clientes):

```java
public enum ErrorCode {
    INVALID_MESSAGE(1), NOT_JOINED(2), USER_TAKEN(3), RATE_LIMITED(4), INTERNAL(99);

    private final int wire;
    ErrorCode(int wire) { this.wire = wire; }
    public int wire() { return wire; }

    public static ErrorCode fromWire(int wire) {
        for (var code : values()) if (code.wire == wire) return code;
        return INTERNAL;
    }
}
```

### Formato binario (TCP y UDP)

`[versión:1B][tipo:1B][campos...]`. Cadenas como `[longitud:u16][bytes UTF-8]`. **No uses `writeUTF`**: escribe UTF-8 *modificado*, incompatible con otros lenguajes.

```java
public final class BinaryCodec implements ServerCodec, ClientCodec {
    static final int VERSION = 1;
    // Tipos ClientMessage: 0x01..0x3F · Tipos ServerMessage: 0x41..0x7F

    @Override
    public byte[] encode(ServerMessage message) {
        var buffer = new ByteArrayOutputStream(256);
        try (var out = new DataOutputStream(buffer)) {
            out.writeByte(VERSION);
            switch (message) {
                case ServerMessage.Welcome(var sessionId) -> {
                    out.writeByte(0x41); writeString(out, sessionId);
                }
                case ServerMessage.TextDelivered(var id, var room, var from, var body, var sentAt) -> {
                    out.writeByte(0x42);
                    writeString(out, id); writeString(out, room); writeString(out, from); writeString(out, body);
                    out.writeLong(sentAt);
                }
                case ServerMessage.Ack(var id) -> { out.writeByte(0x43); writeString(out, id); }
                case ServerMessage.Pong(var nonce) -> { out.writeByte(0x44); out.writeLong(nonce); }
                case ServerMessage.UserJoined(var room, var user) -> {
                    out.writeByte(0x45); writeString(out, room); writeString(out, user);
                }
                case ServerMessage.UserLeft(var room, var user) -> {
                    out.writeByte(0x46); writeString(out, room); writeString(out, user);
                }
                case ServerMessage.Failure(var code, var detail) -> {
                    out.writeByte(0x47); out.writeByte(code.wire()); writeString(out, detail);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // no ocurre escribiendo en memoria
        }
        return buffer.toByteArray();
    }

    @Override
    public ClientMessage decodeClient(byte[] frame) throws ChatProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(frame))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new ChatProtocolException("Versión no soportada: " + version);
            int type = in.readUnsignedByte();
            ClientMessage message = switch (type) {
                case 0x01 -> new ClientMessage.Join(readString(in), readString(in));
                case 0x02 -> new ClientMessage.Leave(readString(in));
                case 0x03 -> new ClientMessage.SendText(readString(in), readString(in), readString(in));
                case 0x04 -> new ClientMessage.Ping(in.readLong());
                default   -> throw new ChatProtocolException("Tipo desconocido: " + type);
            };
            if (in.available() > 0) throw new ChatProtocolException("Bytes sobrantes en el mensaje");
            return message;
        } catch (ChatProtocolException e) {
            throw e;
        } catch (EOFException e) {
            throw new ChatProtocolException("Mensaje truncado", e);
        } catch (IllegalArgumentException e) {           // validación de los records
            throw new ChatProtocolException("Mensaje inválido: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ChatProtocolException("Error leyendo mensaje", e);
        }
    }

    // encode(ClientMessage) y decodeServer(byte[]) son simétricos.

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > Limits.MAX_BODY_BYTES) throw new IllegalArgumentException("Cadena demasiado larga");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > Limits.MAX_BODY_BYTES) throw new ChatProtocolException("Cadena demasiado larga: " + length);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

public final class ChatProtocolException extends IOException {
    public ChatProtocolException(String message) { super(message); }
    public ChatProtocolException(String message, Throwable cause) { super(message, cause); }
}
```

### Framing TCP (prefijo de longitud)

TCP es un flujo de bytes: sin framing, dos mensajes pueden llegar pegados o uno partido.

```java
public final class LengthPrefixedFraming {
    private final int maxFrameBytes;

    public LengthPrefixedFraming(int maxFrameBytes) { this.maxFrameBytes = maxFrameBytes; }

    public void write(DataOutputStream out, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    public byte[] read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > maxFrameBytes) {  // protege contra asignaciones gigantes
            throw new ChatProtocolException("Longitud de frame inválida: " + length);
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        return payload;
    }
}
```

### Contrato gRPC (`transport-grpc/src/main/proto/chat.proto`)

```proto
syntax = "proto3";
package chat.v1;

option java_multiple_files = true;
option java_package = "com.example.chat.grpc.v1";

service ChatGateway {
  rpc Connect(stream ClientEnvelope) returns (stream ServerEnvelope);
}

message ClientEnvelope {
  oneof payload { Join join = 1; Leave leave = 2; SendText send_text = 3; Ping ping = 4; }
}
message ServerEnvelope {
  oneof payload {
    Welcome welcome = 1; TextDelivered text = 2; Ack ack = 3; Pong pong = 4;
    UserJoined user_joined = 5; UserLeft user_left = 6; Failure failure = 7;
  }
}

message Join { string user = 1; string room = 2; }
message Leave { string room = 1; }
message SendText { string message_id = 1; string room = 2; string body = 3; }
message Ping { int64 nonce = 1; }
message Welcome { string session_id = 1; }
message TextDelivered { string message_id = 1; string room = 2; string from = 3; string body = 4; int64 sent_at_epoch_ms = 5; }
message Ack { string message_id = 1; }
message Pong { int64 nonce = 1; }
message UserJoined { string room = 1; string user = 2; }
message UserLeft { string room = 1; string user = 2; }
message Failure { int32 code = 1; string detail = 2; }
```

Reglas Protobuf: nunca reutilices ni renumeres un número de campo; marca los eliminados con `reserved`. El servicio se llama `ChatGateway` para no chocar con `ChatService` del dominio. Las clases generadas **no salen de `transport-grpc`**: `GrpcMapper` las traduce a `ClientMessage`/`ServerMessage`.

## 2. Puertos (`chat-transport-api`)

```java
public record SessionId(String value) {
    public static SessionId random() { return new SessionId(UUID.randomUUID().toString()); }
}

/** Un cliente conectado, visto desde el núcleo. Independiente del protocolo. */
public interface Session {
    SessionId id();
    /** No bloqueante: encola. Si el cliente es demasiado lento, la sesión se cierra. */
    void send(ServerMessage message);
    /** Idempotente. Descarta mensajes aún no enviados. */
    void close();
}

/** Lo que el núcleo ofrece a los transportes. */
public interface ChatHandler {
    /** Nunca debe bloquear: se invoca desde el hilo lector del transporte. */
    void onMessage(Session from, ClientMessage message);
    /** Idempotente: puede invocarse más de una vez por sesión. */
    void onDisconnect(Session session);
}

public interface ServerTransport extends AutoCloseable {
    void start(ChatHandler handler) throws IOException;
    InetSocketAddress localAddress();   // permite puerto 0 en tests
    @Override void close();
}

/** Escritura física hacia un cliente. Solo la usa el hilo escritor de la sesión. */
public interface MessageSink {
    void write(ServerMessage message) throws IOException;
    void close();

    @FunctionalInterface
    interface IoConsumer<T> { void accept(T value) throws IOException; }

    static MessageSink of(IoConsumer<ServerMessage> writer, Runnable closer) {
        return new MessageSink() {
            @Override public void write(ServerMessage message) throws IOException { writer.accept(message); }
            @Override public void close() { closer.run(); }
        };
    }
}
```

### `DefaultSession`: una cola y un único hilo escritor por sesión

Resuelve tres problemas a la vez: escrituras TCP intercaladas entre hilos, `StreamObserver` de gRPC (que **no es thread-safe**) y clientes lentos que bloquearían el broadcast a toda la sala.

```java
public final class DefaultSession implements Session {
    private static final System.Logger LOG = System.getLogger(DefaultSession.class.getName());

    private final SessionId id;
    private final MessageSink sink;
    private final BlockingQueue<ServerMessage> outbox;
    private final Consumer<Session> onClosed;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile Thread writer;

    private DefaultSession(SessionId id, MessageSink sink, int capacity, Consumer<Session> onClosed) {
        this.id = id;
        this.sink = sink;
        this.outbox = new ArrayBlockingQueue<>(capacity);
        this.onClosed = onClosed;
    }

    /** Fábrica: nunca arranques hilos en el constructor (fuga de {@code this}). */
    public static DefaultSession start(SessionId id, MessageSink sink, int capacity, Consumer<Session> onClosed) {
        var session = new DefaultSession(id, sink, capacity, onClosed);
        session.writer = Thread.ofVirtual().name("writer-" + id.value()).start(session::drain);
        return session;
    }

    @Override public SessionId id() { return id; }

    @Override
    public void send(ServerMessage message) {
        if (!open.get()) return;
        if (!outbox.offer(message)) {
            LOG.log(System.Logger.Level.WARNING, "Sesión {0} lenta: cola llena, se cierra", id.value());
            close();
        }
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            var thread = writer;
            if (thread != null) thread.interrupt();
        }
    }

    private void drain() {
        try {
            while (open.get()) {
                sink.write(outbox.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();                // salida normal tras close()
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "Escritura fallida en sesión " + id.value(), e);
        } finally {
            open.set(false);
            sink.close();                                      // solo este hilo toca el sink
            onClosed.accept(this);                             // único camino de desconexión
        }
    }
}
```

## 3. Núcleo (`chat-core`)

```java
public final class ChatService implements ChatHandler {
    private final Map<SessionId, String> users = new ConcurrentHashMap<>();
    private final Map<String, Set<Session>> rooms = new ConcurrentHashMap<>();
    private final RecentIds recentIds;   // deduplica reintentos (UDP)
    private final Clock clock;

    public ChatService(RecentIds recentIds, Clock clock) {
        this.recentIds = recentIds;
        this.clock = clock;
    }

    @Override
    public void onMessage(Session from, ClientMessage message) {
        switch (message) {   // exhaustivo, sin default: un mensaje nuevo no compila hasta manejarse
            case ClientMessage.Join(var user, var room)          -> join(from, user, room);
            case ClientMessage.Leave(var room)                   -> leave(from, room);
            case ClientMessage.SendText(var id, var room, var body) -> sendText(from, id, room, body);
            case ClientMessage.Ping(var nonce)                   -> from.send(new ServerMessage.Pong(nonce));
        }
    }

    private void join(Session from, String user, String room) {
        var current = users.putIfAbsent(from.id(), user);
        if (current != null && !current.equals(user)) {
            from.send(new ServerMessage.Failure(ErrorCode.USER_TAKEN, "La sesión ya es " + current));
            return;
        }
        var members = rooms.computeIfAbsent(room, key -> ConcurrentHashMap.newKeySet());
        if (members.add(from)) {
            broadcast(members, new ServerMessage.UserJoined(room, user), null);
        }
    }

    private void sendText(Session from, String messageId, String room, String body) {
        var user = users.get(from.id());
        var members = rooms.getOrDefault(room, Set.of());
        if (user == null || !members.contains(from)) {
            from.send(new ServerMessage.Failure(ErrorCode.NOT_JOINED, room));
            return;
        }
        if (recentIds.markSeen(from.id(), messageId)) {       // primera vez que lo vemos
            // El remitente lo fija el servidor: nunca se confía en un "from" enviado por el cliente.
            var delivered = new ServerMessage.TextDelivered(messageId, room, user, body, clock.millis());
            broadcast(members, delivered, from);
        }
        from.send(new ServerMessage.Ack(messageId));          // también en duplicados: pudo perderse el Ack
    }

    private static void broadcast(Set<Session> members, ServerMessage message, Session except) {
        for (var member : members) {
            if (!member.equals(except)) member.send(message);  // send() solo encola: nunca bloquea
        }
    }

    // leave(...) y onDisconnect(...): quitar la sesión de las salas, notificar UserLeft,
    // eliminar salas vacías con rooms.computeIfPresent(room, (k, v) -> v.isEmpty() ? null : v).
    // onDisconnect debe ser idempotente.
}
```

`RecentIds`: LRU acotado (`LinkedHashMap` con `removeEldestEntry`) protegido por `ReentrantLock`; `markSeen` devuelve `true` solo la primera vez que ve el par `(sessionId, messageId)`.

## 4. Adaptadores de servidor

### TCP: un hilo virtual lector por conexión

```java
public final class TcpServerTransport implements ServerTransport {
    private static final System.Logger LOG = System.getLogger(TcpServerTransport.class.getName());
    private static final int OUTBOX_CAPACITY = 256;

    private final int port;
    private final BinaryCodec codec;
    private final LengthPrefixedFraming framing;
    private final Duration idleTimeout;
    private final ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor();
    private volatile ServerSocket serverSocket;

    public TcpServerTransport(int port, BinaryCodec codec, LengthPrefixedFraming framing, Duration idleTimeout) {
        this.port = port;
        this.codec = codec;
        this.framing = framing;
        this.idleTimeout = idleTimeout;
    }

    @Override
    public void start(ChatHandler handler) throws IOException {
        serverSocket = new ServerSocket(port);
        Thread.ofVirtual().name("tcp-acceptor").start(() -> acceptLoop(handler));
    }

    private void acceptLoop(ChatHandler handler) {
        while (!serverSocket.isClosed()) {
            try {
                var socket = serverSocket.accept();
                connections.submit(() -> serve(socket, handler));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) LOG.log(System.Logger.Level.WARNING, "Fallo en accept", e);
            }
        }
    }

    private void serve(Socket socket, ChatHandler handler) {
        DefaultSession session = null;
        try (socket) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout((int) idleTimeout.toMillis());   // el cliente debe enviar Ping antes
            var in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            session = DefaultSession.start(SessionId.random(),
                    MessageSink.of(msg -> framing.write(out, codec.encode(msg)), () -> closeQuietly(socket)),
                    OUTBOX_CAPACITY, handler::onDisconnect);
            while (!socket.isClosed()) {
                handler.onMessage(session, codec.decodeClient(framing.read(in)));
            }
        } catch (EOFException | SocketTimeoutException e) {
            LOG.log(System.Logger.Level.DEBUG, "Conexión cerrada o inactiva: {0}", socket.getRemoteSocketAddress());
        } catch (IOException e) {
            LOG.log(System.Logger.Level.INFO, "Conexión terminada: " + socket.getRemoteSocketAddress(), e);
        } finally {
            if (session != null) session.close();
        }
    }

    @Override public InetSocketAddress localAddress() {
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    @Override
    public void close() {
        closeQuietly(serverSocket);
        connections.close();   // ExecutorService es AutoCloseable desde Java 19
    }

    private static void closeQuietly(Closeable closeable) {
        try { if (closeable != null) closeable.close(); } catch (IOException ignored) { }
    }
}
```

### UDP: sesiones inventadas por dirección remota + TTL

```java
public final class UdpServerTransport implements ServerTransport {
    private static final System.Logger LOG = System.getLogger(UdpServerTransport.class.getName());
    private static final int OUTBOX_CAPACITY = 256;

    private record UdpPeer(Session session, AtomicLong lastSeenNanos) {}

    private final int port;
    private final BinaryCodec codec;
    private final Duration sessionTtl;
    private final Map<SocketAddress, UdpPeer> peers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private volatile DatagramSocket socket;

    // constructor omitido

    @Override
    public void start(ChatHandler handler) throws IOException {
        socket = new DatagramSocket(port);
        Thread.ofVirtual().name("udp-receiver").start(() -> receiveLoop(handler));
        sweeper.scheduleAtFixedRate(this::evictIdle, 5, 5, TimeUnit.SECONDS);
    }

    private void receiveLoop(ChatHandler handler) {
        var buffer = new byte[Limits.MAX_FRAME_BYTES + 1];   // +1 para detectar sobredimensionados
        while (!socket.isClosed()) {
            var packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                if (packet.getLength() > Limits.MAX_FRAME_BYTES) continue;
                var message = codec.decodeClient(Arrays.copyOf(packet.getData(), packet.getLength()));
                var address = packet.getSocketAddress();
                // Solo un Join crea sesión: evita agotar memoria con remitentes falsificados.
                var peer = message instanceof ClientMessage.Join
                        ? peers.computeIfAbsent(address, a -> newPeer(a, handler))
                        : peers.get(address);
                if (peer == null) continue;
                peer.lastSeenNanos().set(System.nanoTime());
                handler.onMessage(peer.session(), message);
            } catch (ChatProtocolException e) {
                LOG.log(System.Logger.Level.DEBUG, "Datagrama inválido descartado: {0}", e.getMessage());
            } catch (IOException e) {
                if (!socket.isClosed()) LOG.log(System.Logger.Level.WARNING, "Fallo recibiendo", e);
            }
        }
    }

    private UdpPeer newPeer(SocketAddress address, ChatHandler handler) {
        var sink = MessageSink.of(msg -> {
            byte[] bytes = codec.encode(msg);
            socket.send(new DatagramPacket(bytes, bytes.length, address));
        }, () -> peers.remove(address));
        var session = DefaultSession.start(SessionId.random(), sink, OUTBOX_CAPACITY, handler::onDisconnect);
        return new UdpPeer(session, new AtomicLong(System.nanoTime()));
    }

    private void evictIdle() {
        try {   // una excepción no capturada cancelaría todas las ejecuciones futuras
            long now = System.nanoTime();
            peers.forEach((address, peer) -> {
                if (now - peer.lastSeenNanos().get() > sessionTtl.toNanos()) peer.session().close();
            });
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Fallo expirando sesiones UDP", e);
        }
    }

    // localAddress() y close() análogos a TCP; close() también apaga el sweeper.
}
```

Riesgos UDP que el agente debe tener presentes: suplantación de IP y amplificación (no respondas con más bytes de los recibidos a quien no ha hecho `Join`), duplicados (deduplicación en el núcleo) y desorden (si el orden importa, añade un número de secuencia por emisor).

### gRPC: stream bidireccional

```java
final class ChatGatewayEndpoint extends ChatGatewayGrpc.ChatGatewayImplBase {
    private static final int OUTBOX_CAPACITY = 256;
    private final ChatHandler handler;
    private final GrpcMapper mapper;

    ChatGatewayEndpoint(ChatHandler handler, GrpcMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    @Override
    public StreamObserver<ClientEnvelope> connect(StreamObserver<ServerEnvelope> responses) {
        var sink = MessageSink.of(
                msg -> responses.onNext(mapper.toProto(msg)),         // solo desde el hilo escritor
                () -> { try { responses.onCompleted(); } catch (RuntimeException ignored) { } });
        var session = DefaultSession.start(SessionId.random(), sink, OUTBOX_CAPACITY, handler::onDisconnect);

        return new StreamObserver<>() {
            @Override public void onNext(ClientEnvelope envelope) {
                try {
                    handler.onMessage(session, mapper.fromProto(envelope));
                } catch (IllegalArgumentException e) {
                    session.send(new ServerMessage.Failure(ErrorCode.INVALID_MESSAGE, e.getMessage()));
                }
            }
            @Override public void onError(Throwable t) { session.close(); }
            @Override public void onCompleted()        { session.close(); }
        };
    }
}

public final class GrpcMapper {
    public ClientMessage fromProto(ClientEnvelope envelope) {
        return switch (envelope.getPayloadCase()) {
            case JOIN      -> new ClientMessage.Join(envelope.getJoin().getUser(), envelope.getJoin().getRoom());
            case LEAVE     -> new ClientMessage.Leave(envelope.getLeave().getRoom());
            case SEND_TEXT -> {
                var text = envelope.getSendText();
                yield new ClientMessage.SendText(text.getMessageId(), text.getRoom(), text.getBody());
            }
            case PING            -> new ClientMessage.Ping(envelope.getPing().getNonce());
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException("Envelope sin payload");
        };
    }
    // toProto(ServerMessage): switch exhaustivo sobre ServerMessage.
}
```

`GrpcServerTransport` construye el servidor con `ServerBuilder.forPort(port)`, `.executor(Executors.newVirtualThreadPerTaskExecutor())`, `.maxInboundMessageSize(Limits.MAX_FRAME_BYTES)` y `.addService(new ChatGatewayEndpoint(...))`; en `close()` llama `shutdown()` y `awaitTermination(...)` antes de `shutdownNow()`.

## 5. Lado cliente (`chat-client` + adaptadores)

```java
/**
 * Contrato común (LSP). Toda implementación DEBE garantizar:
 *  - SendText: entrega al-menos-una-vez; se confirma con Ack o se notifica onFailure tras agotar reintentos.
 *  - Los mensajes recibidos llegan a onMessage desde un único hilo, en orden de recepción.
 *  - close() es idempotente y libera hilos y sockets.
 * TCP y gRPC lo cumplen por naturaleza; UDP lo cumple con retransmisión.
 */
public interface ClientTransport extends AutoCloseable {
    void connect(Consumer<ServerMessage> onMessage, Consumer<Throwable> onFailure) throws IOException;
    void send(ClientMessage message) throws IOException;
    @Override void close();
}

public enum Protocol { TCP, UDP, GRPC }

public final class ClientTransports {
    private ClientTransports() {}

    public static ClientTransport create(Protocol protocol, InetSocketAddress server) {
        var codec = new BinaryCodec();
        return switch (protocol) {   // exhaustivo: un protocolo nuevo obliga a implementarlo aquí
            case TCP  -> new TcpClientTransport(server, codec, new LengthPrefixedFraming(Limits.MAX_FRAME_BYTES));
            case UDP  -> new UdpClientTransport(server, codec, RetryPolicy.defaults());
            case GRPC -> new GrpcClientTransport(server, new GrpcMapper());
        };
    }
}

public final class ChatClient implements AutoCloseable {
    private final ClientTransport transport;

    public ChatClient(ClientTransport transport) { this.transport = transport; }   // DIP: no conoce el protocolo

    public String sendText(String room, String body) throws IOException {
        var messageId = UUID.randomUUID().toString();    // id generado en cliente: permite deduplicar reintentos
        transport.send(new ClientMessage.SendText(messageId, room, body));
        return messageId;
    }
    // join, leave, heartbeat (Ping periódico menor que el idle timeout del servidor), close...
}
```

Retransmisión en `UdpClientTransport` (usa `socket.connect(server)` para ignorar datagramas de otros orígenes):

```java
private record Pending(byte[] datagram, int attempts, long nextRetryNanos) {}
private final Map<String, Pending> pending = new ConcurrentHashMap<>();

@Override
public void send(ClientMessage message) throws IOException {
    byte[] bytes = codec.encode(message);
    if (message instanceof ClientMessage.SendText text) {
        pending.put(text.messageId(), new Pending(bytes, 1, System.nanoTime() + retry.timeout().toNanos()));
    }
    socket.send(new DatagramPacket(bytes, bytes.length));
}

/** Programado cada ~100 ms en un ScheduledExecutorService. */
private void retransmit() {
    long now = System.nanoTime();
    pending.forEach((id, p) -> {
        if (now < p.nextRetryNanos()) return;
        if (p.attempts() >= retry.maxAttempts()) {
            if (pending.remove(id, p)) onFailure.accept(new DeliveryFailedException(id));
            return;
        }
        var next = new Pending(p.datagram(), p.attempts() + 1, now + retry.backoff(p.attempts()).toNanos());
        if (pending.replace(id, p, next)) sendQuietly(p.datagram());   // replace: no resucita si llegó el Ack
    });
}

// En el bucle de recepción:
// if (message instanceof ServerMessage.Ack(var id)) pending.remove(id);
```

## 6. SOLID aplicado a este proyecto

| Principio | Dónde se aplica | Señal de violación a corregir |
|---|---|---|
| **S** Responsabilidad única | Códec (bytes↔mensajes), framing (delimitar), transporte (I/O), `DefaultSession` (cola + escritor), `ChatService` (reglas) | Un handler que lee el socket, parsea, valida, gestiona salas y escribe a otros sockets |
| **O** Abierto/cerrado | Nuevo protocolo = nuevo `ServerTransport`/`ClientTransport` + caso en el `switch`; el núcleo no se toca | `ChatService` modificado para soportar un protocolo nuevo |
| **L** Sustitución | Todos los transportes cumplen el contrato documentado de `ClientTransport`; límites de mensaje iguales en todos | UDP acepta mensajes que TCP rechaza; un transporte lanza `UnsupportedOperationException` |
| **I** Segregación | `Session` expone solo `id/send/close`; mensajes separados en `ClientMessage`/`ServerMessage` | Interfaz `Session` con métodos de socket, dirección remota o stream gRPC |
| **D** Inversión | El núcleo depende de `Session`/`ChatHandler`; los transportes también; solo `main` conoce las clases concretas | `import java.net.Socket` o `io.grpc.*` dentro de `chat-core` |

Violación típica de OCP + LSP + DIP a la vez (rechazar en revisión):

```java
// ❌ El núcleo conoce los protocolos concretos
if (session instanceof TcpSession tcp) {
    tcp.socket().getOutputStream().write(bytes);
} else if (session instanceof UdpSession udp) {
    udp.datagramSocket().send(...);
}
// ✅ session.send(message);  — el cómo es problema del adaptador
```

**`sealed` vs interfaces abiertas:** usa tipos sellados para conjuntos de **datos** cerrados que deben manejarse exhaustivamente (mensajes, errores). Usa interfaces abiertas para **comportamientos** extensibles (transportes, sinks). No se contradicen.

## 7. Reglas de concurrencia (Java 21)

1. **Un lector y un escritor por sesión.** Solo el hilo escritor de `DefaultSession` toca el socket/stream de salida.
2. **`onMessage` nunca bloquea**; `send` solo encola. Nada de I/O dentro de un broadcast.
3. **Colas acotadas** con política explícita para clientes lentos (hoy: cerrar la sesión).
4. **No uses `synchronized` alrededor de I/O bloqueante**: en Java 21 fija (*pin*) el hilo virtual a su portador. Usa `ReentrantLock`. Diagnóstico: `-Djdk.tracePinnedThreads=full`.
5. **No hagas pools de hilos virtuales**; crea uno por tarea (`newVirtualThreadPerTaskExecutor`).
6. **Estado compartido** en `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()`; evita `HashMap` compartido.
7. **`InterruptedException`**: restaura siempre con `Thread.currentThread().interrupt()`.
8. **Tareas programadas** envueltas en `try/catch`: una excepción cancela las ejecuciones futuras sin aviso.
9. **Apagado ordenado**: cerrar transportes en orden inverso (`transports.reversed()`), luego executors.

## 8. Raíz de composición del servidor

```java
public final class ChatServerMain {
    public static void main(String[] args) throws InterruptedException, IOException {
        var config = ServerConfig.fromEnv();   // record con puertos, timeouts y rutas TLS
        var codec  = new BinaryCodec();
        var chat   = new ChatService(new RecentIds(10_000), Clock.systemUTC());

        List<ServerTransport> transports = List.of(
                new TcpServerTransport(config.tcpPort(), codec,
                        new LengthPrefixedFraming(Limits.MAX_FRAME_BYTES), config.idleTimeout()),
                new UdpServerTransport(config.udpPort(), codec, config.idleTimeout()),
                new GrpcServerTransport(config.grpcPort(), new GrpcMapper()));

        for (var transport : transports) transport.start(chat);   // el mismo núcleo para los tres

        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(
                () -> transports.reversed().forEach(ServerTransport::close)));
        new CountDownLatch(1).await();
    }
}
```

## 9. Seguridad y operación

- **Identidad:** el servidor asigna el remitente (`from`) desde la sesión; ignora cualquier identidad enviada en un mensaje de texto.
- **Validación:** los records validan al construirse; los decodificadores convierten esos errores en `ChatProtocolException`. Un mensaje inválido nunca tumba el bucle de recepción.
- **Límites:** tamaño de frame, longitud de campos, capacidad de cola, idle timeout y rate limit por sesión (token bucket, `ErrorCode.RATE_LIMITED`).
- **Cifrado:** TLS para TCP con `SSLServerSocketFactory` del JDK; TLS en gRPC con `useTransportSecurity(...)`; UDP sin cifrar solo en red de confianza (DTLS requiere `SSLEngine` y es un trabajo aparte).
- **Logs:** `System.Logger` con id de sesión; **nunca** registres el cuerpo de los mensajes ni credenciales.
- **Configuración:** variables de entorno cargadas en un `record ServerConfig`; nada de puertos o rutas fijas en el código.

## 10. Testing

| Nivel | Qué | Cómo |
|---|---|---|
| Protocolo | Ida y vuelta de **cada** tipo de mensaje; truncados, sobrantes, versión errónea, longitudes excesivas | JUnit 5 `@ParameterizedTest` sobre `BinaryCodec` y `LengthPrefixedFraming` |
| Núcleo | Salas, Ack, deduplicación, `NOT_JOINED`, desconexión | `ChatService` con `RecordingSession` (sin red) |
| Contrato de transporte | Las mismas garantías en TCP, UDP y gRPC (**test de LSP**) | Clase abstracta `ClientTransportContractTest` con una subclase por protocolo |
| Adaptadores | Conexión real, timeouts, cierre | Servidor en puerto `0` + `localAddress()`; gRPC con `InProcessServerBuilder` |
| UDP con pérdidas | Retransmisión y deduplicación | Servidor de prueba que descarta los primeros N datagramas |

```java
final class RecordingSession implements Session {
    final List<ServerMessage> received = new CopyOnWriteArrayList<>();
    private final SessionId id = SessionId.random();
    @Override public SessionId id() { return id; }
    @Override public void send(ServerMessage message) { received.add(message); }
    @Override public void close() { }
}

@Test
void textoSeEntregaAOtrosMiembrosYSeConfirmaAlEmisor() {
    var chat = new ChatService(new RecentIds(100), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var ana = new RecordingSession();
    var luis = new RecordingSession();
    chat.onMessage(ana, new ClientMessage.Join("ana", "general"));
    chat.onMessage(luis, new ClientMessage.Join("luis", "general"));

    chat.onMessage(ana, new ClientMessage.SendText("m-1", "general", "hola"));
    chat.onMessage(ana, new ClientMessage.SendText("m-1", "general", "hola"));   // reintento duplicado

    assertEquals(1, luis.received.stream().filter(m -> m instanceof ServerMessage.TextDelivered).count());
    assertEquals(2, ana.received.stream().filter(m -> m instanceof ServerMessage.Ack).count());
}
```

## 11. Recetas para Claude Code

**Añadir un tipo de mensaje** (p. ej. `Typing`):
1. Añade el `record` a `ClientMessage` o `ServerMessage` con validación en el constructor compacto.
2. Compila (`mvn -q compile`): los `switch` exhaustivos marcarán cada sitio pendiente (códec, `GrpcMapper`, `ChatService`).
3. Asigna un código de tipo binario nuevo (nunca reutilices uno existente) y un campo nuevo en el `oneof` del `.proto`.
4. Verifica que el mensaje más grande posible cabe en `MAX_FRAME_BYTES`.
5. Añade el caso al test de ida y vuelta del códec.

**Añadir un protocolo** (p. ej. WebSocket):
1. Nuevo módulo `transport-xxx` que depende solo de `chat-protocol` y `chat-transport-api`.
2. Implementa `ServerTransport` reutilizando `DefaultSession` y `ClientTransport` cumpliendo su contrato.
3. Añade el valor al enum `Protocol` y el caso en `ClientTransports`; regístralo en `ChatServerMain`.
4. Crea la subclase de `ClientTransportContractTest`. Sin cambios en `chat-core`.

**Antes de dar por terminado cualquier cambio:** ejecuta `mvn -q verify` (en Windows, `mvnw.cmd -q verify` si existe el wrapper) y revisa el checklist.

## 12. Antipatrones

| Antipatrón | Corrección |
|---|---|
| Leer de TCP con `readLine()` o `read(buffer)` asumiendo un mensaje por lectura | Framing con prefijo de longitud y `readFully` |
| Escribir en el socket desde varios hilos | Un único hilo escritor por sesión (`DefaultSession`) |
| Broadcast que escribe directamente en cada socket | `session.send()` que encola |
| `ObjectOutputStream` / serialización Java en la red | `BinaryCodec` o Protobuf |
| `writeUTF` / `readUTF` | Longitud + bytes UTF-8 estándar |
| `ErrorCode.ordinal()` en el cable | Valor `wire` explícito |
| Crear sesión UDP para cualquier datagrama | Solo con `Join`; TTL por inactividad |
| Reintentar sin id de mensaje | `messageId` generado en el cliente + deduplicación en el servidor |
| Clases generadas por Protobuf fuera de `transport-grpc` | Mapear a `ClientMessage`/`ServerMessage` en `GrpcMapper` |
| `synchronized` alrededor de I/O | `ReentrantLock` o diseño sin bloqueo compartido |
| `switch` con `default` sobre tipos sellados | `switch` exhaustivo sin `default` |
| `catch (InterruptedException e) {}` | Restaurar la interrupción |

## Checklist de revisión (obligatorio)

- [ ] Compila con `release 21` sin `--enable-preview` y sin APIs posteriores a Java 21.
- [ ] `chat-core` no importa `java.net.*`, `java.io` de sockets ni `io.grpc.*`.
- [ ] Clases generadas por Protobuf no aparecen fuera de `transport-grpc`.
- [ ] Mensajes nuevos: record validado, `switch` exhaustivos actualizados, tipo binario y campo proto nuevos, test de ida y vuelta.
- [ ] Ningún mensaje válido supera `MAX_FRAME_BYTES`; los límites son idénticos en los tres transportes.
- [ ] Solo el hilo escritor de la sesión escribe en el socket/stream.
- [ ] `onMessage` no bloquea y las colas son acotadas.
- [ ] Toda lectura de red tiene timeout o mecanismo de expiración; los clientes envían heartbeat.
- [ ] Un mensaje malformado se descarta o responde con `Failure`, sin tumbar el bucle de recepción.
- [ ] `InterruptedException` restaura la interrupción; tareas programadas protegidas con `try/catch`.
- [ ] `close()` idempotente y recursos liberados en orden inverso.
- [ ] El remitente de los mensajes lo asigna el servidor.
- [ ] Tests de códec, núcleo y contrato de transporte pasan para TCP, UDP y gRPC.
