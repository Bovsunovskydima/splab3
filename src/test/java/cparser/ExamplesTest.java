package cparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import cparser.grammar.GrammarInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExamplesTest {
    private static final List<String> FAILING = List.of("syntax_errors.c", "semantic_errors.c", "runtime_error.c");

    static Stream<String> examples() {
        List<String> names = new ArrayList<>();
        for (String line : GrammarInfo.resource("/examples/index.txt").split("\n")) {
            if (!line.isBlank()) {
                names.add(line.split("\\|")[0].trim());
            }
        }
        return names.stream();
    }

    @ParameterizedTest
    @MethodSource("examples")
    void exampleBehavesAsExpected(String name) throws Exception {
        Compilation c = Compilation.compile(GrammarInfo.resource("/examples/" + name), Compilation.Options.defaults());
        if (name.equals("syntax_errors.c")) {
            assertTrue(c.diagnostics.stream().anyMatch(d -> d.phase().equals("parser")));
            return;
        }
        if (name.equals("semantic_errors.c")) {
            assertTrue(c.diagnostics.stream().filter(d -> d.isError() && d.phase().equals("semantic")).count() >= 6,
                    c.diagnostics.toString());
            return;
        }
        assertFalse(c.hasErrors(), c.diagnostics.toString());
        assertNotNull(c.run);
        if (name.equals("runtime_error.c")) {
            assertNotNull(c.run.error());
            return;
        }
        assertNull(c.run.error(), c.run.error());
        assertTrue(!FAILING.contains(name));
        String node = nodeOutput(c.javascript);
        assumeTrue(node != null, "Node.js не знайдено, порівняння з JavaScript пропущено");
        assertEquals(c.run.output(), node, "вивід інтерпретатора і згенерованого JavaScript");
    }

    private static String nodeOutput(String js) throws Exception {
        Path file = Files.createTempFile("generated", ".js");
        try {
            Files.writeString(file, js, StandardCharsets.UTF_8);
            Process p = new ProcessBuilder("node", file.toString()).redirectErrorStream(true).start();
            byte[] out = p.getInputStream().readAllBytes();
            p.waitFor(30, TimeUnit.SECONDS);
            return new String(out, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
