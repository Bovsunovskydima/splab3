package cparser.ast;

import java.util.List;

public final class If extends Node {
    public final Expr cond;
    public final Node then;
    public final Node otherwise;

    public If(Expr cond, Node then, Node otherwise, int line, int column) {
        super(line, column);
        this.cond = cond;
        this.then = then;
        this.otherwise = otherwise;
    }

    @Override
    public String kind() {
        return "If";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return otherwise == null ? "if" : "if-else";
    }

    @Override
    public List<Child> children() {
        return list("cond", cond, "then", then, "else", otherwise);
    }
}
