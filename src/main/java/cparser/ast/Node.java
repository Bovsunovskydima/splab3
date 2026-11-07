package cparser.ast;

import java.util.ArrayList;
import java.util.List;

public abstract class Node {
    public final int line;
    public final int column;

    protected Node(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public abstract String kind();

    public abstract String category();

    public String label() {
        return kind();
    }

    public List<Child> children() {
        return List.of();
    }

    protected static List<Child> list(Object... pairs) {
        List<Child> result = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String role = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof Node node) {
                result.add(new Child(role, node));
            } else if (value instanceof List<?> nodes) {
                for (Object item : nodes) {
                    if (item instanceof Node node) {
                        result.add(new Child(role, node));
                    }
                }
            }
        }
        return result;
    }

    public record Child(String role, Node node) {
    }
}
