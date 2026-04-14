import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes scenario start/stop/status scripts via ProcessBuilder.
 */
public class ScenarioExecutor {

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "scenario-exec");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "scenario-timer");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    private static final int SCRIPT_TIMEOUT_SECONDS = 60;

    public void startScenario(Scenario scenario, Map<String, String> params) {
        if (scenario.getState() == Scenario.State.RUNNING || scenario.getState() == Scenario.State.STARTING) {
            throw new IllegalStateException("Scenario " + scenario.id + " is already " + scenario.getState());
        }

        scenario.setState(Scenario.State.STARTING);
        executor.submit(() -> {
            try {
                Path startScript = scenario.directory.resolve("start.sh");
                if (!Files.exists(startScript)) {
                    scenario.setLastError("start.sh not found");
                    scenario.setState(Scenario.State.ERROR);
                    return;
                }

                List<String> cmd = new ArrayList<>();
                cmd.add("bash");
                cmd.add(startScript.toString());

                // Pass parameters in order defined by config
                for (Scenario.Parameter p : scenario.parameters) {
                    String val = params.getOrDefault(p.name, p.defaultValue);
                    cmd.add(val);
                }

                log("Starting " + scenario.id + ": " + String.join(" ", cmd));
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(scenario.directory.toFile());
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                String output = readStream(proc.getInputStream());
                boolean finished = proc.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!finished) {
                    proc.destroyForcibly();
                    scenario.setLastOutput(output);
                    scenario.setLastError("Script timed out after " + SCRIPT_TIMEOUT_SECONDS + "s");
                    scenario.setState(Scenario.State.ERROR);
                    return;
                }

                scenario.setLastOutput(output);
                int exitCode = proc.exitValue();
                if (exitCode == 0) {
                    scenario.setState(Scenario.State.RUNNING);
                    log("Started " + scenario.id + " successfully");

                    // Schedule auto-stop if duration > 0
                    int duration = scenario.defaultDuration;
                    String durationParam = params.get("duration");
                    if (durationParam != null) {
                        try { duration = Integer.parseInt(durationParam); } catch (NumberFormatException ignored) {}
                    }
                    if (duration > 0) {
                        scheduleAutoStop(scenario, duration);
                    }
                } else {
                    scenario.setLastError("start.sh exited with code " + exitCode);
                    scenario.setState(Scenario.State.ERROR);
                    log("Failed to start " + scenario.id + ": exit code " + exitCode);
                }
            } catch (Exception e) {
                scenario.setLastError(e.getMessage());
                scenario.setState(Scenario.State.ERROR);
                log("Error starting " + scenario.id + ": " + e.getMessage());
            }
        });
    }

    public void stopScenario(Scenario scenario) {
        Scenario.State current = scenario.getState();
        if (current == Scenario.State.STOPPED) {
            return;
        }

        // Cancel any auto-stop timer
        ScheduledFuture<?> timer = timers.remove(scenario.id);
        if (timer != null) timer.cancel(false);

        scenario.setState(Scenario.State.STOPPING);
        executor.submit(() -> {
            try {
                Path stopScript = scenario.directory.resolve("stop.sh");
                if (!Files.exists(stopScript)) {
                    scenario.setLastError("stop.sh not found");
                    scenario.setState(Scenario.State.ERROR);
                    return;
                }

                List<String> cmd = new ArrayList<>();
                cmd.add("bash");
                cmd.add(stopScript.toString());

                // Pass port as first arg if available
                for (Scenario.Parameter p : scenario.parameters) {
                    cmd.add(p.defaultValue);
                    break; // only first param for stop
                }

                log("Stopping " + scenario.id);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(scenario.directory.toFile());
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                String output = readStream(proc.getInputStream());
                boolean finished = proc.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!finished) {
                    proc.destroyForcibly();
                    scenario.setLastOutput(output);
                    scenario.setLastError("Stop script timed out");
                    scenario.setState(Scenario.State.ERROR);
                    return;
                }

                scenario.setLastOutput(output);
                scenario.setState(Scenario.State.STOPPED);
                log("Stopped " + scenario.id);
            } catch (Exception e) {
                scenario.setLastError(e.getMessage());
                scenario.setState(Scenario.State.ERROR);
                log("Error stopping " + scenario.id + ": " + e.getMessage());
            }
        });
    }

    public String checkStatus(Scenario scenario) {
        Path statusScript = scenario.directory.resolve("status.sh");
        if (!Files.exists(statusScript)) {
            return "{\"running\":" + (scenario.getState() == Scenario.State.RUNNING) + "}";
        }
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("bash");
            cmd.add(statusScript.toString());
            for (Scenario.Parameter p : scenario.parameters) {
                cmd.add(p.defaultValue);
                break;
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(scenario.directory.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = readStream(proc.getInputStream());
            proc.waitFor(10, TimeUnit.SECONDS);
            return output.isBlank() ? "{\"running\":false}" : output.trim();
        } catch (Exception e) {
            return "{\"running\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public void stopAll(List<Scenario> scenarios) {
        for (Scenario s : scenarios) {
            if (s.getState() == Scenario.State.RUNNING || s.getState() == Scenario.State.STARTING) {
                stopScenario(s);
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
    }

    private void scheduleAutoStop(Scenario scenario, int durationSeconds) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (scenario.getState() == Scenario.State.RUNNING) {
                log("Auto-stopping " + scenario.id + " after " + durationSeconds + "s");
                stopScenario(scenario);
            }
            timers.remove(scenario.id);
        }, durationSeconds, TimeUnit.SECONDS);
        timers.put(scenario.id, future);
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        // Limit output size to 10KB
        if (sb.length() > 10240) {
            return sb.substring(0, 10240) + "\n... (truncated)";
        }
        return sb.toString();
    }

    private void log(String msg) {
        System.out.printf("[%tF %<tT] [ScenarioExecutor] %s%n", System.currentTimeMillis(), msg);
    }
}
