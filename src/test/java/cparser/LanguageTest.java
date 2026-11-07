package cparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cparser.ast.Block;
import cparser.ast.FunctionDef;
import cparser.ast.If;
import cparser.ast.Program;
import org.junit.jupiter.api.Test;

class LanguageTest {

    private static Compilation compile(String source) {
        return Compilation.compile(source, Compilation.Options.defaults());
    }

    private static int exitCode(String body) {
        Compilation c = compile("int main(void) { " + body + " }");
        assertFalse(c.hasErrors(), c.diagnostics.toString());
        assertNull(c.run.error(), c.run.error());
        return c.run.exitCode();
    }

    private static String output(String source) {
        Compilation c = compile(source);
        assertFalse(c.hasErrors(), c.diagnostics.toString());
        assertNull(c.run.error(), c.run.error());
        return c.run.output();
    }

    @Test
    void operatorPrecedenceAndAssociativity() {
        assertEquals(14, exitCode("return 2 + 3 * 4;"));
        assertEquals(20, exitCode("return (2 + 3) * 4;"));
        assertEquals(3, exitCode("return 10 - 4 - 3;"));
        assertEquals(2, exitCode("return 100 / 10 / 5;"));
        assertEquals(-6, exitCode("return -2 * 3;"));
        assertEquals(24, exitCode("return 1 + 2 << 3;"));
        assertEquals(1, exitCode("return 1 < 2 == 1;"));
        assertEquals(1, exitCode("return 0 || 1 && 1;"));
        assertEquals(7, exitCode("int a, b; a = b = 7; return a;"));
        assertEquals(3, exitCode("int x = 0; return x ? 1 : x + 1 ? 3 : 4;"));
        assertEquals(-5, exitCode("int i = 5; return -i++;"));
        assertEquals(6, exitCode("int i = 5; i++; return i;"));
        assertEquals(1, exitCode("int a[3] = {0, 1, 2}; return -a[1] + 2;"));
        assertEquals(3, exitCode("return (int) 3.99;"));
        assertEquals(-3, exitCode("return -7 / 2;"));
        assertEquals(-1, exitCode("return -7 % 2;"));
        assertEquals(-2147483648, exitCode("int big = 2147483647; big = big + 1; return big;"));
    }

    @Test
    void danglingElseBindsToNearestIf() {
        Compilation c = compile("int main(void) { if (1) if (0) return 1; else return 2; return 3; }");
        Program p = c.program;
        FunctionDef main = (FunctionDef) p.items.get(0);
        If outer = (If) main.body.items.get(0);
        assertNull(outer.otherwise);
        If inner = (If) outer.then;
        assertNotNull(inner.otherwise);
        assertEquals(2, c.run.exitCode());
    }

    @Test
    void controlFlow() {
        assertEquals(55, exitCode("int s = 0; for (int i = 1; i <= 10; i++) s += i; return s;"));
        assertEquals(25, exitCode("int s = 0; for (int i = 0; i < 10; i++) { if (i % 2 == 0) continue; s += i; } return s;"));
        assertEquals(5, exitCode("int i = 0; while (1) { if (i == 5) break; i++; } return i;"));
        assertEquals(1, exitCode("int i = 0; do { i++; } while (i < 0); return i;"));
    }

    @Test
    void functionsAndArrays() {
        String program = """
                int sum(int a[], int n) { int s = 0; for (int i = 0; i < n; i++) s += a[i]; return s; }
                int fact(int n) { return n <= 1 ? 1 : n * fact(n - 1); }
                int main(void) {
                    int a[] = {1, 2, 3, 4};
                    int m[2][2] = {{1, 2}, {3, 4}};
                    printf("%d %d %d\\n", sum(a, 4), fact(10), m[1][0]);
                    return 0;
                }
                """;
        assertEquals("10 3628800 3\n", output(program));
    }

    @Test
    void printfFormatting() {
        String program = """
                int main(void) {
                    printf("[%5d][%-5d][%05d][%+d][%x][%#o][%c]\\n", 42, 42, 42, 42, 255, 8, 65);
                    printf("[%.2f][%8.3f][%e][%g][%g][%s]\\n", 3.14159, -2.5, 12345.678, 0.0001, 1e20, "ok");
                    return 0;
                }
                """;
        assertEquals("[   42][42   ][00042][+42][ff][010][A]\n[3.14][  -2.500][1.234568e+04][0.0001][1e+20][ok]\n",
                output(program));
    }

    @Test
    void syntaxErrorsAreRecovered() {
        Compilation c = compile("int main(void) { int a = 5 int b = 3; a = a + * b; return 0; }");
        long errors = c.diagnostics.stream().filter(d -> d.phase().equals("parser")).count();
        assertTrue(errors >= 2, c.diagnostics.toString());
        assertNotNull(c.program);
        assertNull(c.run);
    }

    @Test
    void semanticErrorsAreReported() {
        Compilation c = compile("""
                int f(int x) { return x; }
                int main(void) { int a; b = 1; a = f(1, 2); 5 = a; break; return 0; }
                """);
        String all = c.diagnostics.toString();
        assertTrue(all.contains("'b' не оголошено"), all);
        assertTrue(all.contains("очікує 1"), all);
        assertTrue(all.contains("Ліворуч від присвоєння"), all);
        assertTrue(all.contains("break поза циклом"), all);
        assertNull(c.run);
    }

    @Test
    void runtimeErrors() {
        Compilation c = compile("int main(void) { int a[3]; return a[3]; }");
        assertTrue(c.run.error().contains("Вихід за межі масиву"), c.run.error());
        c = compile("int main(void) { int z = 0; return 5 / z; }");
        assertTrue(c.run.error().contains("Ділення на нуль"), c.run.error());
        c = compile("int main(void) { while (1) { } return 0; }");
        assertTrue(c.run.error().contains("ліміт"), c.run.error());
    }

    @Test
    void traceContainsShiftReduceAccept() {
        Compilation c = compile("int main(void) { return 1 + 2; }");
        assertTrue(c.trace.stream().anyMatch(s -> s.action().equals("shift")));
        assertTrue(c.trace.stream().anyMatch(s -> s.detail().contains("expr ::= expr PLUS expr")));
        assertEquals("accept", c.trace.get(c.trace.size() - 1).action());
        assertTrue(c.program.items.get(0) instanceof FunctionDef f && f.body instanceof Block);
    }
}
