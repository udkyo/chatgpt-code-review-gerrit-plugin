package com.googlesource.gerrit.plugins.chatgpt.data;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class PluginDataHandlerProvider implements Provider<PluginDataHandler> {
    private final Path defaultPluginDataPath;

    @Inject
    public PluginDataHandlerProvider(@com.google.gerrit.extensions.annotations.PluginData Path defaultPluginDataPath) {
        this.defaultPluginDataPath = defaultPluginDataPath;
    }

    public PluginDataHandler get(Path configPath) {
        return new PluginDataHandler(configPath);
    }

    @Override
    public PluginDataHandler get() {
        return get(defaultPluginDataPath.resolve("plugin.config"));
    }
}
