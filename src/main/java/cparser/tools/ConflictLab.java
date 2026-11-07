package cparser.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ConflictLab {
    public record Result(String output, int exitCode, int shiftReduce, int reduceReduce, boolean generated,
                         Integer states) {
    }

    private static final Pattern STATES = Pattern.compile("producing (\\d+) unique parse states");

    private ConflictLab() {
    }

    public static Result run(String grammar, int expect, boolean dumpStates, boolean dumpTables)
            throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("cup-lab");
        try {
            Path spec = dir.resolve("grammar.cup");
            Files.writeString(spec, grammar, StandardCharsets.UTF_8);
            String java = ProcessHandle.current().info().command().orElse("java");
            List<String> classpath = new ArrayList<>();
            for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    classpath.add(Path.of(entry).toAbsolutePath().toString());
                }
            }
            List<String> cmd = new ArrayList<>(List.of(java, "-cp", String.join(File.pathSeparator, classpath),
                    "java_cup.Main", "-destdir", dir.toString(), "-expect", Integer.toString(expect)));
            if (dumpStates) {
                cmd.add("-dump_states");
            }
            if (dumpTables) {
                cmd.add("-dump_tables");
            }
            cmd.add(spec.toString());
            Process process = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
            byte[] bytes = process.getInputStream().readAllBytes();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            String output = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
            output = output.replace(dir.toString(), "<tmp>");
            int sr = count(output, "Shift/Reduce conflict found");
            int rr = count(output, "Reduce/Reduce conflict found");
            boolean generated = Files.exists(dir.resolve("parser.java"));
            Matcher m = STATES.matcher(output);
            Integer states = m.find() ? Integer.valueOf(m.group(1)) : null;
            return new Result(output, process.exitValue(), sr, rr, generated, states);
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    public static Map<String, Object> toMap(Result r) {
        return Map.of("output", r.output(), "exitCode", r.exitCode(), "shiftReduce", r.shiftReduce(),
                "reduceReduce", r.reduceReduce(), "generated", r.generated(),
                "states", r.states() == null ? -1 : r.states());
    }
}
