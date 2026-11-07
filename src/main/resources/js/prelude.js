"use strict";
const __out = [];

function __toInt(x) {
    return Number.isFinite(x) ? Math.trunc(x) | 0 : 0;
}

function __toChar(x) {
    return (__toInt(x) << 24) >> 24;
}

function __idiv(a, b) {
    if (b === 0) throw new Error("Ділення на нуль");
    return (a / b) | 0;
}

function __imod(a, b) {
    if (b === 0) throw new Error("Ділення на нуль (остача)");
    return (a % b) | 0;
}

function __array(dims, k = 0) {
    const a = new Array(dims[k]);
    for (let i = 0; i < dims[k]; i++) a[i] = k + 1 < dims.length ? __array(dims, k + 1) : 0;
    return a;
}

function __arrayInit(dims, values) {
    const a = __array(dims);
    __fill(a, values);
    return a;
}

function __fill(a, values) {
    for (let i = 0; i < values.length; i++) {
        if (Array.isArray(values[i])) __fill(a[i], values[i]);
        else a[i] = values[i];
    }
}

function __puts(s) {
    __out.push(s + "\n");
    return 1;
}

function __putchar(c) {
    __out.push(String.fromCharCode(c & 255));
    return c;
}

function __digits(s, prec, zero) {
    if (prec === 0 && zero) return "";
    return prec > s.length ? "0".repeat(prec - s.length) + s : s;
}

function __fixed(v, p, alt) {
    const s = v.toFixed(p);
    return p === 0 && alt ? s + "." : s;
}

function __exp(v, p, alt) {
    let mant, e;
    if (v === 0) {
        mant = p > 0 ? "0." + "0".repeat(p) : "0";
        e = 0;
    } else {
        const parts = v.toExponential(p).split("e");
        mant = parts[0];
        e = parseInt(parts[1], 10);
    }
    if (alt && p === 0) mant += ".";
    const es = String(Math.abs(e));
    return mant + "e" + (e < 0 ? "-" : "+") + (es.length < 2 ? "0" + es : es);
}

function __general(v, prec, alt) {
    const p = prec === 0 ? 1 : prec;
    const x = v === 0 ? 0 : parseInt(v.toExponential(p - 1).split("e")[1], 10);
    let s = p > x && x >= -4 ? __fixed(v, p - 1 - x, alt) : __exp(v, p - 1, alt);
    if (alt) return s;
    const i = s.indexOf("e");
    let mant = i >= 0 ? s.slice(0, i) : s;
    const tail = i >= 0 ? s.slice(i) : "";
    if (mant.includes(".")) mant = mant.replace(/0+$/, "").replace(/\.$/, "");
    return mant + tail;
}

function __printf(fmt, ...args) {
    let out = "";
    let next = 0;
    let i = 0;
    const isDigit = (ch) => ch >= "0" && ch <= "9";
    const arg = () => (next < args.length ? args[next++] : 0);
    while (i < fmt.length) {
        const c = fmt[i++];
        if (c !== "%") {
            out += c;
            continue;
        }
        let flags = "";
        while (i < fmt.length && "-+ 0#".includes(fmt[i])) flags += fmt[i++];
        let width = 0;
        if (fmt[i] === "*") {
            width = __toInt(arg());
            i++;
            if (width < 0) {
                flags += "-";
                width = -width;
            }
        } else {
            while (i < fmt.length && isDigit(fmt[i])) width = width * 10 + (fmt.charCodeAt(i++) - 48);
        }
        let prec = -1;
        if (fmt[i] === ".") {
            i++;
            prec = 0;
            if (fmt[i] === "*") {
                prec = __toInt(arg());
                i++;
            } else {
                while (i < fmt.length && isDigit(fmt[i])) prec = prec * 10 + (fmt.charCodeAt(i++) - 48);
            }
        }
        while (i < fmt.length && "hlLqjzt".includes(fmt[i])) i++;
        if (i >= fmt.length) {
            out += "%" + flags;
            break;
        }
        const conv = fmt[i++];
        if (conv === "%") {
            out += "%";
            continue;
        }
        let sign = "";
        let prefix = "";
        let body;
        let numeric = true;
        let integer = false;
        switch (conv) {
            case "d":
            case "i": {
                const v = __toInt(arg());
                sign = v < 0 ? "-" : flags.includes("+") ? "+" : flags.includes(" ") ? " " : "";
                body = __digits(String(Math.abs(v)), prec, v === 0);
                integer = true;
                break;
            }
            case "u":
            case "o":
            case "x":
            case "X": {
                const v = __toInt(arg()) >>> 0;
                const radix = conv === "u" ? 10 : conv === "o" ? 8 : 16;
                body = __digits(v.toString(radix), prec, v === 0);
                if (conv === "X") body = body.toUpperCase();
                if (flags.includes("#") && v !== 0) {
                    prefix = conv === "o" ? (body.startsWith("0") ? "" : "0") : conv === "x" ? "0x" : conv === "X" ? "0X" : "";
                }
                integer = true;
                break;
            }
            case "f":
            case "F":
            case "e":
            case "E":
            case "g":
            case "G": {
                const v = Number(arg());
                const negative = v < 0 || Object.is(v, -0);
                sign = negative ? "-" : flags.includes("+") ? "+" : flags.includes(" ") ? " " : "";
                const a = Math.abs(v);
                if (Number.isNaN(v)) {
                    body = "nan";
                    numeric = false;
                } else if (!Number.isFinite(v)) {
                    body = "inf";
                    numeric = false;
                } else {
                    const p = prec < 0 ? 6 : prec;
                    const alt = flags.includes("#");
                    const lower = conv.toLowerCase();
                    body = lower === "f" ? __fixed(a, p, alt) : lower === "e" ? __exp(a, p, alt) : __general(a, p, alt);
                }
                if (conv === conv.toUpperCase()) body = body.toUpperCase();
                break;
            }
            case "c":
                body = String.fromCharCode(__toInt(arg()) & 255);
                numeric = false;
                break;
            case "s":
                body = String(arg());
                if (prec >= 0 && prec < body.length) body = body.slice(0, prec);
                numeric = false;
                break;
            default:
                out += "%" + conv;
                continue;
        }
        let text = sign + prefix + body;
        if (text.length < width) {
            if (flags.includes("-")) {
                text += " ".repeat(width - text.length);
            } else if (flags.includes("0") && numeric && !(integer && prec >= 0)) {
                text = sign + prefix + "0".repeat(width - text.length) + body;
            } else {
                text = " ".repeat(width - text.length) + text;
            }
        }
        out += text;
    }
    __out.push(out);
    return out.length;
}
