package cparser.ast;

import java.util.List;

public final class Binary extends Expr {
    public final String op;
    public final Expr left;
    public final Expr right;
    public Type operandType;

    public Binary(String op, Expr left, Expr right, int line, int column) {
        super(line, column);
        this.op = op;
        this.left = left;
        this.right = right;
    }

    @Override
    public String kind() {
        return "Binary";
    }

    @Override
    public String label() {
        return op;
    }

    @Override
    public List<Child> children() {
        return list("left", left, "right", right);
    }
}
