import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.Executors;

/**
 * Chaos Scenario Runner - REST API Server
 *
 * Usage: java ChaosServer [port] [troubleDir]
 *   port       - HTTP server port (default: 9090)
 *   troubleDir - Path to trouble/ directory (default: ../trouble)
 */
public class ChaosServer {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        String troublePath = args.length > 1 ? args[1] : "../trouble";

        Path troubleDir = Paths.get(troublePath).toAbsolutePath().normalize();
        Path webDir = Paths.get("web").toAbsolutePath().normalize();

        // If running from out/ directory, look for web/ there
        if (!Files.isDirectory(webDir)) {
            webDir = Paths.get(".").toAbsolutePath().resolve("web").normalize();
        }

        System.out.println("==============================================");
        System.out.println(" Chaos Scenario Runner v1.0");
        System.out.println("==============================================");
        System.out.println(" Port         : " + port);
        System.out.println(" Trouble dir  : " + troubleDir);
        System.out.println(" Web dir      : " + webDir);
        System.out.println("==============================================");

        // Initialize components
        ScenarioRegistry registry = new ScenarioRegistry(troubleDir);
        ScenarioExecutor executor = new ScenarioExecutor();

        System.out.println("[INFO] Loaded " + registry.getTotal() + " scenarios");

        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // API handler for /api/v1/*
        ApiHandler apiHandler = new ApiHandler(registry, executor);
        server.createContext("/api/v1/", apiHandler);

        // Static file handler for everything else
        StaticHandler staticHandler = new StaticHandler(webDir);
        server.createContext("/", staticHandler);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[INFO] Shutting down...");
            executor.stopAll(registry.getAll());
            executor.shutdown();
            server.stop(2);
            System.out.println("[INFO] Server stopped.");
        }));

        server.start();
        System.out.println("[INFO] Server started on http://0.0.0.0:" + port);
        System.out.println("[INFO] UI available at http://localhost:" + port + "/");
    }
}
