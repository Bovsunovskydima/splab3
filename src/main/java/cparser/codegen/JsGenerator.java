package cparser.codegen;

import cparser.ast.*;
import cparser.grammar.GrammarInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsGenerator {
    private static final Set<String> RESERVED = Set.of(
            "abstract", "arguments", "await", "boolean", "byte", "case", "catch", "class", "const", "debugger",
            "default", "delete", "enum", "eval", "export", "extends", "false", "final", "finally", "function",
            "goto", "implements", "import", "in", "instanceof", "interface", "let", "long", "native", "new", "null",
            "package", "private", "protected", "public", "short", "static", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try", "typeof", "var", "volatile", "with", "yield",
            "undefined", "NaN", "Infinity", "Math", "Array", "Number", "String", "Object", "Error", "console",
            "process", "globalThis", "window", "document");

    private final StringBuilder sb = new StringBuilder();
    private final Map<String, FunctionDef> functions = new HashMap<>();
    private Type currentReturn = Type.INT;
    private int indent;

    public static String generate(Program program) {
        JsGenerator g = new JsGenerator();
        g.program(program);
        return g.sb.toString();
    }

    private void line(String text) {
        sb.append("    ".repeat(indent)).append(text).append('\n');
    }

    private void program(Program program) {
        sb.append(GrammarInfo.resource("/js/prelude.js")).append('\n');
        for (Node item : program.items) {
            if (item instanceof FunctionDef f) {
                functions.putIfAbsent(f.name, f);
            }
        }
        FunctionDef main = null;
        for (Node item : program.items) {
            if (item instanceof FunctionDef f) {
                if (!f.isPrototype()) {
                    function(f);
                    sb.append('\n');
                    if (f.name.equals("main")) {
                        main = f;
                    }
                }
            } else if (item instanceof VarDecl d) {
                declaration(d);
            }
        }
        if (main != null) {
            line("const __exitCode = " + name("main") + "();");
        }
        line("if (typeof process !== \"undefined\" && process.stdout) process.stdout.write(__out.join(\"\"));");
    }

    private void function(FunctionDef f) {
        List<String> params = new ArrayList<>();
        for (Param p : f.params) {
            params.add(name(p.name));
        }
        currentReturn = f.returnType;
        line("function " + name(f.name) + "(" + String.join(", ", params) + ") {");
        indent++;
        for (Node item : f.body.items) {
            statement(item);
        }
        if (f.returnType.isVoid()) {
            line("return 0;");
        }
        indent--;
        line("}");
    }

    private void declaration(VarDecl d) {
        line("let " + declarators(d) + ";");
    }

    private String declarators(VarDecl d) {
        List<String> parts = new ArrayList<>();
        for (Declarator decl : d.declarators) {
            parts.add(name(decl.name) + " = " + initial(decl));
        }
        return String.join(", ", parts);
    }

    private String initial(Declarator d) {
        if (d.type.isArray()) {
            List<String> dims = new ArrayList<>();
            for (int i = 0; i < d.dims.size(); i++) {
                Expr dim = d.dims.get(i);
                dims.add(dim == null ? Integer.toString(d.inferredSizes[i]) : expr(dim));
            }
            String sizes = "[" + String.join(", ", dims) + "]";
            if (d.init instanceof InitList list) {
                return "__arrayInit(" + sizes + ", " + initList(list, d.type.element()) + ")";
            }
            return "__array(" + sizes + ")";
        }
        if (d.init == null) {
            return "0";
        }
        Expr init = (Expr) d.init;
        return convert(expr(init), init.type, d.type);
    }

    private String initList(InitList list, Type element) {
        List<String> items = new ArrayList<>();
        for (Node item : list.items) {
            if (item instanceof InitList inner) {
                items.add(initList(inner, element.element()));
            } else {
                Expr e = (Expr) item;
                items.add(convert(expr(e), e.type, element));
            }
        }
        return "[" + String.join(", ", items) + "]";
    }

    private void body(Node node) {
        if (node instanceof Block b) {
            for (Node item : b.items) {
                statement(item);
            }
        } else {
            statement(node);
        }
    }

    private void statement(Node node) {
        switch (node) {
            case VarDecl d -> declaration(d);
            case Block b -> {
                line("{");
                indent++;
                body(b);
                indent--;
                line("}");
            }
            case ExprStmt s -> line(strip(expr(s.expr)) + ";");
            case EmptyStmt s -> line(";");
            case If s -> {
                line("if (" + strip(condition(s.cond)) + ") {");
                indent++;
                body(s.then);
                indent--;
                Node otherwise = s.otherwise;
                while (otherwise instanceof If chained) {
                    line("} else if (" + strip(condition(chained.cond)) + ") {");
                    indent++;
                    body(chained.then);
                    indent--;
                    otherwise = chained.otherwise;
                }
                if (otherwise != null) {
                    line("} else {");
                    indent++;
                    body(otherwise);
                    indent--;
                }
                line("}");
            }
            case While s -> {
                line("while (" + strip(condition(s.cond)) + ") {");
                indent++;
                body(s.body);
                indent--;
                line("}");
            }
            case DoWhile s -> {
                line("do {");
                indent++;
                body(s.body);
                indent--;
                line("} while (" + strip(condition(s.cond)) + ");");
            }
            case For s -> {
                String init = "";
                if (s.init instanceof VarDecl d) {
                    init = "let " + declarators(d);
                } else if (s.init instanceof ExprStmt e) {
                    init = strip(expr(e.expr));
                }
                String cond = s.cond == null ? "" : strip(condition(s.cond));
                String step = s.step == null ? "" : strip(expr(s.step));
                line("for (" + init + "; " + cond + "; " + step + ") {");
                indent++;
                body(s.body);
                indent--;
                line("}");
            }
            case Return r -> {
                if (r.value == null) {
                    line("return 0;");
                } else {
                    line("return " + strip(convert(expr(r.value), r.value.type, currentReturn)) + ";");
                }
            }
            case Break b -> line("break;");
            case Continue c -> line("continue;");
            default -> {
            }
        }
    }

    private String condition(Expr e) {
        if (e instanceof Binary b) {
            switch (b.op) {
                case "&&", "||" -> {
                    return "(" + condition(b.left) + " " + b.op + " " + condition(b.right) + ")";
                }
                case "==" -> {
                    return "(" + expr(b.left) + " === " + expr(b.right) + ")";
                }
                case "!=" -> {
                    return "(" + expr(b.left) + " !== " + expr(b.right) + ")";
                }
                case "<", ">", "<=", ">=" -> {
                    return "(" + expr(b.left) + " " + b.op + " " + expr(b.right) + ")";
                }
                default -> {
                }
            }
        }
        if (e instanceof Unary u && u.op.equals("!")) {
            return "!" + condition(u.operand);
        }
        return expr(e);
    }

    private String expr(Expr e) {
        switch (e) {
            case IntLit lit -> {
                return Integer.toString((int) lit.value);
            }
            case FloatLit lit -> {
                String s = Double.toString(lit.value);
                return s.startsWith("-") ? "(" + s + ")" : s;
            }
            case CharLit lit -> {
                return Integer.toString(lit.value);
            }
            case StringLit lit -> {
                return quote(lit.value);
            }
            case Ident id -> {
                return name(id.name);
            }
            case Unary u -> {
                return unary(u);
            }
            case Postfix p -> {
                return "(" + expr(p.operand) + p.op + ")";
            }
            case Binary b -> {
                return binary(b);
            }
            case Assign a -> {
                return assign(a);
            }
            case Conditional c -> {
                return "(" + condition(c.cond) + " ? " + convert(expr(c.then), c.then.type, c.type) + " : "
                        + convert(expr(c.otherwise), c.otherwise.type, c.type) + ")";
            }
            case Call call -> {
                return call(call);
            }
            case Index ix -> {
                return expr(ix.array) + "[" + strip(expr(ix.index)) + "]";
            }
            case Cast c -> {
                return convert(expr(c.expr), c.expr.type, c.target);
            }
            default -> throw new IllegalStateException(e.kind());
        }
    }

    private String unary(Unary u) {
        String operand = expr(u.operand);
        return switch (u.op) {
            case "-" -> u.type.isFloating() ? wrapFloat("(-" + operand + ")", u.type) : "(-" + operand + " | 0)";
            case "+" -> convert(operand, u.operand.type, u.type);
            case "!" -> "(" + operand + " ? 0 : 1)";
            case "~" -> "(~" + operand + ")";
            default -> "(" + u.op + operand + ")";
        };
    }

    private String binary(Binary b) {
        String l = expr(b.left);
        String r = expr(b.right);
        return switch (b.op) {
            case "&&", "||" -> "(" + l + " " + b.op + " " + r + " ? 1 : 0)";
            case "==" -> "(" + l + " === " + r + " ? 1 : 0)";
            case "!=" -> "(" + l + " !== " + r + " ? 1 : 0)";
            case "<", ">", "<=", ">=" -> "(" + l + " " + b.op + " " + r + " ? 1 : 0)";
            default -> arithmetic(b.op, l, r, b.operandType);
        };
    }

    private String arithmetic(String op, String l, String r, Type type) {
        if (type.isFloating()) {
            return wrapFloat("(" + l + " " + op + " " + r + ")", type);
        }
        return switch (op) {
            case "+", "-" -> "((" + l + " " + op + " " + r + ") | 0)";
            case "*" -> "Math.imul(" + l + ", " + r + ")";
            case "/" -> "__idiv(" + l + ", " + r + ")";
            case "%" -> "__imod(" + l + ", " + r + ")";
            default -> "(" + l + " " + op + " " + r + ")";
        };
    }

    private String assign(Assign a) {
        String target = expr(a.target);
        String value = expr(a.value);
        if (a.op.equals("=")) {
            return "(" + target + " = " + strip(convert(value, a.value.type, a.target.type)) + ")";
        }
        String computed = arithmetic(a.arithmeticOp(), target, value, a.operandType);
        return "(" + target + " = " + strip(convert(computed, a.operandType, a.target.type)) + ")";
    }

    private String call(Call call) {
        List<String> args = new ArrayList<>();
        for (Expr arg : call.args) {
            args.add(strip(expr(arg)));
        }
        String joined = String.join(", ", args);
        return switch (call.name) {
            case "printf" -> "__printf(" + joined + ")";
            case "puts" -> "__puts(" + joined + ")";
            case "putchar" -> "__putchar(" + joined + ")";
            case "abs", "fabs" -> "Math.abs(" + joined + ")";
            case "sqrt", "pow", "floor", "ceil", "sin", "cos", "exp", "log" -> "Math." + call.name + "(" + joined + ")";
            default -> name(call.name) + "(" + userArgs(call) + ")";
        };
    }

    private String userArgs(Call call) {
        FunctionDef f = functions.get(call.name);
        List<String> args = new ArrayList<>();
        for (int i = 0; i < call.args.size(); i++) {
            Expr arg = call.args.get(i);
            Type param = f != null && i < f.params.size() ? f.params.get(i).type : arg.type;
            args.add(strip(convert(expr(arg), arg.type, param)));
        }
        return String.join(", ", args);
    }

    private static String wrapFloat(String code, Type type) {
        return type.base == Type.Base.FLOAT ? "Math.fround" + code : code;
    }

    private static String convert(String code, Type from, Type to) {
        if (to == null || from == null || to.isArray() || from.equals(to) || to.isVoid()) {
            return code;
        }
        return switch (to.base) {
            case INT -> from.isFloating() ? "__toInt(" + strip(code) + ")" : code;
            case CHAR -> "__toChar(" + strip(code) + ")";
            case FLOAT -> "Math.fround(" + strip(code) + ")";
            default -> code;
        };
    }

    private static String strip(String code) {
        if (code.length() < 2 || code.charAt(0) != '(' || code.charAt(code.length() - 1) != ')') {
            return code;
        }
        int depth = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"') {
                i = skipString(code, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i < code.length() - 1) {
                    return code;
                }
            }
        }
        return code.substring(1, code.length() - 1);
    }

    private static int skipString(String code, int start) {
        for (int i = start + 1; i < code.length(); i++) {
            if (code.charAt(i) == '\\') {
                i++;
            } else if (code.charAt(i) == '"') {
                return i;
            }
        }
        return code.length();
    }

    private static String name(String name) {
        return RESERVED.contains(name) || name.startsWith("__") || name.startsWith("$") ? "$" + name : name;
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32 || c == 127 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
