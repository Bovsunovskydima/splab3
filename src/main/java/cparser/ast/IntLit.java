package cparser.ast;

public final class IntLit extends Expr {
    public final long value;

    public IntLit(long value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    @Override
    public String kind() {
        return "IntLit";
    }

    @Override
    public String category() {
        return "literal";
    }

    @Override
    public String label() {
        return Long.toString(value);
    }
}
