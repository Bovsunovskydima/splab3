package cparser.ast;

import java.util.List;

public final class For extends Node {
    public final Node init;
    public final Expr cond;
    public final Expr step;
    public final Node body;

    public For(Node init, Expr cond, Expr step, Node body, int line, int column) {
        super(line, column);
        this.init = init instanceof EmptyStmt ? null : init;
        this.cond = cond;
        this.step = step;
        this.body = body;
    }

    @Override
    public String kind() {
        return "For";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return "for";
    }

    @Override
    public List<Child> children() {
        return list("init", init, "cond", cond, "step", step, "body", body);
    }
}
