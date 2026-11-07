package cparser.ast;

import java.util.Objects;

public final class Type {
    public enum Base { VOID, CHAR, INT, FLOAT, DOUBLE, STRING }

    public static final Type VOID = new Type(Base.VOID, 0);
    public static final Type CHAR = new Type(Base.CHAR, 0);
    public static final Type INT = new Type(Base.INT, 0);
    public static final Type FLOAT = new Type(Base.FLOAT, 0);
    public static final Type DOUBLE = new Type(Base.DOUBLE, 0);
    public static final Type STRING = new Type(Base.STRING, 0);

    public final Base base;
    public final int rank;

    private Type(Base base, int rank) {
        this.base = base;
        this.rank = rank;
    }

    public Type withRank(int newRank) {
        return newRank == rank ? this : new Type(base, newRank);
    }

    public Type element() {
        return new Type(base, rank - 1);
    }

    public Type scalar() {
        return withRank(0);
    }

    public boolean isArray() {
        return rank > 0;
    }

    public boolean isNumeric() {
        return rank == 0 && (base == Base.CHAR || base == Base.INT || base == Base.FLOAT || base == Base.DOUBLE);
    }

    public boolean isIntegral() {
        return rank == 0 && (base == Base.CHAR || base == Base.INT);
    }

    public boolean isFloating() {
        return rank == 0 && (base == Base.FLOAT || base == Base.DOUBLE);
    }

    public boolean isVoid() {
        return rank == 0 && base == Base.VOID;
    }

    public static Type arithmetic(Type a, Type b) {
        if (a.base == Base.DOUBLE || b.base == Base.DOUBLE) {
            return DOUBLE;
        }
        if (a.base == Base.FLOAT || b.base == Base.FLOAT) {
            return FLOAT;
        }
        return INT;
    }

    public static Type promote(Type t) {
        return t.base == Base.CHAR ? INT : t;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Type t && t.base == base && t.rank == rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, rank);
    }

    @Override
    public String toString() {
        String name = base == Base.STRING ? "string" : base.name().toLowerCase();
        return name + "[]".repeat(rank);
    }
}
