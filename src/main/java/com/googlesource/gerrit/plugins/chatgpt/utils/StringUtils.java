package com.googlesource.gerrit.plugins.chatgpt.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StringUtils {
    private static final String COMMA = ", ";
    private static final String SEMICOLON = "; ";

    public static String parseOutOfDelimiters(String body, String splitDelim, Function<String, String> processMessage,
                                              String leftDelimReplacement, String rightDelimReplacement) {
        String[] chunks = body.split(splitDelim, -1);
        List<String> resultChunks = new ArrayList<>();
        int lastChunk = chunks.length -1;
        for (int i = 0; i <= lastChunk; i++) {
            String chunk = chunks[i];
            if (i % 2 == 0 || i == lastChunk) {
                resultChunks.add(processMessage.apply(chunk));
            }
            else {
                resultChunks.addAll(Arrays.asList(leftDelimReplacement, chunk, rightDelimReplacement));
            }
        }
        return concatenate(resultChunks);
    }

    public static String parseOutOfDelimiters(String body, String splitDelim, Function<String, String> processMessage) {
        return parseOutOfDelimiters(body, splitDelim, processMessage, splitDelim, splitDelim);
    }

    public static String backslashEachChar(String body) {
        StringBuilder slashedBody = new StringBuilder();

        for (char ch : body.toCharArray()) {
            slashedBody.append("\\\\").append(ch);
        }
        return slashedBody.toString();
    }

    public static String concatenate(List<String> components) {
        return String.join("", components);
    }

    public static String joinWithNewLine(List<String> components) {
        return String.join("\n", components);
    }

    public static String joinWithComma(Set<String> components) {
        return String.join(COMMA, components);
    }

    public static String joinWithSemicolon(List<String> components) {
        return String.join(SEMICOLON, components);
    }

    public static List<String> getNumberedList(List<String> components) {
        return IntStream.range(0, components.size())
                .mapToObj(i -> (i + 1) + ". " + components.get(i))
                .collect(Collectors.toList());
    }

    public static String getNumberedListString(List<String> components) {
        return joinWithSemicolon(getNumberedList(components));
    }

}
