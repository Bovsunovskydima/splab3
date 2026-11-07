package cparser.runtime;

public final class RuntimeError extends RuntimeException {
    public final int line;

    public RuntimeError(String message, int line) {
        super(message, null, false, false);
        this.line = line;
    }
}
