package cparser.ast;

public final class Break extends Node {
    public Break(int line, int column) {
        super(line, column);
    }

    @Override
    public String kind() {
        return "Break";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return "break";
    }
}
