package cparser.ast;

public final class Ident extends Expr {
    public final String name;

    public Ident(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    @Override
    public String kind() {
        return "Ident";
    }

    @Override
    public String label() {
        return name;
    }
}
