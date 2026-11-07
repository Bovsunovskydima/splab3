package cparser.ast;

import java.util.List;

public final class Call extends Expr {
    public final String name;
    public final List<Expr> args;

    public Call(String name, List<Expr> args, int line, int column) {
        super(line, column);
        this.name = name;
        this.args = args;
    }

    @Override
    public String kind() {
        return "Call";
    }

    @Override
    public String label() {
        return name + "()";
    }

    @Override
    public List<Child> children() {
        return list("arg", args);
    }
}
