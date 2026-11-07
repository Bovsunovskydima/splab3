package cparser;

public final class LexerException extends RuntimeException {
    public final int line;
    public final int column;

    public LexerException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }
}
