package cparser.ast;

import java.util.List;

public final class FunctionDef extends Node {
    public final Type returnType;
    public final String name;
    public final List<Param> params;
    public final Block body;

    public FunctionDef(Type returnType, String name, List<Param> params, Block body, int line, int column) {
        super(line, column);
        this.returnType = returnType;
        this.name = name;
        this.params = params;
        this.body = body;
    }

    public boolean isPrototype() {
        return body == null;
    }

    @Override
    public String kind() {
        return isPrototype() ? "FunctionDecl" : "FunctionDef";
    }

    @Override
    public String category() {
        return "top";
    }

    @Override
    public String label() {
        return returnType + " " + name + "()";
    }

    @Override
    public List<Child> children() {
        return list("param", params, "body", body);
    }
}
