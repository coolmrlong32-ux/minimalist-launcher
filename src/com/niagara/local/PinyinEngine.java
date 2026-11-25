package com.niagara.local;

import android.icu.text.Transliterator;
import android.os.Build;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 100% 纯本地离线拼音与检索索引引擎：
 * - 利用 Android 原生 ICU Transliterator (Han-Latin; Latin-ASCII) 结合常见多音字应用名修正表，
 * - 同时生成：全拼 (full)、首字母 (initials)、T9 数字序列 (t9) 以及分组首字母 (A-Z/#)。
 */
final class PinyinEngine {
    private static Transliterator hanToLatin;
    private static boolean initTried = false;

    // 常见多音字修正（针对应用名称高频字）
    private static final Map<Character, String> POLYPHONE = new HashMap<>();
    static {
        POLYPHONE.put('行', "hang");
        POLYPHONE.put('重', "chong");
        POLYPHONE.put('乐', "yue");
        POLYPHONE.put('淘', "tao");
        POLYPHONE.put('微', "wei");
        POLYPHONE.put('信', "xin");
        POLYPHONE.put('支', "zhi");
        POLYPHONE.put('付', "fu");
        POLYPHONE.put('宝', "bao");
        POLYPHONE.put('抖', "dou");
        POLYPHONE.put('音', "yin");
        POLYPHONE.put('快', "kuai");
        POLYPHONE.put('手', "shou");
        POLYPHONE.put('美', "mei");
        POLYPHONE.put('团', "tuan");
        POLYPHONE.put('京', "jing");
        POLYPHONE.put('东', "dong");
        POLYPHONE.put('拼', "pin");
        POLYPHONE.put('多', "duo");
        POLYPHONE.put('网', "wang");
        POLYPHONE.put('易', "yi");
        POLYPHONE.put('云', "yun");
        POLYPHONE.put('高', "gao");
        POLYPHONE.put('德', "de");
        POLYPHONE.put('地', "di");
        POLYPHONE.put('图', "tu");
        POLYPHONE.put('哔', "bi");
        POLYPHONE.put('哩', "li");
        POLYPHONE.put('小', "xiao");
        POLYPHONE.put('红', "hong");
        POLYPHONE.put('书', "shu");
        POLYPHONE.put('知', "zhi");
        POLYPHONE.put('乎', "hu");
        POLYPHONE.put('设', "she");
        POLYPHONE.put('置', "zhi");
        POLYPHONE.put('相', "xiang");
        POLYPHONE.put('机', "ji");
        POLYPHONE.put('册', "ce");
        POLYPHONE.put('日', "ri");
        POLYPHONE.put('历', "li");
        POLYPHONE.put('时', "shi");
        POLYPHONE.put('钟', "zhong");
        POLYPHONE.put('计', "ji");
        POLYPHONE.put('算', "suan");
        POLYPHONE.put('器', "qi");
        POLYPHONE.put('文', "wen");
        POLYPHONE.put('件', "jian");
        POLYPHONE.put('管', "guan");
        POLYPHONE.put('理', "li");
        POLYPHONE.put('电', "dian");
        POLYPHONE.put('话', "hua");
        POLYPHONE.put('短', "duan");
        POLYPHONE.put('浏', "liu");
        POLYPHONE.put('览', "lan");
    }

    private static synchronized Transliterator getTransliterator() {
        if (!initTried) {
            initTried = true;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    hanToLatin = Transliterator.getInstance("Han-Latin; NFD; [[:Nonspacing Mark:]] Remove; Lower");
                } catch (Throwable ignored) {}
            }
        }
        return hanToLatin;
    }

    /** 将单个汉字转为无声调小写拼音；非汉字返回 null。 */
    static String charToPinyin(char ch) {
        if (ch < 0x4E00 || ch > 0x9FA5) return null;
        String fixed = POLYPHONE.get(ch);
        if (fixed != null) return fixed;
        Transliterator tr = getTransliterator();
        if (tr != null && Build.VERSION.SDK_INT >= 29) {
            try {
                String res = tr.transliterate(String.valueOf(ch)).trim().toLowerCase(Locale.ROOT);
                StringBuilder clean = new StringBuilder();
                for (int i = 0; i < res.length(); i++) {
                    char c = res.charAt(i);
                    if (c >= 'a' && c <= 'z') clean.append(c);
                }
                if (clean.length() > 0) return clean.toString();
            } catch (Throwable ignored) {}
        }
        return gb2312Fallback(ch);
    }

    /** GB2312 常见一级汉字音序兜底。 */
    private static String gb2312Fallback(char ch) {
        try {
            byte[] bytes = String.valueOf(ch).getBytes("GBK");
            if (bytes.length < 2) return null;
            int secPosValue = (bytes[0] & 0xFF) - 160;
            int posValue = (bytes[1] & 0xFF) - 160;
            int code = secPosValue * 100 + posValue;
            int[] secPosValueList = {
                1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787,
                3106, 3212, 3472, 3635, 3722, 3730, 3858, 4027, 4086,
                4390, 4558, 4684, 4925, 5249, 5600
            };
            String[] firstLetter = {
                "a", "b", "c", "d", "e", "f", "g", "h", "j",
                "k", "l", "m", "n", "o", "p", "q", "r", "s",
                "t", "w", "x", "y", "z"
            };
            for (int i = 0; i < 23; i++) {
                if (code >= secPosValueList[i] && code < secPosValueList[i + 1]) {
                    return firstLetter[i];
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 生成检索键数组：{fullPinyin, initials, t9Digits}
     */
    static String[] buildSearchKeys(String label) {
        if (TextUtils.isEmpty(label)) return new String[]{"", "", ""};
        String norm = label;
        // 针对常见含多音字词组做精确替换
        if (norm.contains("银行")) norm = norm.replace("银行", "yinhang");
        if (norm.contains("音乐")) norm = norm.replace("音乐", "yinyue");
        if (norm.contains("重庆")) norm = norm.replace("重庆", "chongqing");

        StringBuilder full = new StringBuilder();
        StringBuilder ini = new StringBuilder();
        boolean wordStart = true;

        for (int i = 0; i < norm.length(); i++) {
            char ch = norm.charAt(i);
            String py = charToPinyin(ch);
            if (py != null && !py.isEmpty()) {
                full.append(py);
                ini.append(py.charAt(0));
                wordStart = true;
            } else if (Character.isLetterOrDigit(ch)) {
                char lower = Character.toLowerCase(ch);
                full.append(lower);
                if (wordStart || Character.isUpperCase(ch)) {
                    ini.append(lower);
                }
                wordStart = false;
            } else {
                wordStart = true;
            }
        }
        String fullStr = full.toString();
        String iniStr = ini.toString();
        return new String[]{fullStr, iniStr, toT9(iniStr)};
    }

    /** 获取应用分组首字母 ('A'-'Z' 或 '#')。 */
    static char groupLetter(String label) {
        if (TextUtils.isEmpty(label)) return '#';
        String trimmed = label.trim();
        if (trimmed.isEmpty()) return '#';
        char ch = trimmed.charAt(0);
        String py = charToPinyin(ch);
        if (py != null && !py.isEmpty()) {
            char c = Character.toUpperCase(py.charAt(0));
            if (c >= 'A' && c <= 'Z') return c;
        }
        char up = Character.toUpperCase(ch);
        if (up >= 'A' && up <= 'Z') return up;
        return '#';
    }

    private static String toT9(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'a': case 'b': case 'c': sb.append('2'); break;
                case 'd': case 'e': case 'f': sb.append('3'); break;
                case 'g': case 'h': case 'i': sb.append('4'); break;
                case 'j': case 'k': case 'l': sb.append('5'); break;
                case 'm': case 'n': case 'o': sb.append('6'); break;
                case 'p': case 'q': case 'r': case 's': sb.append('7'); break;
                case 't': case 'u': case 'v': sb.append('8'); break;
                case 'w': case 'x': case 'y': case 'z': sb.append('9'); break;
                default:
                    if (Character.isDigit(c)) sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }
}
