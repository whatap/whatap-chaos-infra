import java.nio.file.Path;
import java.util.*;

/**
 * Data model for a chaos scenario, parsed from config.json.
 */
public class Scenario {

    public enum State {
        STOPPED, STARTING, RUNNING, STOPPING, ERROR
    }

    // From config.json
    public final String id;
    public final String name;
    public final String description;
    public final String category;       // "application", "container", "database"
    public final String severity;       // "low", "medium", "high", "critical"
    public final String targetService;
    public final int targetPort;
    public final int defaultDuration;
    public final List<Parameter> parameters;
    public final List<String> tags;
    public final Path directory;

    // Runtime state
    private volatile State state = State.STOPPED;
    private volatile long startedAt = 0;
    private volatile String lastOutput = "";
    private volatile String lastError = null;

    public Scenario(String id, Map<String, Object> config, Path directory) {
        this.id = id;
        this.directory = directory;
        this.name = JsonUtil.getString(config, "name", id);
        this.description = JsonUtil.getString(config, "description", "");
        this.category = JsonUtil.getString(config, "category", "application");
        this.severity = JsonUtil.getString(config, "severity", "medium");
        this.targetService = JsonUtil.getString(config, "targetService", "");
        this.targetPort = JsonUtil.getInt(config, "targetPort", 0);
        this.defaultDuration = JsonUtil.getInt(config, "defaultDuration", 0);
        this.tags = JsonUtil.getStringArray(config, "tags");

        List<Parameter> params = new ArrayList<>();
        // Support "parameters" as array of {name, description, default}
        List<Map<String, Object>> paramArray = JsonUtil.getArrayOfObjects(config, "parameters");
        if (!paramArray.isEmpty()) {
            for (Map<String, Object> p : paramArray) {
                params.add(new Parameter(
                    JsonUtil.getString(p, "name", ""),
                    JsonUtil.getString(p, "description", ""),
                    JsonUtil.getString(p, "default", "")
                ));
            }
        } else {
            // Also support "params" as object: { "port": { "default": 8083, "description": "..." }, ... }
            Object paramsObj = config.get("params");
            if (paramsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paramsMap = (Map<String, Object>) paramsObj;
                for (Map.Entry<String, Object> entry : paramsMap.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pDef = (Map<String, Object>) entry.getValue();
                        params.add(new Parameter(
                            entry.getKey(),
                            JsonUtil.getString(pDef, "description", ""),
                            JsonUtil.getString(pDef, "default", "")
                        ));
                    }
                }
            }
        }
        this.parameters = Collections.unmodifiableList(params);
    }

    public State getState()       { return state; }
    public long getStartedAt()    { return startedAt; }
    public String getLastOutput() { return lastOutput; }
    public String getLastError()  { return lastError; }

    public synchronized void setState(State state) {
        this.state = state;
        if (state == State.STARTING) {
            this.startedAt = System.currentTimeMillis();
            this.lastError = null;
        }
        if (state == State.STOPPED) {
            this.startedAt = 0;
        }
    }

    public void setLastOutput(String output) { this.lastOutput = output; }
    public void setLastError(String error)   { this.lastError = error; }

    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("severity", severity);
        m.put("state", state.name());
        m.put("targetService", targetService);
        m.put("targetPort", targetPort);
        m.put("tags", tags);
        return m;
    }

    public Map<String, Object> toDetailMap() {
        Map<String, Object> m = toSummaryMap();
        m.put("description", description);
        m.put("defaultDuration", defaultDuration);
        m.put("startedAt", startedAt);
        m.put("lastOutput", lastOutput);
        m.put("lastError", lastError);
        List<Map<String, Object>> paramList = new ArrayList<>();
        for (Parameter p : parameters) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("name", p.name);
            pm.put("description", p.description);
            pm.put("default", p.defaultValue);
            paramList.add(pm);
        }
        m.put("parameters", paramList);
        return m;
    }

    public static class Parameter {
        public final String name;
        public final String description;
        public final String defaultValue;

        public Parameter(String name, String description, String defaultValue) {
            this.name = name;
            this.description = description;
            this.defaultValue = defaultValue;
        }
    }
}
