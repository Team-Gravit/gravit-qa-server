package gravit.qa.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpStubServer {

    private static final String KEY_DELIMITER = " ";

    private final HttpServer server;
    private final Map<String, Deque<StubResponse>> routeToResponses = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();

    public HttpStubServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void stub(
            String method,
            String path,
            StubResponse... responses
    ) {
        routeToResponses.put(method + KEY_DELIMITER + path, new ArrayDeque<>(List.of(responses)));
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public List<RecordedRequest> requests(
            String method,
            String path
    ) {
        return requests.stream()
                .filter(request -> request.method().equals(method) && request.path().equals(path))
                .toList();
    }

    public int maxConcurrency() {
        return maxInFlight.get();
    }

    public void reset() {
        routeToResponses.clear();
        requests.clear();
        inFlight.set(0);
        maxInFlight.set(0);
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        int current = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(current, Math::max);
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            requests.add(new RecordedRequest(
                    method,
                    path,
                    exchange.getRequestURI().getRawQuery(),
                    lowerCaseHeaders(exchange),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            ));

            StubResponse response = next(method + KEY_DELIMITER + path);
            sleep(response.delay());
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] body = response.body();
            exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private StubResponse next(String key) {
        Deque<StubResponse> responses = routeToResponses.get(key);
        if (responses == null || responses.isEmpty()) {
            return StubResponse.json(404, "{\"message\": \"stub not found: " + key + "\"}");
        }
        synchronized (responses) {
            return responses.size() > 1 ? responses.poll() : responses.peek();
        }
    }

    private static Map<String, List<String>> lowerCaseHeaders(HttpExchange exchange) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
        return headers;
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

    public record StubResponse(
            int status,

            Map<String, String> headers,

            byte[] body,

            Duration delay
    ) {

        public static StubResponse json(
                int status,
                String body
        ) {
            return new StubResponse(status, Map.of("Content-Type", "application/json"), body.getBytes(StandardCharsets.UTF_8), Duration.ZERO);
        }

        public static StubResponse status(int status) {
            return json(status, "{}");
        }

        public static StubResponse bytes(
                String contentType,
                byte[] body
        ) {
            return new StubResponse(200, Map.of("Content-Type", contentType), body, Duration.ZERO);
        }

        public StubResponse withHeader(
                String name,
                String value
        ) {
            Map<String, String> merged = new LinkedHashMap<>(headers);
            merged.put(name, value);
            return new StubResponse(status, Map.copyOf(merged), body, delay);
        }

        public StubResponse delayed(Duration delay) {
            return new StubResponse(status, headers, body, delay);
        }
    }

    public record RecordedRequest(
            String method,

            String path,

            String query,

            Map<String, List<String>> headers,

            String body
    ) {

        public String header(String name) {
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.getFirst();
        }

        public boolean hasHeader(String name) {
            return headers.containsKey(name.toLowerCase(Locale.ROOT));
        }
    }
}
