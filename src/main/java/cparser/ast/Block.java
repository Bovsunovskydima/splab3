package cparser.ast;

import java.util.List;

public final class Block extends Node {
    public final List<Node> items;

    public Block(List<Node> items, int line, int column) {
        super(line, column);
        this.items = items;
    }

    @Override
    public String kind() {
        return "Block";
    }

    @Override
    public String category() {
        return "stmt";
    }

    @Override
    public List<Child> children() {
        return list("item", items);
    }
}
