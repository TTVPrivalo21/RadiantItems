package dev.privalo.items.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tiny arithmetic evaluator used by actions/conditions after placeholders are replaced:
 * {@code + - * / % ^ ( )}, numbers and min/max/abs/floor/ceil/round/sqrt/pow/random.
 */
public final class Expr {

    private static final String[] OPERATORS = {">=", "<=", "!=", "==", ">", "<", "="};

    private Expr() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    public static double eval(String input) {
        Parser parser = new Parser(input);
        double value = parser.expression();
        parser.skipSpaces();
        if (parser.pos < input.length()) {
            throw new IllegalArgumentException("Carácter inesperado '" + input.charAt(parser.pos) + "' en \"" + input + "\"");
        }
        return value;
    }

    public static double num(String input, double fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return eval(input.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static boolean isNumeric(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        try {
            eval(input.trim());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 5.0 → "5", 2.345 → "2.35". */
    public static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        String s = String.format(Locale.ROOT, "%.2f", value);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Evaluates "left OP right" (OP: &gt;= &lt;= != == &gt; &lt; =). Numeric when both sides are numeric,
     * otherwise a case-insensitive text comparison (only == / != / = make sense there).
     */
    public static boolean compare(String expression) {
        int bestIndex = -1;
        String bestOp = null;
        for (String op : OPERATORS) {
            int idx = expression.indexOf(op);
            if (idx >= 0 && (bestIndex < 0 || idx < bestIndex || (idx == bestIndex && op.length() > bestOp.length()))) {
                bestIndex = idx;
                bestOp = op;
            }
        }
        if (bestOp == null) {
            String v = expression.trim();
            return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || (isNumeric(v) && eval(v) != 0);
        }
        String left = expression.substring(0, bestIndex).trim();
        String right = expression.substring(bestIndex + bestOp.length()).trim();
        if (isNumeric(left) && isNumeric(right)) {
            double l = eval(left);
            double r = eval(right);
            return switch (bestOp) {
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                case ">" -> l > r;
                case "<" -> l < r;
                case "!=" -> Math.abs(l - r) > 1e-9;
                default -> Math.abs(l - r) <= 1e-9;
            };
        }
        int c = left.compareToIgnoreCase(right);
        return switch (bestOp) {
            case "!=" -> c != 0;
            case "==", "=" -> c == 0;
            case ">=" -> c >= 0;
            case "<=" -> c <= 0;
            case ">" -> c > 0;
            default -> c < 0;
        };
    }

    /** Throws when the expression has no comparison operator (used by validators). */
    public static void requireComparison(String expression) {
        for (String op : OPERATORS) {
            if (expression.contains(op)) {
                return;
            }
        }
        throw new IllegalArgumentException("Falta un operador de comparación (>=, <=, ==, !=, >, <) en \"" + expression + "\"");
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        boolean eat(char c) {
            skipSpaces();
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        double expression() {
            double v = term();
            while (true) {
                if (eat('+')) {
                    v += term();
                } else if (eat('-')) {
                    v -= term();
                } else {
                    return v;
                }
            }
        }

        double term() {
            double v = power();
            while (true) {
                if (eat('*')) {
                    v *= power();
                } else if (eat('/')) {
                    double d = power();
                    v = d == 0 ? 0 : v / d;
                } else if (eat('%')) {
                    double d = power();
                    v = d == 0 ? 0 : v % d;
                } else {
                    return v;
                }
            }
        }

        double power() {
            double base = unary();
            if (eat('^')) {
                return Math.pow(base, power());
            }
            return base;
        }

        double unary() {
            if (eat('-')) {
                return -unary();
            }
            if (eat('+')) {
                return unary();
            }
            return primary();
        }

        double primary() {
            skipSpaces();
            if (eat('(')) {
                double v = expression();
                if (!eat(')')) {
                    throw new IllegalArgumentException("Falta ')' en \"" + s + "\"");
                }
                return v;
            }
            int start = pos;
            if (pos < s.length() && Character.isLetter(s.charAt(pos))) {
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) {
                    pos++;
                }
                String name = s.substring(start, pos).toLowerCase(Locale.ROOT);
                if (!eat('(')) {
                    throw new IllegalArgumentException("Se esperaba '(' tras " + name);
                }
                List<Double> args = new ArrayList<>();
                if (!eat(')')) {
                    do {
                        args.add(expression());
                    } while (eat(','));
                    if (!eat(')')) {
                        throw new IllegalArgumentException("Falta ')' en " + name);
                    }
                }
                return function(name, args);
            }
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("Número esperado en \"" + s + "\"");
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private double function(String name, List<Double> a) {
            return switch (name) {
                case "min" -> a.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case "max" -> a.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                case "abs" -> Math.abs(arg(a, 0));
                case "floor" -> Math.floor(arg(a, 0));
                case "ceil" -> Math.ceil(arg(a, 0));
                case "round" -> Math.round(arg(a, 0));
                case "sqrt" -> Math.sqrt(Math.max(0, arg(a, 0)));
                case "pow" -> Math.pow(arg(a, 0), arg(a, 1));
                case "random" -> {
                    double lo = a.size() >= 2 ? a.get(0) : 0;
                    double hi = a.size() >= 2 ? a.get(1) : (a.isEmpty() ? 1 : a.get(0));
                    yield hi <= lo ? lo : ThreadLocalRandom.current().nextDouble(lo, hi);
                }
                default -> throw new IllegalArgumentException("Función desconocida: " + name);
            };
        }

        private static double arg(List<Double> a, int i) {
            if (i >= a.size()) {
                throw new IllegalArgumentException("Faltan argumentos");
            }
            return a.get(i);
        }
    }
}
