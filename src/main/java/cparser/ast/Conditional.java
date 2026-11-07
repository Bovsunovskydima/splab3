package cparser.ast;

import java.util.List;

public final class Conditional extends Expr {
    public final Expr cond;
    public final Expr then;
    public final Expr otherwise;

    public Conditional(Expr cond, Expr then, Expr otherwise, int line, int column) {
        super(line, column);
        this.cond = cond;
        this.then = then;
        this.otherwise = otherwise;
    }

    @Override
    public String kind() {
        return "Conditional";
    }

    @Override
    public String label() {
        return "?:";
    }

    @Override
    public List<Child> children() {
        return list("cond", cond, "then", then, "else", otherwise);
    }
}
