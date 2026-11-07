package cparser.ast;

public final class FloatLit extends Expr {
    public final double value;

    public FloatLit(double value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    @Override
    public String kind() {
        return "FloatLit";
    }

    @Override
    public String category() {
        return "literal";
    }

    @Override
    public String label() {
        return Double.toString(value);
    }
}
