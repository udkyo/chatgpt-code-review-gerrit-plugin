# ChatGPT Code Review Gerrit Plugin

## Features

This plugin allows you to use ChatGPT for code review in Gerrit conveniently. After submitting a Patch Set, OpenAI will
provide review feedback in the form of comments and, optionally, a vote.
You can continue to ask ChatGPT by @{gerritUserName} or @{gerritEmailAddress} (provided that `gerritEmailAddress` is in
the form "gerritUserName@<any_email_domain>") in the comments to further guide it in generating more targeted review
comments.
Reviews can be also triggered by directing a comment with the `/review` command to ChatGPT.

## Getting Started

1. **Build:** Requires JDK 11 or higher, Maven 3.0 or higher.

   ```bash
   mvn -U clean package
    ```

   If the user needs to disable test just run
   ```bash
   mvn -U -DskipTests=true clean package
   ```

2. **Install:** Upload the compiled jar file to the `$gerrit_site/plugins` directory.

3. **Configure:** First, you need to create a ChatGPT user in Gerrit.
   Then, set up the basic parameters in your `$gerrit_site/etc/gerrit.config` file under the section

   `[plugin "chatgpt-code-review-gerrit-plugin"]`:

- `gptToken`: OpenAI GPT token.
- `gerritUserName`: Gerrit username of ChatGPT user.
- `globalEnable`: Default value is false. The plugin will only review specified repositories. If set to true, the plugin
   will by default review all pull requests.

   For enhanced security, consider storing sensitive information like gptToken in a secure location
   or file. Detailed instructions on how to do this will be provided later in this document.

4. **Verify:** After restarting Gerrit, you can see the following information in Gerrit's logs:

   ```bash
   INFO com.google.gerrit.server.plugins.PluginLoader : Loaded plugin chatgpt-code-review-gerrit-plugin, version ...
   ```

   You can also check the status of the chatgpt-code-review-gerrit-plugin on Gerrit's plugin page as Enabled.

## Usage Examples

### Auto review on Patch Set submission

In the following example, a Patch Set receives a score of "-1" indicating a recommendation.

![Example of Vote](images/chatgpt_vote.png?raw=true)

**NOTE**: Voting is disabled by default. To use this feature, it needs to be activated either across all projects or on
a per-project basis via the `enabledVoting` configuration option, as described below.

### ChatGPT Score Adjustment Following User Interaction

In the example below, ChatGPT initially posits a potential unintended behavior in the code, assigning a "-1" score.
Upon receiving clarification, it resets the score to "0".

![Example of Dialogue](images/chatgpt_changed_mind.png?raw=true)

More examples of ChatGPT's code reviews and inline discussions are available at
https://wiki.amarulasolutions.com/opensource/products/chatgpt-gerrit.html

## Configuration Parameters

You have the option to establish global settings, or independently configure specific projects. If you choose
independent configuration, the corresponding project settings will override the global parameters.

### Global Configuration

To configure these parameters, you need to modify your Gerrit configuration file (`gerrit.config`). The file format is
as follows:

```
[plugin "chatgpt-code-review-gerrit-plugin"]
    # Required parameters
    gptToken = {gptToken}
    ...

    # Optional parameters
    gptModel = {gptModel}
    gptSystemPrompt = {gptSystemPrompt}
    ...
```

#### Secure Configuration

It is highly recommended to store sensitive information such as `gptToken` in the `secure.config`
file. Please edit the file at $gerrit_site/etc/`secure.config` and include the following details:

```
[plugin "chatgpt-code-review-gerrit-plugin"]
    gptToken = {gptToken}
```

If you wish to encrypt the information within the `secure.config` file, you can refer
to: https://gerrit.googlesource.com/plugins/secure-config

### Project Configuration

To add the following content, please edit the `project.config` file in `refs/meta/config`:

```
[plugin "chatgpt-code-review-gerrit-plugin"]
    # Required parameters
    gerritUserName = {gerritUserName}
    ...

    # Optional parameters
    gptModel = {gptModel}
    gptSystemPrompt = {gptSystemPrompt}
    ...
```

#### Secure Configuration

Please ensure **strict control over the access permissions of `refs/meta/config`** since sensitive information such as
`gptToken` is configured in the `project.config` file within `refs/meta/config`.

## Stateful and Stateless modes

Starting from version 3, the plugin introduces a new Stateful interaction mode with ChatGPT is available alongside the
traditional Stateless mode.

### Stateless Mode

In Stateless mode, each request to ChatGPT must include all contextual information and instructions necessary for
ChatGPT to provide insights, utilizing the **Chat Completion** API calls made available by OpenAI.

### Stateful Mode:

Conversely, Stateful mode uses the **Assistant** resource, recently made available in beta by OpenAI, to maintain a
richer interaction context. This mode is designed to:

- Leverage ChatGPT Threads to preserve the memory of conversations related to each Change Set.
- Link these Threads with ChatGPT Assistants that are specialized according to the response needed.
- Associate the Assistants with the complete Codebase of the Git project related to the Change, which is updated
each time commits are merged in Gerrit.

The advantages of the Stateful mode over the Stateless are twofold:
1. To minimize the payload sent to ChatGPT, as it eliminates the need to send contextual information and instructions
with every single request.
2. To enhance the quality of responses from ChatGPT by expanding the context to encompass the entire project and
allowing for the preprocessing of this context along with instructions. This enables ChatGPT to focus more effectively
on the specific requests made.

### Optional Parameters

- `gptMode`: Select whether requests are processed in Stateless or Stateful mode. For backward compatibility, the
default value is `stateless`. To enable Stateful mode, set this parameter to `stateful`.
- `gptModel`: The default model is `gpt-4o`. You can also configure it to `gpt-3.5-turbo` or `gpt-4-turbo`.
- `gptDomain`: The default ChatGPT domain is `https://api.openai.com`.
- `gptSystemPrompt`: You can modify the default system prompt ("Act as a PatchSet Reviewer") to your preferred prompt.
- `gptReviewTemperature`: Specifies the temperature setting for ChatGPT when reviewing a Patch Set, with a default
  setting of 0.2. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more
  focused and deterministic.
- `gptCommentTemperature`: Specifies the temperature setting for ChatGPT when replying to a comment, with a default
  setting of 1.0.
- `gptReviewPatchSet`: Set to true by default. When switched to false, it disables the automatic review of Patch Sets as
  they are created or updated.
- `gptReviewCommitMessages`: The default value is true. When enabled, this option also verifies if the commit message
  matches with the content of the Change Set.
- `gptFullFileReview`: Enabled by default. Activating this option sends both unchanged lines and changes to ChatGPT for
  review, offering additional context information. Deactivating it (set to false) results in only the changed lines
  being submitted for review.
- `gptStreamOutput`: The default value is false. Whether the response is expected in stream output mode or not.
- `maxReviewLines`: The default value is 1000. This sets a limit on the number of lines of code included in the review.
- `maxReviewFileSize`: Set with a default value of 10000, this parameter establishes a cap on the file size that can be
  included in reviews.
- `enabledUsers`: By default, every user is enabled to have their Patch Sets and comments reviewed. To limit review
  capabilities to specific users, list their usernames in this setting, separated by commas.
- `disabledUsers`: Functions oppositely to enabledUsers.
- `enabledGroups`: By default, all groups are permitted to have their Patch Sets and comments reviewed. To restrict
  review access to certain groups, specify their names in this setting, separating them with commas.
- `disabledGroups`: Operates in reverse to `enabledGroups`, excluding specified groups from reviews.
- `enabledTopicFilter`: Specifies a list of keywords that trigger ChatGPT reviews based on the topic of the Patch Set.
  When this setting is active, only Patch Sets and their associated comments containing at least one of these keywords
  in the topic are reviewed.
- `disabledTopicFilter`: Works in contrast to enabledTopicFilter, excluding Patch Sets and comments from review if their
  topics contain specified keywords.
- `enabledFileExtensions`: This limits the reviewed files to the given types. Default file extensions are ".py, .java,
  .js, .ts, .html, .css, .cs, .cpp, .c, .h, .php, .rb, .swift, .kt, .r, .jl, .go, .scala, .pl, .pm, .rs, .dart, .lua,
  .sh, .vb, .bat".
- `enabledVoting`: Initially disabled (false). If set to true, allows ChatGPT to cast a vote on each reviewed Patch Set
  by assigning a score.
- `filterNegativeComments`: Activated by default (true), ensuring only negative review comments (scored below the
  `filterCommentsBelowScore` threshold outlined further) are displayed. Disabling this setting (false) will
  also show positive and neutral comments.
- `filterCommentsBelowScore`: With `filterNegativeComments` active, review comments with a score at or above this
  setting's value will not be shown (default is 0).
- `filterRelevantComments`: This setting is enabled by default (true) to display only those review comments considered
  relevant by ChatGPT, which means they have a relevance index at or above the `filterCommentsRelevanceThreshold`
  specified below. Turning off this option (false) allows the display of comments ChatGPT marks as irrelevant.
- `filterCommentsRelevanceThreshold`: When `filterRelevantComments` is enabled, any review comment assigned a relevance
  score by ChatGPT below this threshold will not be shown. The default threshold is set at 0.6.
- `gptRelevanceRules`: This option allows customization of the rules ChatGPT uses to determine the relevance of a task.
- `patchSetCommentsAsResolved`: Initially set to false, this option leaves ChatGPT's Patch Set comments as unresolved,
  inviting further discussion. If activated, it marks ChatGPT's Patch Set comments as resolved.
- `inlineCommentsAsResolved`: Initially set to false, this option leaves ChatGPT's inline comments as unresolved,
  inviting further discussion. If activated, it marks ChatGPT's inline comments as resolved.
- `votingMinScore`: The lowest possible score that can be given to a Patch Set (Default value: -1).
- `votingMaxScore`: The highest possible score that can be given to a Patch Set (Default value: +1).
- `ignoreResolvedChatGptComments`: Determines if resolved comments from ChatGPT should be disregarded. The default
  setting is true, which means resolved ChatGPT comments are not used for generating new comments or identifying
  duplicate content. If set to false, resolved ChatGPT comments are factored into these processes.
- `ignoreOutdatedInlineComments`: Determines if inline comments made on non-latest Patch Sets should be disregarded. By
  default, this is set to false, meaning all inline comments are used for generating new responses and identifying
  repetitions. If enabled (true), inline comments from previous Patch Sets are excluded from these considerations.
- `enableMessageDebugging`: This setting controls the activation of debugging functionalities through messages (default
value is false). When set to true, it enables commands and options like `--debug` for users as well as the Dynamic
Configuration commands.
- `forceCreateAssistant`: In Stateful mode, forces the creation of a new assistant with each request instead of only
when configuration settings change or Changes are merged.

  **NOTE**: This option may increase OpenAI API usage and should be used for **testing or debugging purposes only**.

#### Optional Parameters for Global Configuration only

- `globalEnable`: Set to false by default, meaning the plugin will review only designated repositories. If enabled, the
  plugin will automatically review all pull requests by default (not recommended in production environments).
- `enabledProjects`: The default value is an empty string. If globalEnable is set to false, the plugin will only run in
  the repositories specified here. The value should be a comma-separated list of repository names, for example:
  "project1,project2,project3".

#### Optional Parameters for Project Configuration only

- `isEnabled`: The default is false. If set to true, the plugin will review the Patch Set of this project.

## Commands

### Review Commands

Reviewing a Change Set or the last Patch Set can occur automatically upon submission or be manually triggered using the
commands outlined in this section.

#### Basic Syntax
- `/review`: when used in a comment directed at ChatGPT on any Change Set, triggers a review of the full Change Set. A
  vote is cast on the Change Set if the voting feature is enabled and the ChatGPT Gerrit user is authorized to vote on
  it.
- `/review_last`: when used in a comment directed at ChatGPT on any Change Set, triggers a review of the last Patch Set
  of the Change Set. Unlike `/review`, this command does not result in casting or updating votes.

#### Command Options

- `--filter=[true/false]`: Controls the filtering of duplicate, conflicting and irrelevant comments, defaulting to
  "true" to apply filters.
- `--debug`: When paired with `/review` or `/review_last` commands, this option displays useful debug information in
  each ChatGPT reply, showing all replies as though the filter setting were disabled.

  **NOTE**: The usage of `--debug` option is disabled by default. To enable it, `enableMessageDebugging` setting must be
  set to true.

### Directives

Directives are mandatory instructions written in plain English that ChatGPT must adhere to during its reviews. They can
be specified using the following command.

#### Basic Syntax
`/directive <DIRECTIVE_CONTENT>`: This command, when included in a comment with a subsequent directive description
"<DIRECTIVE_CONTENT>", specifies a directive that ChatGPT must adhere to.

### Dynamic Configuration

You can now dynamically alter the plugin configuration via messages sent to the ChatGPT user, primarily for testing and
debugging purposes. This feature becomes available when the `enableMessageDebugging` configuration setting is enabled.

#### Basic Syntax
- `/configure` displays the current settings and their dynamically modified values in a response message.
- `/configure --<CONFIG_KEY_1>=<CONFIG_VALUE_1> [... --<CONFIG_KEY_N>=<CONFIG_VALUE_N>]` assigns new values to one or
  more configuration keys.

  **NOTE**: Values that include spaces, such as `gptSystemPrompt`, must be enclosed in double quotes.

#### Command Options

The `reset` option can be employed to restore modified settings to their original defaults. Its usage is detailed below:
- `/configure --reset` restores all modified settings to their default values.
- `/configure --reset --<CONFIG_KEY_1> [... --<CONFIG_KEY_N>]` specifically restores the indicated key(s) to their
  default values.

## Testing

- You can run the unit tests in the project to familiarize yourself with the plugin's source code.
- If you want to individually test the Gerrit API or the ChatGPT API, you can refer to the test cases in
  CodeReviewPluginIT.

## License

Apache License 2.0
