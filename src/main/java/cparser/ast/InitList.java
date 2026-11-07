package cparser.ast;

import java.util.List;

public final class InitList extends Node {
    public final List<Node> items;

    public InitList(List<Node> items, int line, int column) {
        super(line, column);
        this.items = items;
    }

    @Override
    public String kind() {
        return "InitList";
    }

    @Override
    public String category() {
        return "decl";
    }

    @Override
    public String label() {
        return "{ " + items.size() + " }";
    }

    @Override
    public List<Child> children() {
        return list("item", items);
    }
}
