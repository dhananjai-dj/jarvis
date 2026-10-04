package com.project.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiiMasker {

    private static final Map<String, String> keyBucket = new HashMap<>();
    private static final Map<String, String> valueBucket = new HashMap<>();
    private static int counter = 0;

    private static final Pattern COMBINED_PII_PATTERN = Pattern.compile(
            "(?<EMAIL>(?i)\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b)|" +
                    "(?<PHONE>\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b)|" +
                    "(?<CARD>\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b)"
    );

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[(EMAIL|PHONE|CARD)_\\d+\\]");

    public static String maskValue(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        Matcher matcher = COMBINED_PII_PATTERN.matcher(query);
        StringBuilder sb = new StringBuilder(query.length());

        while (matcher.find()) {
            String value = matcher.group();

            String secret = valueBucket.computeIfAbsent(value, k -> {
                String prefix;
                if (matcher.group("EMAIL") != null) {
                    prefix = "EMAIL";
                } else if (matcher.group("PHONE") != null) {
                    prefix = "PHONE";
                } else {
                    prefix = "CARD";
                }

                String placeholder = "[" + prefix + "_" + counter++ + "]";
                keyBucket.put(placeholder, value);
                return placeholder;
            });

            matcher.appendReplacement(sb, Matcher.quoteReplacement(secret));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static String unMaskValue(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(query);
        StringBuilder sb = new StringBuilder(query.length());

        while (matcher.find()) {
            String placeholder = matcher.group();
            String originalValue = keyBucket.getOrDefault(placeholder, placeholder);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(originalValue));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static void clear() {
        keyBucket.clear();
        valueBucket.clear();
        counter = 0;
    }
}