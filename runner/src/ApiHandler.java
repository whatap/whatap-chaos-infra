import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST API handler for /api/v1/*
 */
public class ApiHandler implements HttpHandler {

    private final ScenarioRegistry registry;
    private final ScenarioExecutor executor;
    private final long startTime = System.currentTimeMillis();

    public ApiHandler(ScenarioRegistry registry, ScenarioExecutor executor) {
        this.registry = registry;
        this.executor = executor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            if (path.equals("/api/v1/health")) {
                handleHealth(exchange);
            } else if (path.equals("/api/v1/scenarios") && method.equals("GET")) {
                handleListScenarios(exchange);
            } else if (path.equals("/api/v1/scenarios/stop-all") && method.equals("POST")) {
                handleStopAll(exchange);
            } else if (path.equals("/api/v1/scenarios/refresh") && method.equals("POST")) {
                handleRefresh(exchange);
            } else if (path.matches("/api/v1/scenarios/[^/]+") && method.equals("GET")) {
                String id = path.substring("/api/v1/scenarios/".length());
                handleGetScenario(exchange, id);
            } else if (path.matches("/api/v1/scenarios/[^/]+/start") && method.equals("POST")) {
                String id = extractId(path, "/start");
                handleStartScenario(exchange, id);
            } else if (path.matches("/api/v1/scenarios/[^/]+/stop") && method.equals("POST")) {
                String id = extractId(path, "/stop");
                handleStopScenario(exchange, id);
            } else if (path.matches("/api/v1/scenarios/[^/]+/status") && method.equals("GET")) {
                String id = extractId(path, "/status");
                handleScenarioStatus(exchange, id);
            } else {
                sendJson(exchange, 404, errorJson("Not found: " + path));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson(e.getMessage()));
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "ok");
        resp.put("uptime", (System.currentTimeMillis() - startTime) / 1000);
        resp.put("scenarioCount", registry.getTotal());
        resp.put("runningCount", registry.getRunningCount());
        sendJson(exchange, 200, JsonUtil.toJson(resp));
    }

    private void handleListScenarios(HttpExchange exchange) throws IOException {
        List<Scenario> all = registry.getAll();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Scenario s : all) {
            items.add(s.toSummaryMap());
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("scenarios", items);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", all.size());
        summary.put("running", registry.getRunningCount());
        summary.put("stopped", all.size() - registry.getRunningCount());
        resp.put("summary", summary);
        sendJson(exchange, 200, JsonUtil.toJson(resp));
    }

    private void handleGetScenario(HttpExchange exchange, String id) throws IOException {
        Scenario s = registry.getById(id);
        if (s == null) {
            sendJson(exchange, 404, errorJson("Scenario not found: " + id));
            return;
        }
        sendJson(exchange, 200, JsonUtil.toJson(s.toDetailMap()));
    }

    private void handleStartScenario(HttpExchange exchange, String id) throws IOException {
        Scenario s = registry.getById(id);
        if (s == null) {
            sendJson(exchange, 404, errorJson("Scenario not found: " + id));
            return;
        }

        // Parse optional params from body
        Map<String, String> params = new LinkedHashMap<>();
        String body = readBody(exchange);
        if (body != null && !body.isBlank()) {
            try {
                Map<String, Object> bodyMap = JsonUtil.parseObject(body);
                Object paramsObj = bodyMap.get("params");
                if (paramsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pm = (Map<String, Object>) paramsObj;
                    for (Map.Entry<String, Object> e : pm.entrySet()) {
                        params.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
                    }
                }
            } catch (Exception e) {
                sendJson(exchange, 400, errorJson("Invalid JSON body: " + e.getMessage()));
                return;
            }
        }

        try {
            executor.startScenario(s, params);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", s.id);
            resp.put("state", "STARTING");
            resp.put("message", "Scenario start initiated");
            sendJson(exchange, 200, JsonUtil.toJson(resp));
        } catch (IllegalStateException e) {
            sendJson(exchange, 409, errorJson(e.getMessage()));
        }
    }

    private void handleStopScenario(HttpExchange exchange, String id) throws IOException {
        Scenario s = registry.getById(id);
        if (s == null) {
            sendJson(exchange, 404, errorJson("Scenario not found: " + id));
            return;
        }
        executor.stopScenario(s);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", s.id);
        resp.put("state", "STOPPING");
        resp.put("message", "Scenario stop initiated");
        sendJson(exchange, 200, JsonUtil.toJson(resp));
    }

    private void handleScenarioStatus(HttpExchange exchange, String id) throws IOException {
        Scenario s = registry.getById(id);
        if (s == null) {
            sendJson(exchange, 404, errorJson("Scenario not found: " + id));
            return;
        }
        String statusJson = executor.checkStatus(s);
        // Merge with scenario state
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", s.id);
        resp.put("state", s.getState().name());
        resp.put("detail", statusJson);
        sendJson(exchange, 200, JsonUtil.toJson(resp));
    }

    private void handleStopAll(HttpExchange exchange) throws IOException {
        List<Scenario> running = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Scenario s : registry.getAll()) {
            if (s.getState() == Scenario.State.RUNNING || s.getState() == Scenario.State.STARTING) {
                running.add(s);
                ids.add(s.id);
            }
        }
        executor.stopAll(running);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("stopped", ids);
        resp.put("count", ids.size());
        sendJson(exchange, 200, JsonUtil.toJson(resp));
    }

    private void handleRefresh(HttpExchange exchange) throws IOException {
        registry.refresh();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Scenarios refreshed");
        resp.put("total", registry.getTotal());
        sendJson(exchange, 200, JsonUtil.toJson(resp));
    }

    // Helpers

    private String extractId(String path, String suffix) {
        String s = path.substring("/api/v1/scenarios/".length());
        return s.substring(0, s.length() - suffix.length());
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String errorJson(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        return JsonUtil.toJson(m);
    }
}
