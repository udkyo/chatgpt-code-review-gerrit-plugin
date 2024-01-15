package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatCompletionRequest {
    private String model;
    private boolean stream;
    private double temperature;
    private int seed;
    private List<Message> messages;
    private List<Tool> tools;
    private ToolChoice tool_choice;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Tool {
        private String type;
        private Function function;

        @Data
        public static class Function {
            private String name;
            private String description;
            private Parameters parameters;

            @Data
            public static class Parameters {
                private String type;
                private Properties properties;
                private List<String> required;

                @Data
                public static class Properties {
                    private Property suggestions;

                    @Data
                    public static class Property {
                        private String type;
                        private Item items;

                        @Data
                        public static class Item {
                            private String type;
                            private ObjectProperties properties;
                            private List<String> required;

                            @Data
                            public static class ObjectProperties {
                                private Field id;
                                private Field suggestion;
                                private Field filename;
                                private Field lineNumber;
                                private Field codeSnippet;

                                @Data
                                public static class Field {
                                    private String type;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Data
    public static class ToolChoice {
        private String type;
        private Function function;

        @Data
        public static class Function {
            private String name;
        }
    }

}
