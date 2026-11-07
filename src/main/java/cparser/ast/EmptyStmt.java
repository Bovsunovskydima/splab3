package cparser.ast;

public final class EmptyStmt extends Node {
    public EmptyStmt(int line, int column) {
        super(line, column);
    }

    @Override
    public String kind() {
        return "EmptyStmt";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return ";";
    }
}
