package cparser.runtime;

import cparser.ast.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Interpreter {
    public record Result(String output, Integer exitCode, String error, int errorLine, long steps, long millis) {
    }

    private static final int MAX_DEPTH = 10_000;
    private static final int MAX_OUTPUT = 200_000;
    private static final long MAX_ARRAY = 20_000_000L;

    private static final class Variable {
        final Type type;
        Object value;

        Variable(Type type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final class Frame {
        final Map<String, Variable> vars = new HashMap<>();
        final Frame parent;

        Frame(Frame parent) {
            this.parent = parent;
        }

        Variable lookup(String name) {
            for (Frame f = this; f != null; f = f.parent) {
                Variable v = f.vars.get(name);
                if (v != null) {
                    return v;
                }
            }
            return null;
        }
    }

    public static final class ArrayValue {
        final Object[] items;
        final Type type;

        ArrayValue(Object[] items, Type type) {
            this.items = items;
            this.type = type;
        }
    }

    private interface LValue {
        Object get();

        void set(Object value);
    }

    private static final class BreakSignal extends RuntimeException {
        static final BreakSignal INSTANCE = new BreakSignal();

        BreakSignal() {
            super(null, null, false, false);
        }
    }

    private static final class ContinueSignal extends RuntimeException {
        static final ContinueSignal INSTANCE = new ContinueSignal();

        ContinueSignal() {
            super(null, null, false, false);
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Object value;

        ReturnSignal(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private final Program program;
    private final Map<String, FunctionDef> functions = new HashMap<>();
    private final Frame globals = new Frame(null);
    private final StringBuilder out = new StringBuilder();
    private final long maxSteps;
    private volatile boolean cancelled;
    private long steps;
    private int depth;
    private long allocated;

    private Interpreter(Program program, long maxSteps) {
        this.program = program;
        this.maxSteps = maxSteps;
        for (Node item : program.items) {
            if (item instanceof FunctionDef f && !f.isPrototype()) {
                functions.put(f.name, f);
            }
        }
    }

    public static Result run(Program program, long maxSteps, long timeoutMs) {
        Interpreter interpreter = new Interpreter(program, maxSteps);
        Object[] outcome = new Object[3];
        long start = System.nanoTime();
        Thread thread = new Thread(null, () -> interpreter.execute(outcome), "c-interpreter", 1L << 30);
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(timeoutMs);
            if (thread.isAlive()) {
                interpreter.cancelled = true;
                thread.join(2000);
                outcome[1] = "Перевищено ліміт часу виконання (" + timeoutMs / 1000 + " с)";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        String output;
        synchronized (interpreter.out) {
            output = interpreter.out.toString();
        }
        return new Result(output, (Integer) outcome[0], (String) outcome[1],
                outcome[2] == null ? 0 : (Integer) outcome[2], interpreter.steps, millis);
    }

    private void execute(Object[] outcome) {
        try {
            for (Node item : program.items) {
                if (item instanceof VarDecl d) {
                    declare(d, globals);
                }
            }
            FunctionDef main = functions.get("main");
            Object result = invoke(main, List.of(), main.line);
            outcome[0] = result instanceof Integer i ? i : 0;
        } catch (RuntimeError e) {
            outcome[1] = e.getMessage();
            outcome[2] = e.line;
        } catch (StackOverflowError e) {
            outcome[1] = "Переповнення стеку (занадто глибока рекурсія)";
        }
    }

    private void tick(Node node) {
        if (++steps > maxSteps) {
            throw new RuntimeError("Перевищено ліміт у " + maxSteps + " кроків (можливо, нескінченний цикл)", node.line);
        }
        if (cancelled) {
            throw new RuntimeError("Виконання перервано за тайм-аутом", node.line);
        }
    }

    private void print(String s, Node at) {
        synchronized (out) {
            out.append(s);
            if (out.length() > MAX_OUTPUT) {
                throw new RuntimeError("Занадто великий вивід (понад " + MAX_OUTPUT + " символів)", at.line);
            }
        }
    }

    private void declare(VarDecl decl, Frame frame) {
        for (Declarator d : decl.declarators) {
            tick(d);
            Object value;
            if (d.type.isArray()) {
                int[] sizes = new int[d.dims.size()];
                for (int i = 0; i < sizes.length; i++) {
                    Expr dim = d.dims.get(i);
                    sizes[i] = dim == null ? d.inferredSizes[i] : toInt(eval(dim, frame));
                    if (sizes[i] <= 0) {
                        throw new RuntimeError("Некоректний розмір масиву '" + d.name + "': " + sizes[i], d.line);
                    }
                }
                ArrayValue array = newArray(d.type, sizes, 0, d);
                if (d.init instanceof InitList list) {
                    fill(array, list, frame);
                }
                value = array;
            } else if (d.init != null) {
                value = convert(eval((Expr) d.init, frame), d.type);
            } else {
                value = zero(d.type);
            }
            frame.vars.put(d.name, new Variable(d.type, value));
        }
    }

    private ArrayValue newArray(Type type, int[] sizes, int k, Node at) {
        allocated += sizes[k];
        if (allocated > MAX_ARRAY) {
            throw new RuntimeError("Занадто великий масив (ліміт " + MAX_ARRAY + " елементів)", at.line);
        }
        Object[] items = new Object[sizes[k]];
        Type element = type.element();
        for (int i = 0; i < items.length; i++) {
            items[i] = element.isArray() ? newArray(element, sizes, k + 1, at) : zero(element);
        }
        return new ArrayValue(items, type);
    }

    private void fill(ArrayValue array, InitList list, Frame frame) {
        for (int i = 0; i < list.items.size(); i++) {
            if (i >= array.items.length) {
                throw new RuntimeError("Забагато ініціалізаторів для масиву розміру " + array.items.length, list.line);
            }
            Node item = list.items.get(i);
            if (item instanceof InitList inner) {
                fill((ArrayValue) array.items[i], inner, frame);
            } else {
                array.items[i] = convert(eval((Expr) item, frame), array.type.element());
            }
        }
    }

    private void exec(Node node, Frame frame) {
        tick(node);
        switch (node) {
            case VarDecl d -> declare(d, frame);
            case Block b -> {
                Frame inner = new Frame(frame);
                for (Node item : b.items) {
                    exec(item, inner);
                }
            }
            case ExprStmt s -> eval(s.expr, frame);
            case If s -> {
                if (truthy(eval(s.cond, frame))) {
                    exec(s.then, frame);
                } else if (s.otherwise != null) {
                    exec(s.otherwise, frame);
                }
            }
            case While s -> {
                while (truthy(eval(s.cond, frame))) {
                    try {
                        exec(s.body, frame);
                    } catch (BreakSignal e) {
                        break;
                    } catch (ContinueSignal e) {
                        tick(s);
                    }
                }
            }
            case DoWhile s -> {
                do {
                    try {
                        exec(s.body, frame);
                    } catch (BreakSignal e) {
                        break;
                    } catch (ContinueSignal e) {
                        tick(s);
                    }
                } while (truthy(eval(s.cond, frame)));
            }
            case For s -> {
                Frame loop = new Frame(frame);
                if (s.init != null) {
                    exec(s.init, loop);
                }
                while (s.cond == null || truthy(eval(s.cond, loop))) {
                    try {
                        exec(s.body, loop);
                    } catch (BreakSignal e) {
                        break;
                    } catch (ContinueSignal e) {
                        tick(s);
                    }
                    if (s.step != null) {
                        eval(s.step, loop);
                    }
                }
            }
            case Return r -> throw new ReturnSignal(r.value == null ? null : eval(r.value, frame));
            case Break b -> throw BreakSignal.INSTANCE;
            case Continue c -> throw ContinueSignal.INSTANCE;
            default -> {
            }
        }
    }

    private Object eval(Expr e, Frame frame) {
        tick(e);
        switch (e) {
            case IntLit lit -> {
                return (int) lit.value;
            }
            case FloatLit lit -> {
                return lit.value;
            }
            case CharLit lit -> {
                return lit.value;
            }
            case StringLit lit -> {
                return lit.value;
            }
            case Ident id -> {
                Variable v = frame.lookup(id.name);
                if (v == null) {
                    throw new RuntimeError("Змінну '" + id.name + "' не знайдено", id.line);
                }
                return v.value;
            }
            case Unary u -> {
                return unary(u, frame);
            }
            case Postfix p -> {
                LValue target = lvalue(p.operand, frame);
                Object old = target.get();
                target.set(step(old, p.op.equals("++") ? 1 : -1, p.operand.type));
                return old;
            }
            case Binary b -> {
                return binary(b, frame);
            }
            case Assign a -> {
                LValue target = lvalue(a.target, frame);
                Object value = eval(a.value, frame);
                Object result;
                if (a.op.equals("=")) {
                    result = convert(value, a.target.type);
                } else {
                    result = convert(arithmetic(a.arithmeticOp(), target.get(), value, a.operandType, a),
                            a.target.type);
                }
                target.set(result);
                return result;
            }
            case Conditional c -> {
                Expr branch = truthy(eval(c.cond, frame)) ? c.then : c.otherwise;
                return convert(eval(branch, frame), c.type);
            }
            case Call call -> {
                return call(call, frame);
            }
            case Index ix -> {
                ArrayValue array = (ArrayValue) eval(ix.array, frame);
                int index = toInt(eval(ix.index, frame));
                check(array, index, ix);
                return array.items[index];
            }
            case Cast c -> {
                return convert(eval(c.expr, frame), c.target);
            }
            default -> throw new RuntimeError("Невідомий вираз " + e.kind(), e.line);
        }
    }

    private Object unary(Unary u, Frame frame) {
        switch (u.op) {
            case "++", "--" -> {
                LValue target = lvalue(u.operand, frame);
                Object value = step(target.get(), u.op.equals("++") ? 1 : -1, u.operand.type);
                target.set(value);
                return value;
            }
            default -> {
            }
        }
        Object v = eval(u.operand, frame);
        return switch (u.op) {
            case "-" -> u.type.isFloating() ? convert(-toDouble(v), u.type) : (Object) (-toInt(v));
            case "+" -> convert(v, u.type);
            case "!" -> truthy(v) ? 0 : 1;
            case "~" -> ~toInt(v);
            default -> throw new RuntimeError("Невідомий унарний оператор " + u.op, u.line);
        };
    }

    private Object binary(Binary b, Frame frame) {
        if (b.op.equals("&&")) {
            return truthy(eval(b.left, frame)) && truthy(eval(b.right, frame)) ? 1 : 0;
        }
        if (b.op.equals("||")) {
            return truthy(eval(b.left, frame)) || truthy(eval(b.right, frame)) ? 1 : 0;
        }
        Object l = eval(b.left, frame);
        Object r = eval(b.right, frame);
        switch (b.op) {
            case "<", ">", "<=", ">=", "==", "!=" -> {
                int cmp;
                if (b.operandType.isFloating()) {
                    double x = toDouble(l);
                    double y = toDouble(r);
                    if (Double.isNaN(x) || Double.isNaN(y)) {
                        return b.op.equals("!=") ? 1 : 0;
                    }
                    cmp = Double.compare(x, y);
                    if (x == y) {
                        cmp = 0;
                    }
                } else {
                    cmp = Integer.compare(toInt(l), toInt(r));
                }
                boolean result = switch (b.op) {
                    case "<" -> cmp < 0;
                    case ">" -> cmp > 0;
                    case "<=" -> cmp <= 0;
                    case ">=" -> cmp >= 0;
                    case "==" -> cmp == 0;
                    default -> cmp != 0;
                };
                return result ? 1 : 0;
            }
            default -> {
                return arithmetic(b.op, l, r, b.operandType, b);
            }
        }
    }

    private Object arithmetic(String op, Object l, Object r, Type type, Node at) {
        if (type.isFloating()) {
            double x = toDouble(l);
            double y = toDouble(r);
            double result = switch (op) {
                case "+" -> x + y;
                case "-" -> x - y;
                case "*" -> x * y;
                case "/" -> x / y;
                default -> throw new RuntimeError("Оператор " + op + " не застосовний до дійсних чисел", at.line);
            };
            return convert(result, type);
        }
        int x = toInt(l);
        int y = toInt(r);
        return switch (op) {
            case "+" -> x + y;
            case "-" -> x - y;
            case "*" -> x * y;
            case "/" -> {
                if (y == 0) {
                    throw new RuntimeError("Ділення на нуль", at.line);
                }
                yield x / y;
            }
            case "%" -> {
                if (y == 0) {
                    throw new RuntimeError("Ділення на нуль (остача)", at.line);
                }
                yield x % y;
            }
            case "<<" -> x << y;
            case ">>" -> x >> y;
            case "&" -> x & y;
            case "|" -> x | y;
            case "^" -> x ^ y;
            default -> throw new RuntimeError("Невідомий оператор " + op, at.line);
        };
    }

    private Object step(Object value, int delta, Type type) {
        if (type.isFloating()) {
            return convert(toDouble(value) + delta, type);
        }
        return convert(toInt(value) + delta, type);
    }

    private LValue lvalue(Expr e, Frame frame) {
        if (e instanceof Ident id) {
            Variable v = frame.lookup(id.name);
            if (v == null) {
                throw new RuntimeError("Змінну '" + id.name + "' не знайдено", id.line);
            }
            return new LValue() {
                public Object get() {
                    return v.value;
                }

                public void set(Object value) {
                    v.value = value;
                }
            };
        }
        if (e instanceof Index ix) {
            ArrayValue array = (ArrayValue) eval(ix.array, frame);
            int index = toInt(eval(ix.index, frame));
            check(array, index, ix);
            return new LValue() {
                public Object get() {
                    return array.items[index];
                }

                public void set(Object value) {
                    array.items[index] = value;
                }
            };
        }
        throw new RuntimeError("Вираз не може стояти ліворуч від присвоєння", e.line);
    }

    private void check(ArrayValue array, int index, Node at) {
        if (index < 0 || index >= array.items.length) {
            throw new RuntimeError("Вихід за межі масиву: індекс " + index + ", розмір " + array.items.length, at.line);
        }
    }

    private Object call(Call call, Frame frame) {
        List<Object> args = new ArrayList<>(call.args.size());
        for (Expr arg : call.args) {
            args.add(eval(arg, frame));
        }
        FunctionDef f = functions.get(call.name);
        if (f != null) {
            return invoke(f, args, call.line);
        }
        return builtin(call, args);
    }

    private Object invoke(FunctionDef f, List<Object> args, int line) {
        if (++depth > MAX_DEPTH) {
            depth = 0;
            throw new RuntimeError("Переповнення стеку викликів (глибина понад " + MAX_DEPTH + ")", line);
        }
        Frame frame = new Frame(globals);
        for (int i = 0; i < f.params.size(); i++) {
            Param p = f.params.get(i);
            Object value = p.type.isArray() ? args.get(i) : convert(args.get(i), p.type);
            frame.vars.put(p.name, new Variable(p.type, value));
        }
        Object result = null;
        try {
            for (Node item : f.body.items) {
                exec(item, frame);
            }
        } catch (ReturnSignal r) {
            result = r.value;
        } finally {
            depth--;
        }
        if (f.returnType.isVoid()) {
            return null;
        }
        return result == null ? zero(f.returnType) : convert(result, f.returnType);
    }

    private Object builtin(Call call, List<Object> args) {
        switch (call.name) {
            case "printf" -> {
                String text = CFormat.format((String) args.get(0), args.subList(1, args.size()));
                print(text, call);
                return text.length();
            }
            case "puts" -> {
                print(args.get(0) + "\n", call);
                return 1;
            }
            case "putchar" -> {
                int c = toInt(args.get(0));
                print(String.valueOf((char) (c & 0xFF)), call);
                return c;
            }
            case "abs" -> {
                return Math.abs(toInt(args.get(0)));
            }
            case "pow" -> {
                return Math.pow(toDouble(args.get(0)), toDouble(args.get(1)));
            }
            default -> {
            }
        }
        double x = toDouble(args.get(0));
        return switch (call.name) {
            case "sqrt" -> Math.sqrt(x);
            case "fabs" -> Math.abs(x);
            case "floor" -> Math.floor(x);
            case "ceil" -> Math.ceil(x);
            case "sin" -> Math.sin(x);
            case "cos" -> Math.cos(x);
            case "exp" -> Math.exp(x);
            case "log" -> Math.log(x);
            default -> throw new RuntimeError("Невідома функція " + call.name, call.line);
        };
    }

    static boolean truthy(Object v) {
        if (v instanceof Integer i) {
            return i != 0;
        }
        if (v instanceof Double d) {
            return d != 0.0;
        }
        return v != null;
    }

    static int toInt(Object v) {
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Double d) {
            return (int) d.doubleValue();
        }
        return 0;
    }

    static double toDouble(Object v) {
        if (v instanceof Double d) {
            return d;
        }
        if (v instanceof Integer i) {
            return i;
        }
        return 0;
    }

    static Object convert(Object v, Type type) {
        if (type == null || type.isArray() || v instanceof ArrayValue || v instanceof String) {
            return v;
        }
        return switch (type.base) {
            case INT -> toInt(v);
            case CHAR -> (int) (byte) toInt(v);
            case FLOAT -> (double) (float) toDouble(v);
            case DOUBLE -> toDouble(v);
            default -> v;
        };
    }

    static Object zero(Type type) {
        return type.isFloating() ? (Object) 0.0 : (Object) 0;
    }
}
