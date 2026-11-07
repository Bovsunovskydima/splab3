package cparser.ast;

import java.util.List;

public final class Index extends Expr {
    public final Expr array;
    public final Expr index;

    public Index(Expr array, Expr index, int line, int column) {
        super(line, column);
        this.array = array;
        this.index = index;
    }

    @Override
    public String kind() {
        return "Index";
    }

    @Override
    public String label() {
        return "[ ]";
    }

    @Override
    public List<Child> children() {
        return list("array", array, "index", index);
    }
}
