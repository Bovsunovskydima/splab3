package cparser.ast;

public abstract class Expr extends Node {
    public Type type;

    protected Expr(int line, int column) {
        super(line, column);
    }

    @Override
    public String category() {
        return "expr";
    }
}
