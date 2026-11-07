package cparser.ast;

import java.util.List;

public final class Cast extends Expr {
    public final Type target;
    public final Expr expr;

    public Cast(Type target, Expr expr, int line, int column) {
        super(line, column);
        this.target = target;
        this.expr = expr;
    }

    @Override
    public String kind() {
        return "Cast";
    }

    @Override
    public String label() {
        return "(" + target + ")";
    }

    @Override
    public List<Child> children() {
        return list("expr", expr);
    }
}
