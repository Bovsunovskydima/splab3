package cparser.runtime;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public final class CFormat {
    private CFormat() {
    }

    public static String format(String fmt, List<Object> args) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        int i = 0;
        while (i < fmt.length()) {
            char c = fmt.charAt(i++);
            if (c != '%') {
                out.append(c);
                continue;
            }
            StringBuilder flags = new StringBuilder();
            while (i < fmt.length() && "-+ 0#".indexOf(fmt.charAt(i)) >= 0) {
                flags.append(fmt.charAt(i++));
            }
            int width = 0;
            if (i < fmt.length() && fmt.charAt(i) == '*') {
                width = toInt(arg(args, next++));
                i++;
                if (width < 0) {
                    flags.append('-');
                    width = -width;
                }
            } else {
                while (i < fmt.length() && Character.isDigit(fmt.charAt(i))) {
                    width = width * 10 + (fmt.charAt(i++) - '0');
                }
            }
            int precision = -1;
            if (i < fmt.length() && fmt.charAt(i) == '.') {
                i++;
                precision = 0;
                if (i < fmt.length() && fmt.charAt(i) == '*') {
                    precision = toInt(arg(args, next++));
                    i++;
                } else {
                    while (i < fmt.length() && Character.isDigit(fmt.charAt(i))) {
                        precision = precision * 10 + (fmt.charAt(i++) - '0');
                    }
                }
            }
            while (i < fmt.length() && "hlLqjzt".indexOf(fmt.charAt(i)) >= 0) {
                i++;
            }
            if (i >= fmt.length()) {
                out.append('%').append(flags);
                break;
            }
            char conv = fmt.charAt(i++);
            String f = flags.toString();
            if (conv == '%') {
                out.append('%');
                continue;
            }
            String sign = "";
            String prefix = "";
            String body;
            boolean numeric = true;
            boolean integer = false;
            switch (conv) {
                case 'd', 'i' -> {
                    long v = toInt(arg(args, next++));
                    sign = v < 0 ? "-" : f.contains("+") ? "+" : f.contains(" ") ? " " : "";
                    body = digits(Long.toString(Math.abs(v)), precision, v == 0);
                    integer = true;
                }
                case 'u', 'o', 'x', 'X' -> {
                    long v = Integer.toUnsignedLong(toInt(arg(args, next++)));
                    int radix = conv == 'u' ? 10 : conv == 'o' ? 8 : 16;
                    body = digits(Long.toString(v, radix), precision, v == 0);
                    if (conv == 'X') {
                        body = body.toUpperCase();
                    }
                    if (f.contains("#") && v != 0) {
                        prefix = conv == 'o' ? (body.startsWith("0") ? "" : "0") : conv == 'x' ? "0x" : conv == 'X' ? "0X" : "";
                    }
                    integer = true;
                }
                case 'f', 'F', 'e', 'E', 'g', 'G' -> {
                    double v = toDouble(arg(args, next++));
                    boolean negative = v < 0 || (v == 0 && 1 / v < 0);
                    sign = negative ? "-" : f.contains("+") ? "+" : f.contains(" ") ? " " : "";
                    double a = Math.abs(v);
                    if (Double.isNaN(v)) {
                        body = "nan";
                        numeric = false;
                    } else if (Double.isInfinite(v)) {
                        body = "inf";
                        numeric = false;
                    } else {
                        int p = precision < 0 ? 6 : precision;
                        boolean alt = f.contains("#");
                        body = switch (Character.toLowerCase(conv)) {
                            case 'f' -> fixed(a, p, alt);
                            case 'e' -> exponent(a, p, alt);
                            default -> general(a, p, alt);
                        };
                    }
                    if (Character.isUpperCase(conv)) {
                        body = body.toUpperCase();
                    }
                }
                case 'c' -> {
                    body = String.valueOf((char) (toInt(arg(args, next++)) & 0xFF));
                    numeric = false;
                }
                case 's' -> {
                    body = String.valueOf(arg(args, next++));
                    if (precision >= 0 && precision < body.length()) {
                        body = body.substring(0, precision);
                    }
                    numeric = false;
                }
                default -> {
                    out.append('%').append(conv);
                    continue;
                }
            }
            String text = sign + prefix + body;
            if (text.length() < width) {
                String pad;
                if (f.contains("-")) {
                    out.append(text).append(" ".repeat(width - text.length()));
                    continue;
                }
                if (f.contains("0") && numeric && !(integer && precision >= 0)) {
                    pad = "0".repeat(width - text.length());
                    text = sign + prefix + pad + body;
                } else {
                    text = " ".repeat(width - text.length()) + text;
                }
            }
            out.append(text);
        }
        return out.toString();
    }

    private static Object arg(List<Object> args, int index) {
        return index < args.size() ? args.get(index) : 0;
    }

    private static int toInt(Object v) {
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Double d) {
            return (int) d.doubleValue();
        }
        return 0;
    }

    private static double toDouble(Object v) {
        if (v instanceof Double d) {
            return d;
        }
        if (v instanceof Integer i) {
            return i;
        }
        return 0;
    }

    private static String digits(String s, int precision, boolean zero) {
        if (precision == 0 && zero) {
            return "";
        }
        if (precision > s.length()) {
            return "0".repeat(precision - s.length()) + s;
        }
        return s;
    }

    static String fixed(double v, int precision, boolean alt) {
        String s = new BigDecimal(v).setScale(precision, RoundingMode.HALF_EVEN).toPlainString();
        return precision == 0 && alt ? s + "." : s;
    }

    static String exponent(double v, int precision, boolean alt) {
        int exp = 0;
        String digits;
        if (v == 0) {
            digits = "0".repeat(precision + 1);
        } else {
            BigDecimal bd = new BigDecimal(v).round(new MathContext(precision + 1, RoundingMode.HALF_EVEN));
            exp = bd.precision() - bd.scale() - 1;
            digits = bd.unscaledValue().toString();
            if (digits.length() < precision + 1) {
                digits = digits + "0".repeat(precision + 1 - digits.length());
            } else if (digits.length() > precision + 1) {
                digits = digits.substring(0, precision + 1);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(digits.charAt(0));
        if (precision > 0 || alt) {
            sb.append('.');
        }
        sb.append(digits, 1, digits.length());
        sb.append('e').append(exp < 0 ? '-' : '+');
        String e = Integer.toString(Math.abs(exp));
        sb.append(e.length() < 2 ? "0" + e : e);
        return sb.toString();
    }

    static String general(double v, int precision, boolean alt) {
        int p = precision == 0 ? 1 : precision;
        int x = 0;
        if (v != 0) {
            BigDecimal bd = new BigDecimal(v).round(new MathContext(p, RoundingMode.HALF_EVEN));
            x = bd.precision() - bd.scale() - 1;
        }
        String s;
        if (p > x && x >= -4) {
            s = fixed(v, p - 1 - x, alt);
        } else {
            s = exponent(v, p - 1, alt);
        }
        if (alt) {
            return s;
        }
        int e = s.indexOf('e');
        String mantissa = e >= 0 ? s.substring(0, e) : s;
        String tail = e >= 0 ? s.substring(e) : "";
        if (mantissa.contains(".")) {
            mantissa = mantissa.replaceAll("0+$", "");
            if (mantissa.endsWith(".")) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
        }
        return mantissa + tail;
    }
}
