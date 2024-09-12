package com.googlesource.gerrit.plugins.chatgpt.localization;

import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.ResourceBundle;

@Slf4j
public class Localizer {
    private final ResourceBundle resourceBundle;

    @Inject
    public Localizer(Configuration config) {
        this.resourceBundle = ResourceBundle.getBundle("localization.localTexts", config.getLocaleDefault());
    }

    public String getText(String key) {
        return resourceBundle.getString(key);
    }
}
