import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Discovers and manages scenario definitions from the trouble/ directory.
 */
public class ScenarioRegistry {

    private final Path troubleDir;
    private final ConcurrentHashMap<String, Scenario> scenarios = new ConcurrentHashMap<>();

    public ScenarioRegistry(Path troubleDir) {
        this.troubleDir = troubleDir;
        refresh();
    }

    public void refresh() {
        if (!Files.isDirectory(troubleDir)) {
            System.out.println("[WARN] Trouble directory not found: " + troubleDir);
            return;
        }
        try (Stream<Path> dirs = Files.list(troubleDir)) {
            Set<String> found = new HashSet<>();
            dirs.filter(Files::isDirectory)
                .sorted()
                .forEach(dir -> {
                    Path configFile = dir.resolve("config.json");
                    if (Files.exists(configFile)) {
                        String id = dir.getFileName().toString();
                        found.add(id);
                        if (!scenarios.containsKey(id)) {
                            try {
                                String json = Files.readString(configFile);
                                Map<String, Object> config = JsonUtil.parseObject(json);
                                Scenario s = new Scenario(id, config, dir);
                                scenarios.put(id, s);
                                System.out.println("[INFO] Loaded scenario: " + id + " (" + s.name + ")");
                            } catch (Exception e) {
                                System.out.println("[WARN] Failed to load scenario " + id + ": " + e.getMessage());
                            }
                        }
                    }
                });
            // Remove scenarios whose directories no longer exist
            scenarios.keySet().removeIf(id -> !found.contains(id));
        } catch (IOException e) {
            System.out.println("[ERROR] Failed to scan trouble directory: " + e.getMessage());
        }
    }

    public List<Scenario> getAll() {
        return scenarios.values().stream()
            .sorted(Comparator.comparing(s -> s.id))
            .collect(Collectors.toList());
    }

    public Scenario getById(String id) {
        return scenarios.get(id);
    }

    public List<Scenario> getByCategory(String category) {
        return scenarios.values().stream()
            .filter(s -> s.category.equalsIgnoreCase(category))
            .sorted(Comparator.comparing(s -> s.id))
            .collect(Collectors.toList());
    }

    public int getTotal() {
        return scenarios.size();
    }

    public long getRunningCount() {
        return scenarios.values().stream()
            .filter(s -> s.getState() == Scenario.State.RUNNING || s.getState() == Scenario.State.STARTING)
            .count();
    }
}
