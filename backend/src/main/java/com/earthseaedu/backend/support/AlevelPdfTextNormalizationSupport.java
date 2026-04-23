package com.earthseaedu.backend.support;

import cn.hutool.core.text.CharSequenceUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A-Level PDF 文本清洗工具，统一处理常见的 Unicode 变体、PDF 抽取噪音和 mojibake 字符。
 */
public final class AlevelPdfTextNormalizationSupport {

    private static final Map<String, String> STRING_REPLACEMENTS = buildStringReplacements();
    private static final Map<Character, String> CHARACTER_EXPANSIONS = buildCharacterExpansions();

    private AlevelPdfTextNormalizationSupport() {
    }

    /**
     * 归一化单行 PDF 文本，适合题干、选项、mark scheme 行级处理。
     */
    public static String normalizeLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String normalized = normalizeCharacters(rawLine)
            .replaceAll("(?<=\\S)\\s+\\?\\s+(?=\\S)", " • ")
            .replaceAll("^\\?\\s+", "• ")
            .replaceAll("\\s+", " ")
            .trim();
        return CharSequenceUtil.isBlank(normalized) ? null : normalized;
    }

    /**
     * 归一化包含换行的文本块，保留段落边界并压缩连续空行。
     */
    public static String normalizeTextBlock(String rawText) {
        if (rawText == null) {
            return null;
        }
        String[] rawLines = rawText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> normalizedLines = new ArrayList<>();
        boolean lastBlank = true;
        for (String rawLine : rawLines) {
            String normalizedLine = normalizeLine(rawLine);
            if (normalizedLine == null) {
                if (!lastBlank) {
                    normalizedLines.add("");
                }
                lastBlank = true;
                continue;
            }
            normalizedLines.add(normalizedLine);
            lastBlank = false;
        }
        while (!normalizedLines.isEmpty() && normalizedLines.get(normalizedLines.size() - 1).isEmpty()) {
            normalizedLines.remove(normalizedLines.size() - 1);
        }
        return normalizedLines.isEmpty() ? null : String.join("\n", normalizedLines);
    }

    /**
     * 归一化为搜索/规则匹配文本，统一大小写并把常见分隔符转换为空格。
     */
    public static String normalizeSearchText(String rawText) {
        String normalized = CharSequenceUtil.emptyIfNull(normalizeTextBlock(rawText)).toLowerCase(Locale.ROOT);
        return (" " + normalized
            .replace('-', ' ')
            .replace('/', ' ')
            .replace('_', ' ')
            .replace(',', ' ')
            .replace('.', ' ')
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('×', ' ')
            .replace('÷', ' ')
            .replace('Ω', ' ')
            .replace('μ', ' ')
            .replace('·', ' ')
            .replaceAll("\\s+", " ")
            .trim() + " ");
    }

    private static String normalizeCharacters(String rawText) {
        String normalized = rawText;
        for (Map.Entry<String, String> entry : STRING_REPLACEMENTS.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (isZeroWidth(current) || current == '\uFFFD') {
                continue;
            }
            String replacement = CHARACTER_EXPANSIONS.get(current);
            if (replacement != null) {
                builder.append(replacement);
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static boolean isZeroWidth(char current) {
        return current == '\u200B'
            || current == '\u200C'
            || current == '\u200D'
            || current == '\u2060'
            || current == '\uFEFF';
    }

    private static Map<String, String> buildStringReplacements() {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("\u00A0", " ");
        replacements.put("Â", " ");
        replacements.put("âˆ’", "-");
        replacements.put("â€“", "-");
        replacements.put("â€”", "-");
        replacements.put("â€˜", "'");
        replacements.put("â€™", "'");
        replacements.put("â€œ", "\"");
        replacements.put("â€�", "\"");
        replacements.put("â€¢", "•");
        replacements.put("â€˘", "•");
        replacements.put("âœ“", "•");
        replacements.put("âœ”", "•");
        replacements.put("âœ—", "✗");
        replacements.put("Ã—", "×");
        replacements.put("Ã·", "÷");
        replacements.put("Î©", "Ω");
        replacements.put("Î¼", "μ");
        replacements.put("Â°", "°");
        replacements.put("Â±", "±");
        replacements.put("Â·", "·");
        replacements.put("Âµ", "μ");
        return replacements;
    }

    private static Map<Character, String> buildCharacterExpansions() {
        Map<Character, String> replacements = new LinkedHashMap<>();
        replacements.put('\u2010', "-");
        replacements.put('\u2011', "-");
        replacements.put('\u2012', "-");
        replacements.put('\u2013', "-");
        replacements.put('\u2014', "-");
        replacements.put('\u2015', "-");
        replacements.put('\u2212', "-");
        replacements.put('\u2018', "'");
        replacements.put('\u2019', "'");
        replacements.put('\u201A', "'");
        replacements.put('\u201B', "'");
        replacements.put('\u201C', "\"");
        replacements.put('\u201D', "\"");
        replacements.put('\u2022', "•");
        replacements.put('\u25CF', "•");
        replacements.put('\u25E6', "•");
        replacements.put('\u25A0', "■");
        replacements.put('\u25BA', "");
        replacements.put('\u25B6', "");
        replacements.put('\u2713', "•");
        replacements.put('\u2714', "•");
        replacements.put('\u2717', "✗");
        replacements.put('\u00D7', "×");
        replacements.put('\u00F7', "÷");
        replacements.put('\u2126', "Ω");
        replacements.put('\u03A9', "Ω");
        replacements.put('\u00B5', "μ");
        replacements.put('\u03BC', "μ");
        replacements.put('\u00B7', "·");
        replacements.put('\u2026', "...");
        replacements.put('\u2070', "0");
        replacements.put('\u00B9', "1");
        replacements.put('\u00B2', "2");
        replacements.put('\u00B3', "3");
        replacements.put('\u2074', "4");
        replacements.put('\u2075', "5");
        replacements.put('\u2076', "6");
        replacements.put('\u2077', "7");
        replacements.put('\u2078', "8");
        replacements.put('\u2079', "9");
        replacements.put('\u207A', "+");
        replacements.put('\u207B', "-");
        replacements.put('\u207C', "=");
        replacements.put('\u207D', "(");
        replacements.put('\u207E', ")");
        replacements.put('\u2080', "0");
        replacements.put('\u2081', "1");
        replacements.put('\u2082', "2");
        replacements.put('\u2083', "3");
        replacements.put('\u2084', "4");
        replacements.put('\u2085', "5");
        replacements.put('\u2086', "6");
        replacements.put('\u2087', "7");
        replacements.put('\u2088', "8");
        replacements.put('\u2089', "9");
        replacements.put('\u208A', "+");
        replacements.put('\u208B', "-");
        replacements.put('\u208C', "=");
        replacements.put('\u208D', "(");
        replacements.put('\u208E', ")");
        return replacements;
    }
}
