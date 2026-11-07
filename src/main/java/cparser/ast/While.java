package cparser.ast;

import java.util.List;

public final class While extends Node {
    public final Expr cond;
    public final Node body;

    public While(Expr cond, Node body, int line, int column) {
        super(line, column);
        this.cond = cond;
        this.body = body;
    }

    @Override
    public String kind() {
        return "While";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return "while";
    }

    @Override
    public List<Child> children() {
        return list("cond", cond, "body", body);
    }
}
