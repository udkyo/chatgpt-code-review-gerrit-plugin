package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

@Data
public class DiffContent {
    public String a;
    public String b;
    public String ab;
}
