package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FileDiffProcessed {
    private List<InputFileDiff.Content> diff;
    private List<String> newContent;
}
