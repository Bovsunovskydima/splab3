package cparser.ast;

import java.util.List;

public final class Unary extends Expr {
    public final String op;
    public final Expr operand;

    public Unary(String op, Expr operand, int line, int column) {
        super(line, column);
        this.op = op;
        this.operand = operand;
    }

    @Override
    public String kind() {
        return "Unary";
    }

    @Override
    public String label() {
        return op.length() == 2 ? op + "x" : op;
    }

    @Override
    public List<Child> children() {
        return list("operand", operand);
    }
}
