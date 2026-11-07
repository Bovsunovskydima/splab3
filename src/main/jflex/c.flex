package cparser.gen;

import java_cup.runtime.Symbol;
import cparser.LexerException;

%%

%class Lexer
%public
%unicode
%cupsym Sym
%cup
%line
%column

%{
    private final StringBuilder text = new StringBuilder();
    private int textLine;
    private int textColumn;

    private Symbol token(int type) {
        return new Symbol(type, yyline + 1, yycolumn + 1);
    }

    private Symbol token(int type, Object value) {
        return new Symbol(type, yyline + 1, yycolumn + 1, value);
    }

    private LexerException error(String message) {
        return new LexerException(message, yyline + 1, yycolumn + 1);
    }

    private static int escape(char c) {
        switch (c) {
            case 'n': return '\n';
            case 't': return '\t';
            case 'r': return '\r';
            case '0': return 0;
            case 'a': return 7;
            case 'b': return 8;
            case 'f': return 12;
            case 'v': return 11;
            case '\\': return '\\';
            case '\'': return '\'';
            case '"': return '"';
            case '?': return '?';
            default: return -1;
        }
    }

    private Symbol charLiteral(String lexeme) {
        String body = lexeme.substring(1, lexeme.length() - 1);
        int value;
        if (body.charAt(0) != '\\') {
            value = body.charAt(0);
        } else if (body.charAt(1) == 'x') {
            value = Integer.parseInt(body.substring(2), 16) & 0xFF;
        } else {
            value = escape(body.charAt(1));
            if (value < 0) {
                throw error("Невідома escape-послідовність " + body);
            }
        }
        return token(Sym.CHAR_LIT, (int) (byte) value);
    }

    private Symbol intLiteral(String lexeme, int radix, int skip) {
        String digits = lexeme.replaceAll("[uUlL]+$", "").substring(skip);
        try {
            return token(Sym.INT_LIT, Long.parseLong(digits.isEmpty() ? "0" : digits, radix));
        } catch (NumberFormatException e) {
            throw error("Занадто велике ціле число " + lexeme);
        }
    }
%}

%eofval{
    return token(Sym.EOF);
%eofval}

LineTerminator = \r | \n | \r\n
WhiteSpace     = {LineTerminator} | [ \t\f]
Identifier     = [A-Za-z_][A-Za-z0-9_]*
IntSuffix      = [uUlL]*
DecInteger     = (0 | [1-9][0-9]*) {IntSuffix}
HexInteger     = 0 [xX] [0-9a-fA-F]+ {IntSuffix}
OctInteger     = 0 [0-7]+ {IntSuffix}
Exponent       = [eE] [+-]? [0-9]+
FloatNumber    = ([0-9]+ "." [0-9]* {Exponent}? | "." [0-9]+ {Exponent}? | [0-9]+ {Exponent}) [fFlL]?
CharLiteral    = \' ([^\\\'\r\n] | \\ [ntr0abfv\\\'\"?] | \\x [0-9a-fA-F]+) \'

%state STRING

%%

<YYINITIAL> {
    "#" [^\r\n]*                 { }
    "//" [^\r\n]*                { }
    "/*" ~"*/"                   { }
    "/*"                         { throw error("Незакритий коментар /*"); }

    "int"                        { return token(Sym.INT); }
    "char"                       { return token(Sym.CHAR); }
    "float"                      { return token(Sym.FLOAT); }
    "double"                     { return token(Sym.DOUBLE); }
    "void"                       { return token(Sym.VOID); }
    "if"                         { return token(Sym.IF); }
    "else"                       { return token(Sym.ELSE); }
    "while"                      { return token(Sym.WHILE); }
    "do"                         { return token(Sym.DO); }
    "for"                        { return token(Sym.FOR); }
    "return"                     { return token(Sym.RETURN); }
    "break"                      { return token(Sym.BREAK); }
    "continue"                   { return token(Sym.CONTINUE); }

    {Identifier}                 { return token(Sym.IDENT, yytext()); }
    {FloatNumber}                { String s = yytext().replaceAll("[fFlL]$", ""); return token(Sym.FLOAT_LIT, Double.valueOf(s)); }
    {HexInteger}                 { return intLiteral(yytext(), 16, 2); }
    {OctInteger}                 { return intLiteral(yytext(), 8, 1); }
    {DecInteger}                 { return intLiteral(yytext(), 10, 0); }
    {CharLiteral}                { return charLiteral(yytext()); }
    \"                           { text.setLength(0); textLine = yyline + 1; textColumn = yycolumn + 1; yybegin(STRING); }

    "+="                         { return token(Sym.PLUS_ASSIGN); }
    "-="                         { return token(Sym.MINUS_ASSIGN); }
    "*="                         { return token(Sym.STAR_ASSIGN); }
    "/="                         { return token(Sym.SLASH_ASSIGN); }
    "%="                         { return token(Sym.PERCENT_ASSIGN); }
    "&="                         { return token(Sym.AND_ASSIGN); }
    "|="                         { return token(Sym.OR_ASSIGN); }
    "^="                         { return token(Sym.XOR_ASSIGN); }
    "<<="                        { return token(Sym.SHL_ASSIGN); }
    ">>="                        { return token(Sym.SHR_ASSIGN); }
    "++"                         { return token(Sym.INC); }
    "--"                         { return token(Sym.DEC); }
    "=="                         { return token(Sym.EQ); }
    "!="                         { return token(Sym.NE); }
    "<="                         { return token(Sym.LE); }
    ">="                         { return token(Sym.GE); }
    "&&"                         { return token(Sym.AND); }
    "||"                         { return token(Sym.OR); }
    "<<"                         { return token(Sym.SHL); }
    ">>"                         { return token(Sym.SHR); }
    "+"                          { return token(Sym.PLUS); }
    "-"                          { return token(Sym.MINUS); }
    "*"                          { return token(Sym.STAR); }
    "/"                          { return token(Sym.SLASH); }
    "%"                          { return token(Sym.PERCENT); }
    "="                          { return token(Sym.ASSIGN); }
    "<"                          { return token(Sym.LT); }
    ">"                          { return token(Sym.GT); }
    "!"                          { return token(Sym.NOT); }
    "&"                          { return token(Sym.BITAND); }
    "|"                          { return token(Sym.BITOR); }
    "^"                          { return token(Sym.BITXOR); }
    "~"                          { return token(Sym.BITNOT); }
    "?"                          { return token(Sym.QUESTION); }
    ":"                          { return token(Sym.COLON); }
    "("                          { return token(Sym.LPAREN); }
    ")"                          { return token(Sym.RPAREN); }
    "{"                          { return token(Sym.LBRACE); }
    "}"                          { return token(Sym.RBRACE); }
    "["                          { return token(Sym.LBRACKET); }
    "]"                          { return token(Sym.RBRACKET); }
    ";"                          { return token(Sym.SEMI); }
    ","                          { return token(Sym.COMMA); }

    {WhiteSpace}                 { }
    [^]                          { throw error("Невідомий символ '" + yytext() + "'"); }
}

<STRING> {
    \"                           { yybegin(YYINITIAL); return new Symbol(Sym.STRING_LIT, textLine, textColumn, text.toString()); }
    [^\r\n\"\\]+                 { text.append(yytext()); }
    \\x [0-9a-fA-F]+             { text.append((char) (Integer.parseInt(yytext().substring(2), 16) & 0xFF)); }
    \\ [^\r\n]                   { int c = escape(yytext().charAt(1)); if (c < 0) throw error("Невідома escape-послідовність " + yytext()); text.append((char) c); }
    {LineTerminator}             { throw error("Незакритий рядковий літерал"); }
    <<EOF>>                      { throw error("Незакритий рядковий літерал"); }
}
