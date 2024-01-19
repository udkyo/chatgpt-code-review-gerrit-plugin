package com.googlesource.gerrit.plugins.chatgpt.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class ReviewUtils {

    public static String processGerritMessage(String message) {
        return message.replaceAll( "```\\w+|(?<!\\n\\n)```", "\n\n```");
    }

    public static long getTimeStamp(String updatedString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");
        LocalDateTime updatedDateTime = LocalDateTime.parse(updatedString, formatter);
        return updatedDateTime.toInstant(ZoneOffset.UTC).getEpochSecond();
    }

}
