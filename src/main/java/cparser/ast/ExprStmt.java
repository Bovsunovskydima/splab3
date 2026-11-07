package cparser.ast;

import java.util.List;

public final class ExprStmt extends Node {
    public final Expr expr;

    public ExprStmt(Expr expr) {
        super(expr.line, expr.column);
        this.expr = expr;
    }

    @Override
    public String kind() {
        return "ExprStmt";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public List<Child> children() {
        return list("expr", expr);
    }
}
