package cparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cparser.gen.Parser;
import cparser.grammar.GrammarInfo;
import cparser.tools.ConflictLab;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrammarTest {

    @Test
    void productionNumbersMatchCupTables() {
        GrammarInfo g = GrammarInfo.get();
        short[][] table = new Parser().production_table();
        assertEquals(table.length, g.productions.size(), "кількість продукцій");
        Map<String, Short> lhsIds = new HashMap<>();
        for (int i = 0; i < table.length; i++) {
            GrammarInfo.Production p = g.productions.get(i);
            assertEquals(table[i][1], p.rhs().size(), "довжина правої частини продукції " + i + ": " + p.text());
            Short previous = lhsIds.putIfAbsent(p.lhs(), table[i][0]);
            if (previous != null) {
                assertEquals(previous.shortValue(), table[i][0], "нетермінал " + p.lhs());
            }
        }
    }

    @Test
    void conflictLabReportsConflicts() throws Exception {
        ConflictLab.Result ambiguous = ConflictLab.run(GrammarInfo.resource("/conflicts/expr_ambiguous.cup"), 0, false, false);
        assertTrue(ambiguous.shiftReduce() > 0, ambiguous.output());
        assertTrue(!ambiguous.generated(), ambiguous.output());

        ConflictLab.Result precedence = ConflictLab.run(GrammarInfo.resource("/conflicts/expr_precedence.cup"), 0, false, false);
        assertEquals(0, precedence.shiftReduce(), precedence.output());
        assertTrue(precedence.generated(), precedence.output());

        ConflictLab.Result rr = ConflictLab.run(GrammarInfo.resource("/conflicts/reduce_reduce.cup"), 0, false, false);
        assertTrue(rr.reduceReduce() > 0, rr.output());

        ConflictLab.Result lalr = ConflictLab.run(GrammarInfo.resource("/conflicts/lalr_merge.cup"), 0, false, false);
        assertTrue(lalr.reduceReduce() > 0, lalr.output());

        ConflictLab.Result dangling = ConflictLab.run(GrammarInfo.resource("/conflicts/dangling_else.cup"), 1, false, false);
        assertEquals(1, dangling.shiftReduce(), dangling.output());
        assertTrue(dangling.generated(), dangling.output());

        ConflictLab.Result fixed = ConflictLab.run(GrammarInfo.resource("/conflicts/dangling_else_prec.cup"), 0, false, false);
        assertEquals(0, fixed.shiftReduce(), fixed.output());
    }
}
