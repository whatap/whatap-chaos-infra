import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.file.*;
import java.util.Map;

/**
 * Serves static files from the web/ directory.
 */
public class StaticHandler implements HttpHandler {

    private final Path webRoot;

    private static final Map<String, String> MIME_TYPES = Map.of(
        "html", "text/html; charset=utf-8",
        "css",  "text/css; charset=utf-8",
        "js",   "application/javascript; charset=utf-8",
        "json", "application/json; charset=utf-8",
        "png",  "image/png",
        "ico",  "image/x-icon",
        "svg",  "image/svg+xml"
    );

    public StaticHandler(Path webRoot) {
        this.webRoot = webRoot;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Default to index.html
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        // Prevent directory traversal
        Path resolved = webRoot.resolve(path.substring(1)).normalize();
        if (!resolved.startsWith(webRoot)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            // Fallback to index.html for SPA-like routing
            resolved = webRoot.resolve("index.html");
            if (!Files.exists(resolved)) {
                byte[] msg = "Not Found".getBytes();
                exchange.sendResponseHeaders(404, msg.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(msg); }
                return;
            }
        }

        String ext = getExtension(resolved.getFileName().toString());
        String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

        byte[] data = Files.readAllBytes(resolved);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
