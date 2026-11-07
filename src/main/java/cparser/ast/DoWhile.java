package cparser.ast;

import java.util.List;

public final class DoWhile extends Node {
    public final Node body;
    public final Expr cond;

    public DoWhile(Node body, Expr cond, int line, int column) {
        super(line, column);
        this.body = body;
        this.cond = cond;
    }

    @Override
    public String kind() {
        return "DoWhile";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return "do-while";
    }

    @Override
    public List<Child> children() {
        return list("body", body, "cond", cond);
    }
}
