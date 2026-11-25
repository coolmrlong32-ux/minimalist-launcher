package com.niagara.local;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索框内置即时科学计算器与常用单位换算器（纯本地零延迟）：
 * - 支持四则运算、括号、幂运算 (^)、百分比 (%)；
 * - 支持常见单位即时换算（如 100km, 70kg, 37c, 98f, 10in, 500g）。
 */
final class QuickCalcEngine {
    private final String s;
    private int p;

    private static final Pattern UNIT_PATTERN = Pattern.compile(
            "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*(km|mi|kg|lb|jin|c|f|cm|in|m|ft)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private QuickCalcEngine(String s) {
        this.s = s;
    }

    static String evaluateOrConvert(String raw) {
        if (raw == null || raw.trim().length() < 2) return null;
        String q = raw.trim();

        // 1. 先检查单位换算
        Matcher m = UNIT_PATTERN.matcher(q);
        if (m.matches()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String u = m.group(2).toLowerCase(Locale.ROOT);
                switch (u) {
                    case "km": return fmt(val * 0.621371) + " 英里 (mi)  ·  " + fmt(val * 1000) + " 米";
                    case "mi": return fmt(val * 1.60934) + " 公里 (km)";
                    case "kg": return fmt(val * 2.0) + " 斤  ·  " + fmt(val * 2.20462) + " 磅 (lb)";
                    case "jin": return fmt(val * 0.5) + " 公斤 (kg)  ·  " + fmt(val * 1.10231) + " 磅";
                    case "lb": return fmt(val * 0.453592) + " 公斤 (kg)  ·  " + fmt(val * 0.907185) + " 斤";
                    case "c": return fmt(val * 9.0 / 5.0 + 32.0) + " °F (华氏度)";
                    case "f": return fmt((val - 32.0) * 5.0 / 9.0) + " °C (摄氏度)";
                    case "cm": return fmt(val / 2.54) + " 英寸 (in)  ·  " + fmt(val / 100.0) + " 米";
                    case "in": return fmt(val * 2.54) + " 厘米 (cm)";
                    case "m": return fmt(val * 3.28084) + " 英尺 (ft)";
                    case "ft": return fmt(val * 0.3048) + " 米 (m)";
                }
            } catch (Throwable ignored) {}
        }

        // 2. 检查数学算式
        if (!looksLikeMath(q)) return null;
        try {
            String expr = q.replace(" ", "")
                    .replace('×', '*')
                    .replace('÷', '/')
                    .replace('x', '*')
                    .replace('X', '*')
                    .replace('（', '(')
                    .replace('）', ')');
            QuickCalcEngine calc = new QuickCalcEngine(expr);
            double v = calc.parseExpr();
            if (calc.p != calc.s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return fmt(v);
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean looksLikeMath(String q) {
        if (q.length() < 3) return false;
        boolean hasOp = false, hasDigit = false;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (Character.isDigit(c)) hasDigit = true;
            else if ("+-*/×÷xX%^".indexOf(c) >= 0) hasOp = true;
            else if ("().（） ".indexOf(c) < 0) return false;
        }
        return hasOp && hasDigit;
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e15) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.8f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private double parseExpr() {
        double v = parseTerm();
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == '+') { p++; v += parseTerm(); }
            else if (c == '-') { p++; v -= parseTerm(); }
            else break;
        }
        return v;
    }

    private double parseTerm() {
        double v = parsePow();
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == '*') { p++; v *= parsePow(); }
            else if (c == '/') { p++; v /= parsePow(); }
            else if (c == '%') { p++; v %= parsePow(); }
            else break;
        }
        return v;
    }

    private double parsePow() {
        double b = parseUnary();
        if (p < s.length() && s.charAt(p) == '^') {
            p++;
            return Math.pow(b, parsePow());
        }
        return b;
    }

    private double parseUnary() {
        if (p < s.length() && s.charAt(p) == '-') { p++; return -parseUnary(); }
        if (p < s.length() && s.charAt(p) == '+') { p++; return parseUnary(); }
        return parseAtom();
    }

    private double parseAtom() {
        if (p < s.length() && s.charAt(p) == '(') {
            p++;
            double v = parseExpr();
            if (p >= s.length() || s.charAt(p) != ')') throw new IllegalArgumentException();
            p++;
            return v;
        }
        int st = p;
        while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
        if (st == p) throw new IllegalArgumentException();
        return Double.parseDouble(s.substring(st, p));
    }
}
