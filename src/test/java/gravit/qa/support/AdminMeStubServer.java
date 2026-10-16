package gravit.qa.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminMeStubServer {

    public static final String ADMIN_BODY = """
            {"adminId": 7, "nickname": "운영자", "email": "admin@gravit.test", "profileImgNumber": 1}
            """;

    private final HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    private volatile int status = 200;
    private volatile String body = ADMIN_BODY;
    private volatile Duration delay = Duration.ZERO;

    public AdminMeStubServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/v1/admin/me", exchange -> {
            hits.incrementAndGet();
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            sleep(delay);

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void respond(
            int status,
            String body
    ) {
        this.status = status;
        this.body = body;
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public void reset() {
        this.status = 200;
        this.body = ADMIN_BODY;
        this.delay = Duration.ZERO;
        hits.set(0);
        authorizations.clear();
    }

    public int hits() {
        return hits.get();
    }

    public List<String> authorizations() {
        return List.copyOf(authorizations);
    }

    public void stop() {
        server.stop(0);
    }

    private static void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
