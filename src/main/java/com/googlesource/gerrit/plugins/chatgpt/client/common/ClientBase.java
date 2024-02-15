package com.googlesource.gerrit.plugins.chatgpt.client.common;

import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;

public abstract class ClientBase {
    protected final Gson gson = new Gson();
    protected Configuration config;

    public ClientBase(Configuration config) {
        this.config = config;
    }

}
