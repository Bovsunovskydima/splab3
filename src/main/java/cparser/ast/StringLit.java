package cparser.ast;

public final class StringLit extends Expr {
    public final String value;

    public StringLit(String value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    @Override
    public String kind() {
        return "StringLit";
    }

    @Override
    public String category() {
        return "literal";
    }

    @Override
    public String label() {
        String shown = value.replace("\n", "\\n").replace("\t", "\\t");
        if (shown.length() > 24) {
            shown = shown.substring(0, 21) + "...";
        }
        return "\"" + shown + "\"";
    }
}
