package cparser.semantic;

import cparser.Diagnostic;
import cparser.ast.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Analyzer {
    public static final class FunctionInfo {
        public final String name;
        public final Type returnType;
        public final List<Type> params;
        public final boolean variadic;
        public final boolean builtin;
        public FunctionDef definition;

        FunctionInfo(String name, Type returnType, List<Type> params, boolean variadic, boolean builtin) {
            this.name = name;
            this.returnType = returnType;
            this.params = params;
            this.variadic = variadic;
            this.builtin = builtin;
        }
    }

    public static final Map<String, FunctionInfo> BUILTINS = new LinkedHashMap<>();

    static {
        builtin("printf", Type.INT, true, Type.STRING);
        builtin("puts", Type.INT, false, Type.STRING);
        builtin("putchar", Type.INT, false, Type.INT);
        builtin("abs", Type.INT, false, Type.INT);
        builtin("pow", Type.DOUBLE, false, Type.DOUBLE, Type.DOUBLE);
        for (String name : List.of("sqrt", "fabs", "floor", "ceil", "sin", "cos", "exp", "log")) {
            builtin(name, Type.DOUBLE, false, Type.DOUBLE);
        }
    }

    private static void builtin(String name, Type ret, boolean variadic, Type... params) {
        BUILTINS.put(name, new FunctionInfo(name, ret, List.of(params), variadic, true));
    }

    private record Variable(String name, Type type) {
    }

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<String, FunctionInfo> functions = new LinkedHashMap<>();
    private final Deque<Map<String, Variable>> scopes = new ArrayDeque<>();
    private final Set<String> called = new HashSet<>();
    private final Map<String, Call> firstCall = new HashMap<>();
    private FunctionDef current;
    private int loops;

    public static List<Diagnostic> analyze(Program program) {
        Analyzer analyzer = new Analyzer();
        analyzer.run(program);
        return analyzer.diagnostics;
    }

    public static boolean hasMain(Program program) {
        for (Node item : program.items) {
            if (item instanceof FunctionDef f && f.name.equals("main") && !f.isPrototype()) {
                return true;
            }
        }
        return false;
    }

    private void error(Node at, String message) {
        diagnostics.add(Diagnostic.error("semantic", at.line, at.column, message));
    }

    private void warning(Node at, String message) {
        diagnostics.add(Diagnostic.warning("semantic", at.line, at.column, message));
    }

    private void run(Program program) {
        scopes.push(new HashMap<>());
        for (Node item : program.items) {
            if (item instanceof FunctionDef f) {
                function(f);
            } else if (item instanceof VarDecl d) {
                declaration(d);
            }
        }
        for (String name : called) {
            FunctionInfo f = functions.get(name);
            if (f != null && f.definition == null) {
                error(firstCall.get(name), "Функцію '" + name + "' оголошено, але ніде не визначено");
            }
        }
        FunctionInfo main = functions.get("main");
        if (main == null || main.definition == null) {
            diagnostics.add(Diagnostic.warning("semantic", 1, 1,
                    "Функцію main не знайдено: програму розібрано, але виконати її неможливо"));
        } else {
            if (!main.returnType.equals(Type.INT) && !main.returnType.isVoid()) {
                warning(main.definition, "Функція main має повертати int");
            }
            if (!main.params.isEmpty()) {
                warning(main.definition, "Параметри функції main ігноруються");
            }
        }
    }

    private void function(FunctionDef f) {
        if (BUILTINS.containsKey(f.name)) {
            error(f, "Не можна перевизначати стандартну функцію '" + f.name + "'");
            return;
        }
        if (scopes.getLast().containsKey(f.name)) {
            error(f, "Ім'я '" + f.name + "' вже використано для глобальної змінної");
        }
        List<Type> types = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Param p : f.params) {
            if (p.type.isVoid() || (p.type.base == Type.Base.VOID)) {
                error(p, "Параметр '" + p.name + "' не може мати тип void");
            }
            if (!names.add(p.name)) {
                error(p, "Повторна назва параметра '" + p.name + "'");
            }
            types.add(p.type);
        }
        FunctionInfo existing = functions.get(f.name);
        FunctionInfo info;
        if (existing != null) {
            if (!existing.returnType.equals(f.returnType) || !existing.params.equals(types)) {
                error(f, "Оголошення функції '" + f.name + "' не збігається з попереднім");
            }
            if (existing.definition != null && !f.isPrototype()) {
                error(f, "Повторне визначення функції '" + f.name + "'");
            }
            info = existing;
        } else {
            info = new FunctionInfo(f.name, f.returnType, types, false, false);
            functions.put(f.name, info);
        }
        if (f.isPrototype()) {
            return;
        }
        info.definition = f;
        current = f;
        Map<String, Variable> scope = new HashMap<>();
        for (Param p : f.params) {
            scope.put(p.name, new Variable(p.name, p.type));
        }
        scopes.push(scope);
        for (Node item : f.body.items) {
            statement(item);
        }
        scopes.pop();
        current = null;
    }

    private void declaration(VarDecl d) {
        for (Declarator decl : d.declarators) {
            Type type = d.baseType.withRank(decl.dims.size());
            decl.type = type;
            if (d.baseType.isVoid()) {
                error(decl, "Змінна '" + decl.name + "' не може мати тип void");
            }
            decl.inferredSizes = new int[decl.dims.size()];
            for (int i = 0; i < decl.dims.size(); i++) {
                Expr dim = decl.dims.get(i);
                if (dim == null) {
                    if (i != 0) {
                        error(decl, "Розмір можна пропустити лише для першого виміру масиву '" + decl.name + "'");
                    } else if (!(decl.init instanceof InitList)) {
                        error(decl, "Не вказано розмір масиву '" + decl.name + "'");
                    }
                    continue;
                }
                Type t = expression(dim);
                if (!t.isIntegral()) {
                    error(dim, "Розмір масиву має бути цілим числом");
                }
                if (dim instanceof IntLit lit) {
                    if (lit.value <= 0) {
                        error(dim, "Розмір масиву має бути додатним");
                    }
                    decl.inferredSizes[i] = (int) lit.value;
                }
            }
            if (decl.init != null) {
                if (type.isArray()) {
                    if (decl.init instanceof InitList list) {
                        initList(list, type, decl, 0);
                        if (decl.dims.get(0) == null) {
                            decl.inferredSizes[0] = list.items.size();
                        }
                    } else {
                        error(decl.init, "Масив '" + decl.name + "' можна ініціалізувати лише списком { ... }");
                    }
                } else if (decl.init instanceof InitList) {
                    error(decl.init, "Скалярну змінну '" + decl.name + "' не можна ініціалізувати списком");
                } else {
                    Expr init = (Expr) decl.init;
                    assignable(type, expression(init), init);
                }
            }
            Map<String, Variable> scope = scopes.peek();
            if (scope.containsKey(decl.name)) {
                error(decl, "Повторне оголошення змінної '" + decl.name + "' в одній області видимості");
            } else if (scopes.size() == 1 && functions.containsKey(decl.name)) {
                error(decl, "Ім'я '" + decl.name + "' вже використано для функції");
            }
            scope.put(decl.name, new Variable(decl.name, type));
        }
    }

    private void initList(InitList list, Type type, Declarator decl, int depth) {
        int size = decl.inferredSizes[depth];
        if (decl.dims.get(depth) != null && size > 0 && list.items.size() > size) {
            error(list, "Забагато ініціалізаторів для масиву '" + decl.name + "' (розмір " + size + ")");
        }
        for (Node item : list.items) {
            if (type.rank > 1) {
                if (item instanceof InitList inner) {
                    initList(inner, type.element(), decl, depth + 1);
                } else {
                    error(item, "Для багатовимірного масиву очікується вкладений список { ... }");
                }
            } else if (item instanceof InitList) {
                error(item, "Зайвий рівень вкладеності в ініціалізаторі масиву '" + decl.name + "'");
            } else {
                assignable(type.element(), expression((Expr) item), item);
            }
        }
    }

    private void statement(Node node) {
        switch (node) {
            case VarDecl d -> declaration(d);
            case Block b -> {
                scopes.push(new HashMap<>());
                for (Node item : b.items) {
                    statement(item);
                }
                scopes.pop();
            }
            case ExprStmt s -> expression(s.expr);
            case If s -> {
                condition(s.cond);
                statement(s.then);
                if (s.otherwise != null) {
                    statement(s.otherwise);
                }
            }
            case While s -> {
                condition(s.cond);
                loop(s.body);
            }
            case DoWhile s -> {
                loop(s.body);
                condition(s.cond);
            }
            case For s -> {
                scopes.push(new HashMap<>());
                if (s.init != null) {
                    statement(s.init);
                }
                if (s.cond != null) {
                    condition(s.cond);
                }
                if (s.step != null) {
                    expression(s.step);
                }
                loop(s.body);
                scopes.pop();
            }
            case Return r -> returnStatement(r);
            case Break b -> {
                if (loops == 0) {
                    error(b, "break поза циклом");
                }
            }
            case Continue c -> {
                if (loops == 0) {
                    error(c, "continue поза циклом");
                }
            }
            default -> {
            }
        }
    }

    private void loop(Node body) {
        loops++;
        statement(body);
        loops--;
    }

    private void returnStatement(Return r) {
        Type expected = current.returnType;
        if (r.value == null) {
            if (!expected.isVoid()) {
                warning(r, "return без значення у функції, що повертає " + expected);
            }
            return;
        }
        Type actual = expression(r.value);
        if (expected.isVoid()) {
            error(r, "Функція '" + current.name + "' має тип void і не може повертати значення");
        } else if (!actual.isNumeric()) {
            error(r, "Не можна повернути значення типу " + actual + " з функції типу " + expected);
        }
    }

    private void condition(Expr cond) {
        Type t = expression(cond);
        if (!t.isNumeric()) {
            error(cond, "Умова має бути числового типу, а не " + t);
        }
    }

    private void assignable(Type target, Type value, Node at) {
        if (target.isArray()) {
            error(at, "Масиви не можна присвоювати цілком");
        } else if (!value.isNumeric()) {
            error(at, "Не можна присвоїти значення типу " + value + " змінній типу " + target);
        }
    }

    private void lvalue(Expr e) {
        boolean ok = (e instanceof Ident || e instanceof Index) && e.type != null && !e.type.isArray()
                && e.type.isNumeric();
        if (!ok) {
            error(e, "Ліворуч від присвоєння має бути змінна або елемент масиву");
        }
    }

    private Type expression(Expr e) {
        Type t = infer(e);
        e.type = t;
        return t;
    }

    private Type infer(Expr e) {
        switch (e) {
            case IntLit lit -> {
                if (lit.value > Integer.MAX_VALUE) {
                    warning(lit, "Число " + lit.value + " не вміщується в int і буде обрізане");
                }
                return Type.INT;
            }
            case FloatLit lit -> {
                return Type.DOUBLE;
            }
            case CharLit lit -> {
                return Type.CHAR;
            }
            case StringLit lit -> {
                error(lit, "Рядкові літерали підтримуються лише як аргументи printf і puts");
                return Type.STRING;
            }
            case Ident id -> {
                Variable v = lookup(id.name);
                if (v != null) {
                    return v.type;
                }
                if (functions.containsKey(id.name) || BUILTINS.containsKey(id.name)) {
                    error(id, "Функцію '" + id.name + "' використано як значення (немає дужок виклику)");
                } else {
                    error(id, "Змінну '" + id.name + "' не оголошено");
                }
                return Type.INT;
            }
            case Unary u -> {
                Type t = expression(u.operand);
                switch (u.op) {
                    case "-", "+" -> {
                        numeric(u, t, u.op);
                        return t.isNumeric() ? Type.promote(t) : Type.INT;
                    }
                    case "!" -> {
                        numeric(u, t, u.op);
                        return Type.INT;
                    }
                    case "~" -> {
                        integral(u, t, u.op);
                        return Type.INT;
                    }
                    default -> {
                        lvalue(u.operand);
                        return t.isNumeric() ? t : Type.INT;
                    }
                }
            }
            case Postfix p -> {
                Type t = expression(p.operand);
                lvalue(p.operand);
                return t.isNumeric() ? t : Type.INT;
            }
            case Binary b -> {
                return binary(b);
            }
            case Assign a -> {
                return assign(a);
            }
            case Conditional c -> {
                condition(c.cond);
                Type a = expression(c.then);
                Type b = expression(c.otherwise);
                if (!a.isNumeric() || !b.isNumeric()) {
                    error(c, "Гілки умовного оператора ?: мають бути числовими");
                    return Type.INT;
                }
                return Type.arithmetic(Type.promote(a), Type.promote(b));
            }
            case Call call -> {
                return call(call);
            }
            case Index ix -> {
                Type array = expression(ix.array);
                Type index = expression(ix.index);
                if (!index.isIntegral()) {
                    error(ix.index, "Індекс масиву має бути цілим числом");
                }
                if (!array.isArray()) {
                    error(ix, "Індексувати можна лише масив, а не " + array);
                    return Type.INT;
                }
                return array.element();
            }
            case Cast c -> {
                Type t = expression(c.expr);
                if (!t.isNumeric()) {
                    error(c, "Не можна привести значення типу " + t + " до " + c.target);
                }
                return c.target;
            }
            default -> {
                return Type.INT;
            }
        }
    }

    private void numeric(Expr at, Type t, String op) {
        if (!t.isNumeric()) {
            error(at, "Операнд '" + op + "' має бути числом, а не " + t);
        }
    }

    private void integral(Expr at, Type t, String op) {
        if (!t.isIntegral()) {
            error(at, "Операнд '" + op + "' має бути цілим числом, а не " + t);
        }
    }

    private Type binary(Binary b) {
        Type l = expression(b.left);
        Type r = expression(b.right);
        switch (b.op) {
            case "+", "-", "*", "/" -> {
                if (!l.isNumeric() || !r.isNumeric()) {
                    error(b, "Операнди '" + b.op + "' мають бути числами (отримано " + l + " і " + r + ")");
                    b.operandType = Type.INT;
                    return Type.INT;
                }
                b.operandType = Type.arithmetic(l, r);
                return b.operandType;
            }
            case "%", "<<", ">>", "&", "|", "^" -> {
                if (!l.isIntegral() || !r.isIntegral()) {
                    error(b, "Операнди '" + b.op + "' мають бути цілими (отримано " + l + " і " + r + ")");
                }
                b.operandType = Type.INT;
                return Type.INT;
            }
            case "&&", "||" -> {
                if (!l.isNumeric() || !r.isNumeric()) {
                    error(b, "Операнди '" + b.op + "' мають бути числами");
                }
                b.operandType = Type.INT;
                return Type.INT;
            }
            default -> {
                if (!l.isNumeric() || !r.isNumeric()) {
                    error(b, "Порівнювати можна лише числа (отримано " + l + " і " + r + ")");
                    b.operandType = Type.INT;
                } else {
                    b.operandType = Type.arithmetic(l, r);
                }
                return Type.INT;
            }
        }
    }

    private Type assign(Assign a) {
        Type target = expression(a.target);
        Type value = expression(a.value);
        lvalue(a.target);
        if (a.op.equals("=")) {
            assignable(target, value, a.value);
            a.operandType = target;
            return target;
        }
        String op = a.arithmeticOp();
        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
            if (!value.isNumeric()) {
                error(a, "Операнд '" + a.op + "' має бути числом");
                a.operandType = Type.INT;
            } else {
                a.operandType = target.isNumeric() ? Type.arithmetic(target, value) : Type.INT;
            }
        } else {
            if (!target.isIntegral() || !value.isIntegral()) {
                error(a, "Операнди '" + a.op + "' мають бути цілими");
            }
            a.operandType = Type.INT;
        }
        return target;
    }

    private Type call(Call call) {
        FunctionInfo f = BUILTINS.get(call.name);
        if (f == null) {
            f = functions.get(call.name);
        }
        if (f == null) {
            if (lookup(call.name) != null) {
                error(call, "'" + call.name + "' є змінною, а не функцією");
            } else {
                error(call, "Функцію '" + call.name + "' не оголошено");
            }
            for (Expr arg : call.args) {
                expression(arg);
            }
            return Type.INT;
        }
        if (!f.builtin) {
            called.add(f.name);
            firstCall.putIfAbsent(f.name, call);
        }
        int expected = f.params.size();
        int actual = call.args.size();
        if ((!f.variadic && actual != expected) || (f.variadic && actual < expected)) {
            error(call, "Функція '" + f.name + "' очікує " + (f.variadic ? "щонайменше " : "") + expected
                    + " аргумент(ів), передано " + actual);
        }
        for (int i = 0; i < actual; i++) {
            Expr arg = call.args.get(i);
            Type param = i < expected ? f.params.get(i) : null;
            boolean stringAllowed = f.builtin && (Type.STRING.equals(param) || (f.variadic && param == null));
            Type t = stringAllowed && stringArgument(arg) ? Type.STRING : expression(arg);
            arg.type = t;
            if (param == null) {
                if (!t.isNumeric() && !t.equals(Type.STRING)) {
                    error(arg, "Аргумент " + (i + 1) + " функції '" + f.name + "' має недопустимий тип " + t);
                }
            } else if (param.isArray()) {
                if (!param.equals(t)) {
                    error(arg, "Аргумент " + (i + 1) + " функції '" + f.name + "': очікується " + param
                            + ", передано " + t);
                }
            } else if (param.equals(Type.STRING)) {
                if (!t.equals(Type.STRING)) {
                    error(arg, "Аргумент " + (i + 1) + " функції '" + f.name + "' має бути рядковим літералом");
                }
            } else if (!t.isNumeric()) {
                error(arg, "Аргумент " + (i + 1) + " функції '" + f.name + "': очікується " + param
                        + ", передано " + t);
            }
        }
        if (f.name.equals("printf") && !call.args.isEmpty() && call.args.get(0) instanceof StringLit fmt) {
            printfFormat(call, fmt.value);
        }
        return f.returnType;
    }

    private boolean stringArgument(Expr e) {
        if (e instanceof StringLit) {
            e.type = Type.STRING;
            return true;
        }
        if (e instanceof Conditional c && isStringTree(c.then) && isStringTree(c.otherwise)) {
            condition(c.cond);
            stringArgument(c.then);
            stringArgument(c.otherwise);
            c.type = Type.STRING;
            return true;
        }
        return false;
    }

    private static boolean isStringTree(Expr e) {
        return e instanceof StringLit
                || (e instanceof Conditional c && isStringTree(c.then) && isStringTree(c.otherwise));
    }

    private void printfFormat(Call call, String format) {
        List<Expr> args = call.args.subList(1, call.args.size());
        int used = 0;
        for (int i = 0; i < format.length(); i++) {
            if (format.charAt(i) != '%') {
                continue;
            }
            i++;
            while (i < format.length() && "-+ 0#".indexOf(format.charAt(i)) >= 0) {
                i++;
            }
            while (i < format.length() && (Character.isDigit(format.charAt(i)) || format.charAt(i) == '*'
                    || format.charAt(i) == '.')) {
                if (format.charAt(i) == '*') {
                    used++;
                }
                i++;
            }
            while (i < format.length() && "hlLqjzt".indexOf(format.charAt(i)) >= 0) {
                i++;
            }
            if (i >= format.length()) {
                warning(call, "Незавершена специфікація формату в printf");
                return;
            }
            char conv = format.charAt(i);
            if (conv == '%') {
                continue;
            }
            if (used >= args.size()) {
                warning(call, "printf: для %" + conv + " не вистачає аргументу");
                used++;
                continue;
            }
            Type t = args.get(used).type;
            used++;
            if (t == null) {
                continue;
            }
            if ("dioxXuc".indexOf(conv) >= 0 && t.isFloating()) {
                warning(args.get(used - 1), "printf: %" + conv + " очікує ціле число, а передано " + t);
            } else if ("fFeEgG".indexOf(conv) >= 0 && t.isIntegral()) {
                warning(args.get(used - 1), "printf: %" + conv + " очікує дійсне число, а передано " + t);
            } else if (conv == 's' && !t.equals(Type.STRING)) {
                warning(args.get(used - 1), "printf: %s очікує рядок, а передано " + t);
            }
        }
        if (used < args.size()) {
            warning(call, "printf: зайві аргументи (використано " + used + " з " + args.size() + ")");
        }
    }

    private Variable lookup(String name) {
        for (Map<String, Variable> scope : scopes) {
            Variable v = scope.get(name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
