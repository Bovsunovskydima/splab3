package cparser.ast;

public final class Continue extends Node {
    public Continue(int line, int column) {
        super(line, column);
    }

    @Override
    public String kind() {
        return "Continue";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return "continue";
    }
}
