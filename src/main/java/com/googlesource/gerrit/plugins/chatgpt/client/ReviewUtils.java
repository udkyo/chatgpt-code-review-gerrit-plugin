package com.googlesource.gerrit.plugins.chatgpt.client;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    public static long getTimeStamp(String updatedString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");
        LocalDateTime updatedDateTime = LocalDateTime.parse(updatedString, formatter);
        return updatedDateTime.toInstant(ZoneOffset.UTC).getEpochSecond();
    }

}
