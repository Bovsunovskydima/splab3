package cparser;

public record Diagnostic(String severity, String phase, int line, int column, String message) {

    public static Diagnostic error(String phase, int line, int column, String message) {
        return new Diagnostic("error", phase, line, column, message);
    }

    public static Diagnostic warning(String phase, int line, int column, String message) {
        return new Diagnostic("warning", phase, line, column, message);
    }

    public boolean isError() {
        return severity.equals("error");
    }

    @Override
    public String toString() {
        String kind = isError() ? "помилка" : "попередження";
        return line + ":" + column + " " + kind + " [" + phase + "] " + message;
    }
}
