package com.googlesource.gerrit.plugins.chatgpt.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReviewUtils {

    public static String[] extractID(String inputString) {
        String regex = "\\[[\\w \\-]*ID:(\\d+)\\]:?\\s*";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(inputString);

        if (matcher.find()) {
            String capturedNumber = matcher.group(1);
            String modifiedString = matcher.replaceFirst("");
            return new String[]{capturedNumber, modifiedString};
        } else {
            return null;
        }
    }

}