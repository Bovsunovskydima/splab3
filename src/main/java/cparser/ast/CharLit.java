package cparser.ast;

public final class CharLit extends Expr {
    public final int value;

    public CharLit(int value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    @Override
    public String kind() {
        return "CharLit";
    }

    @Override
    public String category() {
        return "literal";
    }

    @Override
    public String label() {
        if (value >= 32 && value < 127) {
            return "'" + (char) value + "'";
        }
        return "'\\" + value + "'";
    }
}
