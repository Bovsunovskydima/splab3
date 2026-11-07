package cparser.ast;

public final class Param extends Node {
    public final Type type;
    public final String name;

    public Param(Type type, String name, int line, int column) {
        super(line, column);
        this.type = type;
        this.name = name;
    }

    @Override
    public String kind() {
        return "Param";
    }

    @Override
    public String category() {
        return "decl";
    }

    @Override
    public String label() {
        return type + " " + name;
    }
}
