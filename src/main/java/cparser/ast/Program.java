package cparser.ast;

import java.util.List;

public final class Program extends Node {
    public final List<Node> items;

    public Program(List<Node> items) {
        super(1, 1);
        this.items = items;
    }

    @Override
    public String kind() {
        return "Program";
    }

    @Override
    public String category() {
        return "top";
    }

    @Override
    public List<Child> children() {
        return list("item", items);
    }
}
