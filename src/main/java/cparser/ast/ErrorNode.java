package cparser.ast;

public final class ErrorNode extends Node {
    public ErrorNode(int line, int column) {
        super(line, column);
    }

    @Override
    public String kind() {
        return "Error";
    }

    @Override
    public String category() {
        return "error";
    }

    @Override
    public String label() {
        return "error";
    }
}
