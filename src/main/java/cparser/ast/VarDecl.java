package cparser.ast;

import java.util.List;

public final class VarDecl extends Node {
    public final Type baseType;
    public final List<Declarator> declarators;

    public VarDecl(Type baseType, List<Declarator> declarators, int line, int column) {
        super(line, column);
        this.baseType = baseType;
        this.declarators = declarators;
    }

    @Override
    public String kind() {
        return "VarDecl";
    }

    @Override
    public String category() {
        return "decl";
    }

    @Override
    public String label() {
        return "VarDecl " + baseType;
    }

    @Override
    public List<Child> children() {
        return list("var", declarators);
    }
}
