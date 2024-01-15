package com.googlesource.gerrit.plugins.chatgpt.utils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class FileUtils {

    public static InputStreamReader getInputStreamReader(String filename) {
        return new InputStreamReader(Objects.requireNonNull(
                FileUtils.class.getClassLoader().getResourceAsStream(filename)), StandardCharsets.UTF_8);
    }
}