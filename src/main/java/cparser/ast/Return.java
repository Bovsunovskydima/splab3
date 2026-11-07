package cparser.ast;

import java.util.List;

public final class Return extends Node {
    public final Expr value;

    public Return(Expr value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    @Override
    public String kind() {
        return "Return";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public String label() {
        return "return";
    }

    @Override
    public List<Child> children() {
        return list("value", value);
    }
}
