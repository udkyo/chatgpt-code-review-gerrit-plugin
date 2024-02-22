package com.googlesource.gerrit.plugins.chatgpt.client.common;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;

public abstract class ClientBase {
    protected Configuration config;

    public ClientBase(Configuration config) {
        this.config = config;
    }

}
