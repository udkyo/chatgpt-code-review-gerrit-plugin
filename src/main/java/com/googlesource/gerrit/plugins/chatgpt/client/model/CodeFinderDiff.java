package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.TreeMap;

@AllArgsConstructor
@Data
public class CodeFinderDiff {
    private DiffContent content;
    private TreeMap<Integer, Integer> charToLineMap;
}
