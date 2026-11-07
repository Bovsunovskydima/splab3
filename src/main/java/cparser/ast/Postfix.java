package cparser.ast;

import java.util.List;

public final class Postfix extends Expr {
    public final String op;
    public final Expr operand;

    public Postfix(String op, Expr operand, int line, int column) {
        super(line, column);
        this.op = op;
        this.operand = operand;
    }

    @Override
    public String kind() {
        return "Postfix";
    }

    @Override
    public String label() {
        return "x" + op;
    }

    @Override
    public List<Child> children() {
        return list("operand", operand);
    }
}
