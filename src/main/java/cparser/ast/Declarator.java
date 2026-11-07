package cparser.ast;

import java.util.ArrayList;
import java.util.List;

public final class Declarator extends Node {
    public final String name;
    public final List<Expr> dims = new ArrayList<>();
    public Node init;
    public Type type;
    public int[] inferredSizes;

    public Declarator(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    @Override
    public String kind() {
        return "Declarator";
    }

    @Override
    public String category() {
        return "decl";
    }

    @Override
    public String label() {
        StringBuilder sb = new StringBuilder(name);
        for (Expr dim : dims) {
            sb.append(dim == null ? "[]" : "[ ]");
        }
        return sb.toString();
    }

    @Override
    public List<Child> children() {
        return list("size", dims, "init", init);
    }
}
