package cparser.ast;

import java.util.List;

public final class Assign extends Expr {
    public final String op;
    public final Expr target;
    public final Expr value;
    public Type operandType;

    public Assign(String op, Expr target, Expr value, int line, int column) {
        super(line, column);
        this.op = op;
        this.target = target;
        this.value = value;
    }

    public String arithmeticOp() {
        return op.substring(0, op.length() - 1);
    }

    @Override
    public String kind() {
        return "Assign";
    }

    @Override
    public String label() {
        return op;
    }

    @Override
    public List<Child> children() {
        return list("target", target, "value", value);
    }
}
