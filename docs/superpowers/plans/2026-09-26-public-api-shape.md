# Public API Shape for 0.1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the library its 0.1.0 public surface: `io.github.baevkir` coordinates and packages, a read-only `CommandContext`, i18n keyed on `User`, internals behind `internal`, settled names and defaults, and group-chat command addressing.

**Architecture:** A scripted move puts every class in its final package first, so later tasks edit files once. `CommandContext` becomes an interface implemented by `internal.ConversationState`, which keeps the fold state and mutators; the pipeline works with `ConversationState`, everything a bot touches sees `CommandContext`. The remaining tasks are focused API changes, each with its own tests.

**Tech Stack:** Java 25, Spring Boot 4.1, Maven, Project Reactor, Lombok, telegrambots 10.0.0, JUnit 6, AssertJ, Mockito, reactor-test.

**Spec:** `docs/superpowers/specs/2026-09-26-public-api-shape-design.md`

## Global Constraints

- Branch: `feat/public-api-0.1` (built on `fix/pre-release-hardening`). Never commit to `master`.
- Commits: conventional style (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`), **no** `Co-Authored-By` or any AI attribution trailer. Never stage `target/`, `.idea/`, `.DS_Store`, `.claude/`.
- New files are `git add`-ed in the task that creates them.
- Coordinates: `groupId` `io.github.baevkir`, `artifactId` `sessionbot-telegram-spring-boot-starter`, version `0.1.0-SNAPSHOT`.
- Root package `io.github.baevkir.sessionbot`. Property prefix `sessionbot.telegram.*` unchanged.
- Java style: class members ordered fields → constructors → lifecycle → public methods → private helpers → nested types; no one-letter identifiers except `i`/`j` in classic `for` loops; `getFirst()`/`getLast()` over index access; SLF4J with the exception as last argument, never `ex.getMessage()` alongside it.
- Run tests with `mvn -o -q test` (offline, dependencies are cached). A single class: `mvn -o -q test -Dtest=ClassName`.
- Every task ends with the full suite green.

## Review Focus

1. **A burst of bare updates** (a user sends several documents or forwards several messages at once) — every one must be dispatched; nothing may be dropped because an earlier one closed the chat's stream. Pinned in Task 4 (`consecutiveBareUpdatesAreAllDispatched`).
2. **A bot's handler code calling `context.getCommandUpdate().getFrom()` on a bare update** (all four family-iot handlers do) — must still return the sender, not throw. Pinned in Task 3 (`bareContextExposesItsUpdateAsCommandUpdate`).
3. **Group commands with a differently-cased or empty addressee** (`/order@MYBOT`, `/order@`) — processed as addressed to this bot. Pinned in Task 9.
4. **Degenerate callback data** (`#`, `/`, `/order@?x`) — parsed without an exception. Pinned in Task 8.
5. **An existing handler implementation declared as `Publisher<PartialBotApiMethod<?>> handle(...)`** — still compiles after the return type widens. Pinned in Task 6.

---

### Task 1: Move to `io.github.baevkir` and the 0.1.0 package layout

Mechanical, behavior-preserving. One script moves every file, rewrites packages, fully-qualified names and imports, renames four classes, and deletes the unused presenters.

**Files:**
- Modify: `pom.xml:12-16`
- Move: every file under `src/main/java/com/kb/sessionbot/` and `src/test/java/com/kb/sessionbot/` (mapping in the script)
- Delete: `commands/presenter/AbstractMessagePresenter.java`, `commands/presenter/BotMethodPresenter.java`, test `commands/presenter/AbstractMessagePresenterTest.java`
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `src/main/java/io/github/baevkir/sessionbot/internal/package-info.java`

**Interfaces:**
- Produces (used by every later task) — the class → package map below. Renamed: `IBotCommand` → `internal.RegisteredCommand`, `ParameterRendererFactory` → `render.CompositeParameterRenderer`, `CommandsSessionBotConfiguration` → `autoconfigure.SessionBotAutoConfiguration`, `CommandsSessionBotProperties` → `autoconfigure.SessionBotProperties`, test `CommandsSessionBotAutoConfigurationTest` → `autoconfigure.SessionBotAutoConfigurationTest`.

- [ ] **Step 1: Save the relayout script** to `$TMPDIR/relayout.py` (outside the repo):

```python
#!/usr/bin/env python3
"""Moves com.kb.sessionbot to io.github.baevkir.sessionbot with the 0.1.0 package layout.

Run once from the repository root. Moves files with `git mv`, rewrites package declarations,
fully-qualified references and imports, renames classes, and adds the imports that
same-package references need after the split.
"""
import pathlib
import re
import subprocess

OLD_ROOT = "com.kb.sessionbot"
NEW_ROOT = "io.github.baevkir.sessionbot"

MAIN = {
    "ChatUpdateStream": "ChatUpdateStream",
    "InboundUpdateBus": "InboundUpdateBus",
    "MessageExecutor": "MessageExecutor",
    "OutboundMessageBus": "OutboundMessageBus",
    "auth/AuthInterceptor": "AuthInterceptor",
    "commands/CommandBuilder": "CommandBuilder",
    "model/UpdateWrapper": "UpdateWrapper",
    "model/DynamicParameters": "DynamicParameters",
    "model/CommandContext": "CommandContext",

    "commands/dispatcher/annotations/BotCommand": "annotation.BotCommand",
    "commands/dispatcher/annotations/CommandMethod": "annotation.CommandMethod",
    "commands/dispatcher/annotations/Parameter": "annotation.Parameter",
    "commands/dispatcher/annotations/Rendering": "annotation.Rendering",
    "commands/dispatcher/annotations/RenderingOption": "annotation.RenderingOption",

    "guard/CommandGuard": "guard.CommandGuard",
    "guard/GuardContext": "guard.GuardContext",
    "guard/GuardDeniedHandler": "guard.GuardDeniedHandler",
    "guard/Guarded": "guard.Guarded",

    "contacts/ContactHandler": "handler.ContactHandler",
    "documents/DocumentHandler": "handler.DocumentHandler",
    "text/TextHandler": "handler.TextHandler",

    "commands/dispatcher/parameters/ParameterRenderer": "render.ParameterRenderer",
    "commands/dispatcher/parameters/ParameterRequest": "render.ParameterRequest",
    "commands/dispatcher/parameters/ParameterRendererFactory": "render.CompositeParameterRenderer",
    "commands/dispatcher/parameters/TextParameterRenderer": "render.TextParameterRenderer",
    "commands/dispatcher/parameters/DateParameterRenderer": "render.DateParameterRenderer",
    "commands/dispatcher/parameters/BooleanParameterRenderer": "render.BooleanParameterRenderer",
    "commands/dispatcher/parameters/TimeParameterRenderer": "render.TimeParameterRenderer",
    "model/Option": "render.Option",

    "errors/handler/ErrorHandler": "error.ErrorHandler",
    "errors/handler/BotCommandErrorHandler": "error.BotCommandErrorHandler",
    "errors/handler/BotAuthErrorHandler": "error.BotAuthErrorHandler",
    "errors/exception/BotCommandException": "error.BotCommandException",
    "errors/exception/BotAuthException": "error.BotAuthException",

    "i18n/LocaleProvider": "i18n.LocaleProvider",
    "i18n/ConfiguredLocaleProvider": "i18n.ConfiguredLocaleProvider",
    "i18n/BotLabels": "i18n.BotLabels",

    "menu/CommandMenuService": "menu.CommandMenuService",

    "config/CommandsSessionBotConfiguration": "autoconfigure.SessionBotAutoConfiguration",
    "config/CommandsSessionBotProperties": "autoconfigure.SessionBotProperties",

    "CommandsSessionBot": "internal.CommandsSessionBot",
    "SinkInboundUpdateBus": "internal.SinkInboundUpdateBus",
    "SinkOutboundMessageBus": "internal.SinkOutboundMessageBus",
    "TelegramClientMessageExecutor": "internal.TelegramClientMessageExecutor",
    "TelegramUpdateHandler": "internal.TelegramUpdateHandler",
    "commands/CommandConstants": "internal.CommandConstants",
    "commands/CommandsFactory": "internal.CommandsFactory",
    "commands/HelpCommand": "internal.HelpCommand",
    "commands/IBotCommand": "internal.RegisteredCommand",
    "commands/TelegramHtml": "internal.TelegramHtml",
    "commands/WireFormat": "internal.WireFormat",
    "commands/dispatcher/CommandsDispatcher": "internal.CommandsDispatcher",
    "commands/dispatcher/DispatcherBotCommand": "internal.DispatcherBotCommand",
    "commands/dispatcher/InvocationResultResolver": "internal.InvocationResultResolver",
    "commands/dispatcher/MethodMatcher": "internal.MethodMatcher",
    "model/ContextState": "internal.ContextState",
    "model/MessageDescriptor": "internal.MessageDescriptor",
    "model/MethodDescriptor": "internal.MethodDescriptor",
    "model/ParameterDescriptor": "internal.ParameterDescriptor",
    "guard/CommandGuards": "internal.CommandGuards",
    "guard/GuardResolver": "internal.GuardResolver",
    "guard/HelpGuardDeniedHandler": "internal.HelpGuardDeniedHandler",
    "menu/CommandMenus": "internal.CommandMenus",
    "errors/handler/ErrorHandlerFactory": "internal.ErrorHandlerFactory",
}

TEST = {
    "CommandsSessionBotTest": "internal.CommandsSessionBotTest",
    "MessageExecutorTest": "internal.MessageExecutorTest",
    "SinkInboundUpdateBusTest": "internal.SinkInboundUpdateBusTest",
    "SinkOutboundMessageBusTest": "internal.SinkOutboundMessageBusTest",
    "TelegramUpdateHandlerAuthTest": "internal.TelegramUpdateHandlerAuthTest",
    "TelegramUpdateHandlerGuardTest": "internal.TelegramUpdateHandlerGuardTest",
    "TelegramUpdateHandlerTest": "internal.TelegramUpdateHandlerTest",
    "commands/CommandBuilderTest": "CommandBuilderTest",
    "commands/CommandsFactoryTest": "internal.CommandsFactoryTest",
    "commands/HelpCommandTest": "internal.HelpCommandTest",
    "commands/MessageDescriptorTest": "internal.MessageDescriptorTest",
    "commands/TelegramHtmlTest": "internal.TelegramHtmlTest",
    "commands/WireFormatTest": "internal.WireFormatTest",
    "commands/dispatcher/CommandsDispatcherTest": "internal.CommandsDispatcherTest",
    "commands/dispatcher/DispatcherBotCommandGuardsTest": "internal.DispatcherBotCommandGuardsTest",
    "commands/dispatcher/MethodMatcherTest": "internal.MethodMatcherTest",
    "commands/dispatcher/ProxiedCommandTest": "internal.ProxiedCommandTest",
    "commands/dispatcher/parameters/DateParameterRendererTest": "render.DateParameterRendererTest",
    "commands/dispatcher/parameters/TimeParameterRendererTest": "render.TimeParameterRendererTest",
    "config/CommandsSessionBotAutoConfigurationTest": "autoconfigure.SessionBotAutoConfigurationTest",
    "errors/handler/ErrorHandlerFactoryTest": "internal.ErrorHandlerFactoryTest",
    "fixtures/BadInjectionCommand": "fixtures.BadInjectionCommand",
    "fixtures/EchoCommand": "fixtures.EchoCommand",
    "fixtures/FixtureCommandConfig": "fixtures.FixtureCommandConfig",
    "fixtures/Fixtures": "fixtures.Fixtures",
    "fixtures/OrderCommand": "fixtures.OrderCommand",
    "guard/CommandGuardsTest": "internal.CommandGuardsTest",
    "guard/GuardContextTest": "guard.GuardContextTest",
    "guard/GuardResolverTest": "internal.GuardResolverTest",
    "i18n/BotLabelsTest": "i18n.BotLabelsTest",
    "i18n/ConfiguredLocaleProviderTest": "i18n.ConfiguredLocaleProviderTest",
    "i18n/I18nAutoConfigurationTest": "i18n.I18nAutoConfigurationTest",
    "menu/CommandMenuServiceTest": "menu.CommandMenuServiceTest",
    "menu/CommandMenusTest": "internal.CommandMenusTest",
    "model/CommandContextTest": "CommandContextTest",
    "model/DynamicParametersTest": "DynamicParametersTest",
    "model/UpdateWrapperTest": "UpdateWrapperTest",
}

DELETE = [
    "src/main/java/com/kb/sessionbot/commands/presenter/AbstractMessagePresenter.java",
    "src/main/java/com/kb/sessionbot/commands/presenter/BotMethodPresenter.java",
    "src/test/java/com/kb/sessionbot/commands/presenter/AbstractMessagePresenterTest.java",
]


def old_fqn(path):
    return OLD_ROOT + "." + path.replace("/", ".")


def new_fqn(suffix):
    return NEW_ROOT + "." + suffix


def package_of(fqn):
    return fqn.rsplit(".", 1)[0]


def simple(fqn):
    return fqn.rsplit(".", 1)[1]


def main():
    for path in DELETE:
        subprocess.run(["git", "rm", "-q", path], check=True)

    renames = {}
    moves = []
    for source_root, table in (("src/main/java", MAIN), ("src/test/java", TEST)):
        for old_path, suffix in table.items():
            renames[old_fqn(old_path)] = new_fqn(suffix)
            source = pathlib.Path(source_root, "com/kb/sessionbot", old_path + ".java")
            target = pathlib.Path(source_root, *new_fqn(suffix).split(".")).with_suffix(".java")
            moves.append((source, target, new_fqn(suffix)))

    for source, target, _ in moves:
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "mv", str(source), str(target)], check=True)

    simple_renames = {simple(old): simple(new) for old, new in renames.items() if simple(old) != simple(new)}
    by_simple = {simple(new): new for new in renames.values()}

    for _, target, fqn in moves:
        text = target.read_text()
        own_package = package_of(fqn)
        text = re.sub(r"^package [\w.]+;", f"package {own_package};", text, count=1, flags=re.M)
        text = re.sub(r"^import (%s)\.[\w.]*\*;\n" % re.escape(OLD_ROOT), "", text, flags=re.M)
        for old in sorted(renames, key=len, reverse=True):
            text = re.sub(r"\b%s\b" % re.escape(old), renames[old], text)
        for old_simple, new_simple in simple_renames.items():
            text = re.sub(r"\b%s\b" % re.escape(old_simple), new_simple, text)
        text = add_missing_imports(text, own_package, by_simple)
        target.write_text(text)

    imports_file = pathlib.Path("src/main/resources/META-INF/spring/"
                                "org.springframework.boot.autoconfigure.AutoConfiguration.imports")
    imports_text = imports_file.read_text()
    for old in sorted(renames, key=len, reverse=True):
        imports_text = imports_text.replace(old, renames[old])
    imports_file.write_text(imports_text)

    leftovers = subprocess.run(["grep", "-rln", OLD_ROOT, "src"], capture_output=True, text=True).stdout
    if leftovers:
        print("References to the old root remain in:\n" + leftovers)


def add_missing_imports(text, own_package, by_simple):
    imported = set(re.findall(r"^import (?:static )?([\w.]+);", text, flags=re.M))
    imported_simple = {name.rsplit(".", 1)[1] for name in imported}
    body = re.sub(r"^(package|import) .*$", "", text, flags=re.M)
    body = re.sub(r"//.*|/\*[\s\S]*?\*/|\"(?:\\.|[^\"\\])*\"", "", body)
    needed = []
    for name, fqn in sorted(by_simple.items()):
        if package_of(fqn) == own_package or fqn in imported or name in imported_simple:
            continue
        if re.search(r"(?<![\w.])%s\b" % re.escape(name), body):
            needed.append(fqn)
    text = re.sub(r"^import %s\.(\w+);\n" % re.escape(own_package), "", text, flags=re.M)
    if not needed:
        return text
    block = "".join(f"import {fqn};\n" for fqn in needed)
    match = re.search(r"^import .*;$", text, flags=re.M)
    if match:
        return text[:match.start()] + block + text[match.start():]
    package_end = re.search(r"^package .*;\n", text, flags=re.M).end()
    return text[:package_end] + "\n" + block + text[package_end:]


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run it**

Run: `python3 "$TMPDIR/relayout.py" && find src -path '*com/kb*' -type f`
Expected: no "References to the old root remain" output, and `find` prints nothing. (The script was dry-run on a copy of this branch before the plan was written: it compiled and 247 tests passed — 248 minus the deleted presenter test.)

- [ ] **Step 3: Update the coordinates in `pom.xml`**

Replace

```xml
    <groupId>com.kb</groupId>
    <artifactId>telegram-session-bot</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <name>telegram-session-bot</name>
```

with

```xml
    <groupId>io.github.baevkir</groupId>
    <artifactId>sessionbot-telegram-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>

    <name>sessionbot-telegram-spring-boot-starter</name>
```

- [ ] **Step 4: Mark the internal package** — create `src/main/java/io/github/baevkir/sessionbot/internal/package-info.java`:

```java
/**
 * The library's implementation. Nothing here is public API: types may change or disappear in any
 * release. Bots use the types in {@code io.github.baevkir.sessionbot} and its other sub-packages.
 */
package io.github.baevkir.sessionbot.internal;
```

- [ ] **Step 5: Build and test**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS, `Tests run: 247, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add -A src pom.xml
git status --short   # only src/ and pom.xml changes; nothing under target/ or .claude/
git commit -m "refactor: move to io.github.baevkir and the 0.1.0 package layout"
```

---

### Task 2: Rename `scipAnswer`, drop `approved`/`initiator`, `ParameterOption`, annotation targets

**Files:**
- Modify: `src/main/java/io/github/baevkir/sessionbot/internal/CommandConstants.java`
- Modify: `src/main/java/io/github/baevkir/sessionbot/CommandBuilder.java`
- Modify: `src/main/java/io/github/baevkir/sessionbot/DynamicParameters.java`
- Modify: `src/main/java/io/github/baevkir/sessionbot/render/{Boolean,Date,Text,Time}ParameterRenderer.java`, `internal/DispatcherBotCommand.java`, `internal/CommandsDispatcher.java`
- Create: `src/main/java/io/github/baevkir/sessionbot/render/ParameterOption.java`; Delete: `render/Option.java`
- Modify: `render/ParameterRequest.java`, `internal/ParameterDescriptor.java`, `render/TextParameterRenderer.java`
- Modify: `annotation/Parameter.java`, `annotation/Rendering.java`, `annotation/RenderingOption.java`
- Test: `CommandBuilderTest`, `DynamicParametersTest`, `CommandContextTest`, `UpdateWrapperTest`, `fixtures/EchoCommand`, `internal/CommandsDispatcherTest`, `internal/MessageDescriptorTest`, new `internal/ParameterDescriptorTest`

**Interfaces:**
- Produces: `CommandBuilder.skipAnswer(int)`, `DynamicParameters.canSkipAnswer(int)`, wire key `skip`, `CommandConstants.SKIP_ANSWER_DYNAMIC_PARAM`; `record ParameterOption(String value, String label)`; `ParameterRequest.getOptions(): List<ParameterOption>`.
- Removed: `CommandBuilder.commandApproved()`, `CommandBuilder.setInitiator(String)`, `DynamicParameters.commandApproved()`, `DynamicParameters.getInitiator()`, `CommandConstants.APPROVED_DYNAMIC_PARAM`, `CommandConstants.INITIATOR_DYNAMIC_PARAM`.

- [ ] **Step 1: Write the failing tests.** In `CommandBuilderTest` replace the test `scipAnswerCarriesIndex` with

```java
        @Test
        void skipAnswerCarriesIndex() {
            assertThat(CommandBuilder.create().skipAnswer(3).build()).isEqualTo("#skip:3");
        }
```

and delete the tests `commandApprovedFlag` and `setInitiatorCarriesName`. Create `src/test/java/io/github/baevkir/sessionbot/internal/ParameterDescriptorTest.java`:

```java
package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.annotation.Parameter;
import io.github.baevkir.sessionbot.annotation.Rendering;
import io.github.baevkir.sessionbot.annotation.RenderingOption;
import io.github.baevkir.sessionbot.render.ParameterOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterDescriptorTest {

    @SuppressWarnings("unused")
    void pick(@Parameter(value = "size", rendering = @Rendering(options = {
        @RenderingOption(value = "s", displayValue = "Small"),
        @RenderingOption("m")
    })) String size) {
    }

    @Test
    void renderingOptionsBecomeParameterOptionsWithLabelsDefaultingToTheValue() throws Exception {
        var parameter = getClass().getDeclaredMethod("pick", String.class).getParameters()[0];

        var descriptor = ParameterDescriptor.handleParameter(parameter).build();

        assertThat(descriptor.getOptions()).containsExactly(
            new ParameterOption("s", "Small"),
            new ParameterOption("m", "m"));
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `mvn -o -q test -Dtest='CommandBuilderTest,ParameterDescriptorTest'`
Expected: compilation failure — `skipAnswer(int)` and `ParameterOption` do not exist.

- [ ] **Step 3: Rename skip, drop approved/initiator in main code.** In `internal/CommandConstants.java` replace

```java
    String SCIP_ANSWER_DYNAMIC_PARAM = "scipAnswer";
    String APPROVED_DYNAMIC_PARAM = "approved";
    String INITIATOR_DYNAMIC_PARAM = "initiator";
```

with

```java
    String SKIP_ANSWER_DYNAMIC_PARAM = "skip";
```

In `CommandBuilder.java` replace

```java
    public CommandBuilder scipAnswer(int index) {
        return addParam(SCIP_ANSWER_DYNAMIC_PARAM, String.valueOf(index));
    }

    public CommandBuilder commandApproved() {
        return addParam(APPROVED_DYNAMIC_PARAM);
    }

    public CommandBuilder setInitiator(String name) {
        return addParam(INITIATOR_DYNAMIC_PARAM, name);
    }
```

with

```java
    public CommandBuilder skipAnswer(int index) {
        return addParam(SKIP_ANSWER_DYNAMIC_PARAM, String.valueOf(index));
    }
```

In `DynamicParameters.java` replace the method `canScipAnswer` and the two methods after it (`commandApproved`, `getInitiator`) with

```java
    public boolean canSkipAnswer(int index) {
        if (!params.containsKey(SKIP_ANSWER_DYNAMIC_PARAM)) {
            return false;
        }
        try {
            return Integer.parseInt(params.get(SKIP_ANSWER_DYNAMIC_PARAM)) >= index;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
```

Then update the callers:

Run: `sed -i '' 's/\.scipAnswer(/.skipAnswer(/g; s/canScipAnswer(/canSkipAnswer(/g' src/main/java/io/github/baevkir/sessionbot/render/*.java src/main/java/io/github/baevkir/sessionbot/internal/*.java && grep -rn -i scip src/main`
Expected: no output.

- [ ] **Step 4: Introduce `ParameterOption`.** Create `src/main/java/io/github/baevkir/sessionbot/render/ParameterOption.java`:

```java
package io.github.baevkir.sessionbot.render;

/** A predefined answer a renderer may offer as a button: {@code value} is sent back, {@code label} is shown. */
public record ParameterOption(String value, String label) {
}
```

Delete the old class: `git rm -q src/main/java/io/github/baevkir/sessionbot/render/Option.java`.

In `render/ParameterRequest.java` change `private final List<Option> options;` to `private final List<ParameterOption> options;`.

In `internal/ParameterDescriptor.java`: replace the import `io.github.baevkir.sessionbot.render.Option` with `io.github.baevkir.sessionbot.render.ParameterOption`, change the field to `private List<ParameterOption> options;`, and replace the method `getRenderingOptions` with

```java
    private static List<ParameterOption> getRenderingOptions(RenderingOption[] renderingOptions) {
        return Arrays.stream(renderingOptions)
            .map(option -> new ParameterOption(option.value(),
                StringUtils.isNotBlank(option.displayValue()) ? option.displayValue() : option.value()))
            .toList();
    }
```

(remove the now-unused `java.util.Optional` and `java.util.stream.Collectors` imports if nothing else uses them).

In `render/TextParameterRenderer.java` replace

```java
                            .text(labels.resolve(option.getValue(), parameterRequest.getContext()))
                            .callbackData(CommandBuilder.create().addAnswer(option.getKey()).build())
```

with

```java
                            .text(labels.resolve(option.label(), parameterRequest.getContext()))
                            .callbackData(CommandBuilder.create().addAnswer(option.value()).build())
```

- [ ] **Step 5: Annotation targets.** In `annotation/Parameter.java` change `@Target({ElementType.PARAMETER, ElementType.FIELD})` to `@Target(ElementType.PARAMETER)`. Replace the whole of `annotation/Rendering.java` with

```java
package io.github.baevkir.sessionbot.annotation;

import io.github.baevkir.sessionbot.render.ParameterRenderer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** How a {@link Parameter} is prompted for; only usable as a member of {@code @Parameter}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Rendering {
    String name() default "defaultParameterRenderer";
    Class<? extends ParameterRenderer> type() default ParameterRenderer.class;
    RenderingOption[] options() default {};
}
```

and `annotation/RenderingOption.java` with

```java
package io.github.baevkir.sessionbot.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A predefined answer offered as a button; only usable inside {@link Rendering#options()}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface RenderingOption {
    String value();
    String displayValue() default "";
}
```

- [ ] **Step 6: Update the remaining tests.**

Run:

```bash
cd src/test/java/io/github/baevkir/sessionbot
sed -i '' 's/#scipAnswer:/#skip:/g; s/"scipAnswer"/"skip"/g; s/scipAnswer=/skip=/g; s/scip/skip/g; s/Scip/Skip/g' \
  DynamicParametersTest.java internal/CommandsDispatcherTest.java internal/MessageDescriptorTest.java CommandBuilderTest.java
sed -i '' 's/\.commandApproved()/.hasParam("approved")/g; s/\.getInitiator()/.getParam("initiator")/g' \
  DynamicParametersTest.java CommandContextTest.java UpdateWrapperTest.java fixtures/EchoCommand.java
cd -
grep -rn -i "scip\|commandApproved\|getInitiator\|setInitiator" src/test
```

Expected: the final `grep` prints nothing.

In `DynamicParametersTest.emptyHasNoParams` the two lines now read `params.hasParam("approved")` and `params.getParam("initiator")` — keep them (they still assert an empty map has neither).

- [ ] **Step 7: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS, 0 failures (247 − 2 removed builder tests + 1 new descriptor test = 246).

- [ ] **Step 8: Commit**

```bash
git add -A src
git commit -m "refactor: rename skip answer, drop approved/initiator flags, add ParameterOption"
```

---

### Task 3: `CommandContext` becomes a read-only interface over `internal.ConversationState`

**Files:**
- Rewrite: `src/main/java/io/github/baevkir/sessionbot/CommandContext.java`
- Create: `src/main/java/io/github/baevkir/sessionbot/internal/ConversationState.java`
- Modify: `internal/TelegramUpdateHandler.java`, `internal/RegisteredCommand.java`, `internal/DispatcherBotCommand.java`, `internal/HelpCommand.java`, `internal/HelpGuardDeniedHandler.java`, `internal/CommandsFactory.java`, `autoconfigure/SessionBotAutoConfiguration.java`
- Test: rewrite `CommandContextTest.java`; create `internal/ConversationStateTest.java`; update `fixtures/Fixtures.java`, `guard/GuardContextTest.java`, `internal/HelpCommandTest.java`, `internal/MethodMatcherTest.java`, `internal/CommandsDispatcherTest.java`, `internal/ProxiedCommandTest.java`, `render/TimeParameterRendererTest.java`

**Interfaces:**
- Consumes: `UpdateWrapper` (`wrap`, `isCommand`, `getChatId`, `getFrom`, `getCommand`, `getAnswers`, `getCallbackMessage`, `getDynamicParams`), `ContextState`, `DynamicParameters.empty()`.
- Produces:
  - `interface CommandContext { static CommandContext of(Update); String getChatId(); User getUser(); String getCommand(); List<String> getAnswers(); UpdateWrapper getCommandUpdate(); Optional<UpdateWrapper> getCurrentUpdate(); Optional<MaybeInaccessibleMessage> getCallbackMessage(); DynamicParameters getDynamicParams(); }`
  - `final class ConversationState implements CommandContext` with `static empty()`, `static forCommand(UpdateWrapper)`, `static forBareUpdate(UpdateWrapper)`, `boolean hasCommand()`, `ContextState getState()`, `ConversationState startProgress()`, `ConversationState close()`, `ConversationState addAnswer(String)`, `ConversationState addUpdate(UpdateWrapper)`, `ConversationState addQuestionMessage(Message)`, `List<Message> getQuestionMessages()`, `List<UpdateWrapper> getUpdates()`, `List<String> getPendingArguments()`.
  - `RegisteredCommand.process(ConversationState)`; `HelpCommand.render(CommandContext)`; `CommandsFactory.getHelpCommand(): HelpCommand`; `HelpGuardDeniedHandler(HelpCommand)`.

- [ ] **Step 1: Write the failing tests.** Replace `src/test/java/io/github/baevkir/sessionbot/CommandContextTest.java` with

```java
package io.github.baevkir.sessionbot;

import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.internal.ConversationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandContextTest {

    @Test
    @DisplayName("of(Update) opens a command context for a command")
    void ofCommandUpdate() {
        var context = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy"));

        assertThat(context.getCommand()).isEqualTo("order");
        assertThat(context.getAnswers()).containsExactly("buy");
        assertThat(context.getChatId()).isEqualTo(String.valueOf(Fixtures.CHAT_ID));
        assertThat(context.getUser().getUserName()).isEqualTo("tester");
        assertThat(context.getCurrentUpdate()).isEmpty();
    }

    @Test
    @DisplayName("of(Update) opens a bare context for anything else")
    void ofBareUpdate() {
        var context = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello"));

        assertThat(context.getCommand()).isNull();
        assertThat(context.getAnswers()).containsExactly("hello");
        assertThat(context.getCurrentUpdate()).isPresent();
    }

    @Test
    @DisplayName("a bare context exposes its update as the command update, so getCommandUpdate().getFrom() works")
    void bareContextExposesItsUpdateAsCommandUpdate() {
        var context = CommandContext.of(Fixtures.documentUpdate(1, Fixtures.CHAT_ID, 100, "data.csv"));

        assertThat(context.getCommandUpdate()).isNotNull();
        assertThat(context.getCommandUpdate().getFrom().getUserName()).isEqualTo("tester");
        assertThat(context.getUser().getUserName()).isEqualTo("tester");
    }

    @Test
    @DisplayName("getCallbackMessage prefers the latest update's tapped message")
    void callbackMessageFromCurrentUpdate() {
        var context = ConversationState.forCommand(Fixtures.buttonCommandWrapper("/order"))
            .addUpdate(Fixtures.answerWrapper(2, 555, "book"));

        assertThat(context.getCallbackMessage()).get()
            .extracting(message -> message.getMessageId()).isEqualTo(555);
    }

    @Test
    @DisplayName("getCallbackMessage falls back to the command's tapped message")
    void callbackMessageFallsBackToCommand() {
        var context = ConversationState.forCommand(Fixtures.buttonCommandWrapper("/order"))
            .addUpdate(Fixtures.wrap(Fixtures.messageUpdate(2, Fixtures.CHAT_ID, 101, "typed")));

        assertThat(context.getCallbackMessage()).get()
            .extracting(message -> message.getMessageId()).isEqualTo(100);
    }

    @Test
    @DisplayName("getCallbackMessage is empty for a typed command")
    void noCallbackMessageForTypedCommand() {
        assertThat(CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order")).getCallbackMessage())
            .isEmpty();
    }

    @Test
    @DisplayName("getAnswers is unmodifiable")
    void answersAreUnmodifiable() {
        var context = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy"));

        assertThatThrownBy(() -> context.getAnswers().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
```

Create `src/test/java/io/github/baevkir/sessionbot/internal/ConversationStateTest.java`:

```java
package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationStateTest {

    @Nested
    @DisplayName("creation guards")
    class Guards {

        @Test
        void forCommandRejectsNonCommandUpdate() {
            var answer = Fixtures.answerWrapper(2, 100, "buy&book");
            assertThatThrownBy(() -> ConversationState.forCommand(answer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Context should be created only for command.");
        }

        @Test
        void forBareUpdateRejectsCommandUpdate() {
            assertThatThrownBy(() -> ConversationState.forBareUpdate(Fixtures.commandWrapper("/order")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A command opens a command context");
        }

        @Test
        void addUpdateRejectsCommandUpdate() {
            var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
            var command = Fixtures.commandWrapper("/order");
            assertThatThrownBy(() -> context.addUpdate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Command should create new context");
        }
    }

    @Test
    @DisplayName("empty() has no command and no chat; forCommand() is open")
    void emptyAndCommandState() {
        var empty = ConversationState.empty();
        assertThat(empty.hasCommand()).isFalse();
        assertThat(empty.getChatId()).isNull();
        assertThat(empty.getUser()).isNull();
        assertThat(empty.getDynamicParams().isEmpty()).isTrue();

        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
        assertThat(context.hasCommand()).isTrue();
        assertThat(context.getState()).isEqualTo(ContextState.open);
        assertThat(context.getCommand()).isEqualTo("order");
    }

    @Test
    @DisplayName("getAnswers merges command answers and pending arguments")
    void answersMerge() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order?buy"));
        assertThat(context.getAnswers()).containsExactly("buy");

        context.addUpdate(Fixtures.answerWrapper(2, 100, "book"));
        assertThat(context.getAnswers()).containsExactly("buy", "book");
        assertThat(context.getPendingArguments()).containsExactly("book");
    }

    @Test
    @DisplayName("lifecycle open -> progress -> close")
    void lifecycle() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
        assertThat(context.getState()).isEqualTo(ContextState.open);
        context.startProgress();
        assertThat(context.getState()).isEqualTo(ContextState.progress);
        context.close();
        assertThat(context.getState()).isEqualTo(ContextState.close);
    }

    @Test
    @DisplayName("refreshContext rebuild keeps command answers and re-applies the latest update")
    void refreshRebuildSemantics() {
        var command = Fixtures.commandWrapper("/order?buy");
        var refreshUpdate = Fixtures.answerWrapper(2, 100, "book#refreshContext");
        var rebuilt = ConversationState.forCommand(command).addUpdate(refreshUpdate);

        assertThat(rebuilt.getCommand()).isEqualTo("order");
        assertThat(rebuilt.getAnswers()).containsExactly("buy", "book");
        assertThat(rebuilt.getDynamicParams().needRefreshContext()).isTrue();
    }

    @Test
    @DisplayName("getDynamicParams reads the current update, falling back to the command update")
    void dynamicParamsFallback() {
        var context = ConversationState.forCommand(Fixtures.buttonCommandWrapper("/order#approved"));
        assertThat(context.getDynamicParams().hasParam("approved")).isTrue();

        context.addUpdate(Fixtures.answerWrapper(2, 100, "book#initiator:alice"));
        assertThat(context.getDynamicParams().getParam("initiator")).isEqualTo("alice");
    }

    @Test
    @DisplayName("addQuestionMessage rejects null and records the message")
    void questionMessages() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
        assertThatThrownBy(() -> context.addQuestionMessage(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Message is null");
        var message = Fixtures.message(Fixtures.CHAT_ID, 555, "question");
        context.addQuestionMessage(message);
        assertThat(context.getQuestionMessages()).containsExactly(message);
    }

    @Test
    @DisplayName("a bare state carries its update as both opening and current update")
    void bareState() {
        var update = Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello"));
        var context = ConversationState.forBareUpdate(update);

        assertThat(context.hasCommand()).isFalse();
        assertThat(context.getCommandUpdate()).isSameAs(update);
        assertThat(context.getCurrentUpdate()).containsSame(update);
        assertThat(context.getChatId()).isEqualTo(String.valueOf(Fixtures.CHAT_ID));
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `mvn -o -q test -Dtest='CommandContextTest,ConversationStateTest'`
Expected: compilation failure — `ConversationState` does not exist, `CommandContext.of` does not exist.

- [ ] **Step 3: Write `CommandContext`.** Replace `src/main/java/io/github/baevkir/sessionbot/CommandContext.java` with

```java
package io.github.baevkir.sessionbot;

import io.github.baevkir.sessionbot.internal.ConversationState;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of one chat's conversation with the bot: the update that opened it — a command, or a
 * bare text, document or contact outside any command — the answers collected so far and the latest
 * update. Commands, guards, handlers and renderers receive it; only the library advances it.
 */
public interface CommandContext {

    /** A context for a single update: a command opens a command context, anything else a bare one. For tests and out-of-band use. */
    static CommandContext of(Update update) {
        var wrapper = UpdateWrapper.wrap(update);
        return wrapper.isCommand() ? ConversationState.forCommand(wrapper) : ConversationState.forBareUpdate(wrapper);
    }

    String getChatId();

    /** The sender of the opening update, as the {@link AuthInterceptor} left it; {@code null} when it has none. */
    User getUser();

    /** The command name, or {@code null} for a bare update. */
    String getCommand();

    /** The answers collected so far, including those the latest update carries; unmodifiable. */
    List<String> getAnswers();

    /** The update that opened the context: the command, or the bare update itself. */
    UpdateWrapper getCommandUpdate();

    /** The latest update of the conversation. */
    Optional<UpdateWrapper> getCurrentUpdate();

    /** The message whose inline button was tapped: the latest update's, else the opening update's. */
    Optional<MaybeInaccessibleMessage> getCallbackMessage();

    DynamicParameters getDynamicParams();
}
```

- [ ] **Step 4: Write `ConversationState`.** Create `src/main/java/io/github/baevkir/sessionbot/internal/ConversationState.java`:

```java
package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.CommandContext;
import io.github.baevkir.sessionbot.DynamicParameters;
import io.github.baevkir.sessionbot.UpdateWrapper;
import org.springframework.util.Assert;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The library's mutable side of a {@link CommandContext}: one chat's fold state and its
 * open &rarr; progress &rarr; close lifecycle. A chat's updates are folded strictly one at a time, so
 * plain collections suffice.
 */
public final class ConversationState implements CommandContext {

    private final UpdateWrapper openingUpdate;
    private final boolean command;
    private final List<UpdateWrapper> updates = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private final List<Message> questionMessages = new ArrayList<>();
    private ContextState state = ContextState.open;

    private ConversationState(UpdateWrapper openingUpdate, boolean command) {
        this.openingUpdate = openingUpdate;
        this.command = command;
    }

    /** The seed of a chat's fold, before any update arrived. */
    public static ConversationState empty() {
        return new ConversationState(null, false);
    }

    public static ConversationState forCommand(UpdateWrapper commandUpdate) {
        Assert.isTrue(commandUpdate.isCommand(), "Context should be created only for command.");
        var conversation = new ConversationState(commandUpdate, true);
        conversation.answers.addAll(commandUpdate.getAnswers());
        return conversation;
    }

    /** A context for one update outside any command: plain text, a document, a contact, a stray button tap. */
    public static ConversationState forBareUpdate(UpdateWrapper update) {
        Assert.isTrue(!update.isCommand(), "A command opens a command context");
        var conversation = new ConversationState(update, false);
        conversation.updates.add(update);
        return conversation;
    }

    @Override
    public String getChatId() {
        return openingUpdate == null ? null : openingUpdate.getChatId();
    }

    @Override
    public User getUser() {
        return openingUpdate == null ? null : openingUpdate.getFrom();
    }

    @Override
    public String getCommand() {
        return command ? openingUpdate.getCommand() : null;
    }

    @Override
    public List<String> getAnswers() {
        var result = new ArrayList<>(answers);
        result.addAll(getPendingArguments());
        return Collections.unmodifiableList(result);
    }

    @Override
    public UpdateWrapper getCommandUpdate() {
        return openingUpdate;
    }

    @Override
    public Optional<UpdateWrapper> getCurrentUpdate() {
        return updates.isEmpty() ? Optional.empty() : Optional.of(updates.getLast());
    }

    @Override
    public Optional<MaybeInaccessibleMessage> getCallbackMessage() {
        return getCurrentUpdate().flatMap(UpdateWrapper::getCallbackMessage)
            .or(() -> Optional.ofNullable(openingUpdate).flatMap(UpdateWrapper::getCallbackMessage));
    }

    @Override
    public DynamicParameters getDynamicParams() {
        return getCurrentUpdate().or(() -> Optional.ofNullable(openingUpdate))
            .map(UpdateWrapper::getDynamicParams)
            .orElseGet(DynamicParameters::empty);
    }

    public boolean hasCommand() {
        return command;
    }

    public ContextState getState() {
        return state;
    }

    public ConversationState startProgress() {
        state = ContextState.progress;
        return this;
    }

    public ConversationState close() {
        state = ContextState.close;
        return this;
    }

    public ConversationState addAnswer(String answer) {
        answers.add(answer);
        return this;
    }

    public ConversationState addUpdate(UpdateWrapper update) {
        Assert.isTrue(!update.isCommand(), "Command should create new context");
        updates.add(update);
        return this;
    }

    public ConversationState addQuestionMessage(Message message) {
        Objects.requireNonNull(message, "Message is null");
        questionMessages.add(message);
        return this;
    }

    public List<Message> getQuestionMessages() {
        return Collections.unmodifiableList(questionMessages);
    }

    public List<UpdateWrapper> getUpdates() {
        return Collections.unmodifiableList(updates);
    }

    /** The answers the latest update carries, not yet folded into {@link #getAnswers()}'s committed part. */
    public List<String> getPendingArguments() {
        return getCurrentUpdate().map(UpdateWrapper::getAnswers).orElse(List.of());
    }
}
```

- [ ] **Step 5: Switch the command SPI to `ConversationState`.** In `internal/RegisteredCommand.java` change the `process` declaration and its javadoc to

```java
    /**
     * Runs one dispatch step of this command, advancing the conversation.
     *
     * @param conversation the chat's conversation state
     * @return the messages to send
     */
    Publisher<? extends PartialBotApiMethod<?>> process(ConversationState conversation);
```

and replace the `CommandContext` import with nothing (the type is in the same package). In `internal/DispatcherBotCommand.java` change the method signature to `public Publisher<? extends PartialBotApiMethod<?>> process(ConversationState commandContext) {` and remove the `CommandContext` import if unused.

In `internal/HelpCommand.java` replace the method `process(CommandContext commandContext)` with

```java
    @Override
    public Publisher<? extends PartialBotApiMethod<?>> process(ConversationState conversation) {
        return render(conversation);
    }

    /** The help message for {@code commandContext}'s caller, listing only commands their guards permit. */
    public Publisher<? extends PartialBotApiMethod<?>> render(CommandContext commandContext) {
        var userName = userName(commandContext);
        return Flux.fromIterable(botCommands)
            .filter(Predicate.not(RegisteredCommand::hidden))
            .concatMap(botCommand -> CommandGuards.permits(botCommand, GuardContext.of(commandContext, botCommand.getCommandIdentifier()))
                .filter(Boolean::booleanValue)
                .map(permitted -> botCommand))
            .collectList()
            .map(visibleCommands -> {
                StringBuilder helpMessageBuilder = new StringBuilder("<b>").append(labels.helpTitle(commandContext)).append("</b>\n");
                helpMessageBuilder.append(labels.helpIntro(commandContext)).append("\n\n");
                helpMessageBuilder.append(getCommandPresenter(this, userName)).append("\n\n");
                visibleCommands.forEach(botCommand -> helpMessageBuilder.append(getCommandPresenter(botCommand, userName)).append("\n\n"));
                return SendMessage.builder()
                    .chatId(commandContext.getChatId())
                    .parseMode(ParseMode.HTML)
                    .text(helpMessageBuilder.toString())
                    .build();
            });
    }
```

In `internal/HelpGuardDeniedHandler.java` replace the field, constructor and method with

```java
    private final HelpCommand helpCommand;

    public HelpGuardDeniedHandler(HelpCommand helpCommand) {
        this.helpCommand = helpCommand;
    }

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> onDenied(CommandContext context) {
        return helpCommand.render(context);
    }
```

In `internal/CommandsFactory.java` change `public final RegisteredCommand getHelpCommand()` to `public final HelpCommand getHelpCommand()`.

- [ ] **Step 6: Rewire `TelegramUpdateHandler`.** In `internal/TelegramUpdateHandler.java`:

Replace the default guard-denial in the 7-argument constructor

```java
            permitCommands, context -> commandsFactory.getHelpCommand().process(context));
```

with

```java
            permitCommands, new HelpGuardDeniedHandler(commandsFactory.getHelpCommand()));
```

Replace `handleUpdates`, `fold`, `dispatch`, `dispatchOutsideCommand` and `dispatchBare` with

```java
    public Flux<PartialBotApiMethod<?>> handleUpdates(Flux<UpdateWrapper> updates) {
        Assert.notNull(updates, "Updates is null.");
        return updates
            .scanWith(ConversationState::empty, this::fold)
            .skip(1) // drop the empty seed context emitted before any update
            .concatMap(context ->
                dispatch(context)
                    .collectList()
                    .map(results -> new DispatchOutcome(context, results)))
            .takeUntil(outcome -> ContextState.close.equals(outcome.context().getState()))
            .concatMapIterable(DispatchOutcome::results);
    }

    private ConversationState fold(ConversationState context, UpdateWrapper update) {
        if (update.isCommand()) {
            return ConversationState.forCommand(update);
        }
        if (!context.hasCommand()) {
            // Each update outside a command gets its own context, so they never pile up in one.
            return ConversationState.forBareUpdate(update);
        }
        if (update.getDynamicParams().needRefreshContext()) {
            return ConversationState.forCommand(context.getCommandUpdate()).addUpdate(update);
        }
        return context.addUpdate(update);
    }

    private Flux<PartialBotApiMethod<?>> dispatch(ConversationState context) {
        if (!context.hasCommand()) {
            return dispatchOutsideCommand(context)
                .orElseGet(() -> Flux.<PartialBotApiMethod<?>>from(commandsFactory.getHelpCommand().process(context))
                    .doOnNext(messageExecutor::execute));
        }
        log.debug("Dispatching command '{}' in chat {} (state={})", context.getCommand(), context.getChatId(), context.getState());
        Mono<Boolean> authorized = permitCommands.contains(context.getCommand())
                ? Mono.just(true)
                : authInterceptor.intercept(context);
        return authorized
            .<PartialBotApiMethod<?>>flatMapMany(allowed -> {
                if (!allowed) {
                    log.debug("Auth rejected for command '{}' in chat {} (user={})", context.getCommand(), context.getChatId(), userName(context));
                    return Flux.error(new BotAuthException(context, "User " + userName(context) + " is unauthorized to use bot."));
                }
                var command = commandsFactory.getCommand(context.getCommand());
                return CommandGuards.permits(command, GuardContext.of(context, command.getCommandIdentifier()))
                    .<PartialBotApiMethod<?>>flatMapMany(permitted -> {
                        if (!permitted) {
                            log.debug("Guard denied command '{}' in chat {}", context.getCommand(), context.getChatId());
                            context.close();
                            return Flux.<PartialBotApiMethod<?>>from(guardDeniedHandler.onDenied(context));
                        }
                        return Flux.<PartialBotApiMethod<?>>from(command.process(context));
                    });
            })
            .doOnNext(message -> {
                var result = messageExecutor.execute(message);
                if (result instanceof Message resultMessage && ContextState.progress.equals(context.getState())) {
                    context.addQuestionMessage(resultMessage);
                }
            });
    }

    private Optional<Flux<PartialBotApiMethod<?>>> dispatchOutsideCommand(ConversationState context) {
        var update = context.getCommandUpdate();
        var contactDispatch = update.getContact()
            .flatMap(contact -> contactHandlers.stream()
                .filter(handler -> handler.supports(contact))
                .findFirst()
                .map(handler -> dispatchBare(context, "contact " + contact.getUserId(),
                    bareContext -> handler.handle(bareContext, contact))));
        if (contactDispatch.isPresent()) {
            return contactDispatch;
        }
        var documentDispatch = update.getDocument()
            .flatMap(document -> documentHandlers.stream()
                .filter(handler -> handler.supports(document))
                .findFirst()
                .map(handler -> dispatchBare(context, "document '" + document.getFileName() + "'",
                    bareContext -> handler.handle(bareContext, document))));
        if (documentDispatch.isPresent()) {
            return documentDispatch;
        }
        return messageText(update)
            .flatMap(text -> textHandlers.stream()
                .findFirst()
                .map(handler -> dispatchBare(context, "text", bareContext -> handler.handle(bareContext, text))));
    }

    private Flux<PartialBotApiMethod<?>> dispatchBare(ConversationState context, String description,
                                                     Function<CommandContext, Publisher<PartialBotApiMethod<?>>> handling) {
        log.debug("Dispatching {} in chat {}", description, context.getChatId());
        return authInterceptor.intercept(context)
            .<PartialBotApiMethod<?>>flatMapMany(authorized -> {
                if (!authorized) {
                    return Flux.error(new BotAuthException(context, "User " + userName(context) + " is unauthorized to use bot."));
                }
                return handling.apply(context);
            })
            .onErrorMap(error -> error instanceof BotCommandException || error instanceof BotAuthException
                ? error
                : new BotCommandException(context, error))
            .doOnNext(messageExecutor::execute);
    }

    private static String userName(CommandContext context) {
        return context.getUser() != null ? context.getUser().getUserName() : "unknown";
    }
```

and change the record to `private record DispatchOutcome(ConversationState context, List<PartialBotApiMethod<?>> results) {`. Keep `messageText` as is. Remove imports that are no longer used (`java.util.Objects` stays for the constructor).

- [ ] **Step 7: `HelpCommand` and `CommandsFactory` are no longer overridable beans.** In `autoconfigure/SessionBotAutoConfiguration.java` delete the `@ConditionalOnMissingBean` line directly above `public HelpCommand helpCommand(` and the one above `public CommandsFactory commandsFactory(`.

- [ ] **Step 8: Update the remaining tests.**

Run:

```bash
cd src/test/java/io/github/baevkir/sessionbot
sed -i '' 's/CommandContext\.create(/ConversationState.forCommand(/g; s/CommandContext\.forUpdate(/ConversationState.forBareUpdate(/g; s/CommandContext\.empty()/ConversationState.empty()/g' \
  fixtures/Fixtures.java guard/GuardContextTest.java internal/HelpCommandTest.java internal/MethodMatcherTest.java \
  internal/CommandsDispatcherTest.java internal/ProxiedCommandTest.java render/TimeParameterRendererTest.java
sed -i '' 's/public static CommandContext contextFor(/public static ConversationState contextFor(/' fixtures/Fixtures.java
sed -i '' 's/private static CommandContext contextWith(/private static ConversationState contextWith(/' internal/MethodMatcherTest.java
sed -i '' 's/private static CommandContext ctx(/private static ConversationState ctx(/' internal/CommandsDispatcherTest.java
sed -i '' 's/private static CommandContext commandContext()/private static ConversationState commandContext()/; s/private static CommandContext tapped(/private static ConversationState tapped(/' render/TimeParameterRendererTest.java
python3 - fixtures/Fixtures.java guard/GuardContextTest.java render/TimeParameterRendererTest.java <<'PY'
import re, sys
line = "import io.github.baevkir.sessionbot.internal.ConversationState;\n"
for path in sys.argv[1:]:
    text = open(path).read()
    if line not in text:
        first_import = re.search(r"^import ", text, flags=re.M).start()
        text = text[:first_import] + line + text[first_import:]
        open(path, "w").write(text)
PY
cd -
```

Then, by hand:
- `guard/GuardContextTest.anEmptyContextFallsBackToTheCurrentUpdate`: replace `ConversationState.empty()\n            .addUpdate(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello")))` with `ConversationState.forBareUpdate(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello")))`, and rename the test to `aBareContextCarriesItsSender`.
- `internal/HelpCommandTest.guardsSeeTheSenderEvenWhenHelpIsTheFallbackForPlainText`: same replacement for `plainText`.
- Drop now-unused `import io.github.baevkir.sessionbot.CommandContext;` lines the compiler flags as unused (they compile either way).

- [ ] **Step 9: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS, 0 failures (the old `CommandContextTest`'s 8 tests are replaced by 7 in the new one and 10 in `ConversationStateTest`).

- [ ] **Step 10: Commit**

```bash
git add -A src
git commit -m "feat!: make CommandContext a read-only interface over internal ConversationState"
```

---

### Task 4: `/help` closes its conversation

**Files:**
- Modify: `src/main/java/io/github/baevkir/sessionbot/internal/HelpCommand.java`
- Test: `src/test/java/io/github/baevkir/sessionbot/internal/TelegramUpdateHandlerTest.java`, `internal/HelpCommandTest.java`

**Interfaces:**
- Consumes: `ConversationState.close()`, `HelpCommand.render(CommandContext)` (Task 3).

- [ ] **Step 1: Write the failing tests.** Add to `internal/HelpCommandTest.java`:

```java
    @Test
    void processClosesTheConversation() {
        var help = new HelpCommand(List.of(), Fixtures.labels(Locale.ENGLISH));
        var conversation = Fixtures.contextFor("/help");

        StepVerifier.create(Flux.from(help.process(conversation))).expectNextCount(1).verifyComplete();

        assertThat(conversation.getState()).isEqualTo(ContextState.close);
    }
```

Add to `internal/TelegramUpdateHandlerTest.java` (it already builds handlers with `handler(...)`, uses `ALLOW`, `echoText(...)`, `Fixtures`, `StepVerifier`; add imports `java.time.Duration` and `java.util.concurrent.CopyOnWriteArrayList` if missing):

```java
    @DisplayName("/help completes the chat's stream at once instead of holding it until the idle TTL")
    @Test
    void helpCompletesTheChatStream() {
        var handler = handler(ALLOW, List.of(), List.of());
        var updates = Flux.concat(
            Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/help"))),
            Flux.<UpdateWrapper>never());

        StepVerifier.create(handler.handleUpdates(updates))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @DisplayName("consecutive bare updates are each dispatched and do not complete the chat's stream")
    @Test
    void consecutiveBareUpdatesAreAllDispatched() {
        var received = new CopyOnWriteArrayList<String>();
        TextHandler collecting = (context, text) -> {
            received.add(text);
            return Flux.<PartialBotApiMethod<?>>just(SendMessage.builder().chatId(context.getChatId()).text("got " + text).build());
        };
        var handler = handler(ALLOW, List.of(), List.of(collecting));
        var updates = Flux.concat(
            Flux.just(
                Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "first")),
                Fixtures.wrap(Fixtures.messageUpdate(2, Fixtures.CHAT_ID, 101, "second")),
                Fixtures.wrap(Fixtures.messageUpdate(3, Fixtures.CHAT_ID, 102, "third"))),
            Flux.<UpdateWrapper>never());

        StepVerifier.create(handler.handleUpdates(updates))
            .expectNextCount(3)
            .expectNoEvent(Duration.ofMillis(200))
            .thenCancel()
            .verify(Duration.ofSeconds(5));
        assertThat(received).containsExactly("first", "second", "third");
    }
```

(`handler(auth, documentHandlers, textHandlers)` is the existing helper the `bareTextRoutesToTheTextHandlerVerbatim` test already calls as `handler(ALLOW, List.of(), List.of(echoText(received)))`. Add imports `org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod` and `io.github.baevkir.sessionbot.handler.TextHandler` if missing.)

- [ ] **Step 2: Run to see the help tests fail**

Run: `mvn -o -q test -Dtest='HelpCommandTest,TelegramUpdateHandlerTest'`
Expected: `processClosesTheConversation` fails (state is `open`), `helpCompletesTheChatStream` fails on timeout (no completion). `consecutiveBareUpdatesAreAllDispatched` already passes — it pins Task 3's fold and guards against a regression here.

- [ ] **Step 3: Close in `HelpCommand.process`**

```java
    @Override
    public Publisher<? extends PartialBotApiMethod<?>> process(ConversationState conversation) {
        conversation.close();
        return render(conversation);
    }
```

- [ ] **Step 4: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "fix: close the conversation after /help so the chat's stream is released"
```

---

### Task 5: Key i18n on `User`

**Files:**
- Modify: `i18n/LocaleProvider.java`, `i18n/ConfiguredLocaleProvider.java`, `i18n/BotLabels.java`
- Modify: `internal/RegisteredCommand.java`, `internal/HelpCommand.java`, `internal/DispatcherBotCommand.java`, `internal/CommandMenus.java`, `menu/CommandMenuService.java`
- Test: `i18n/BotLabelsTest.java`, `i18n/ConfiguredLocaleProviderTest.java`, `menu/CommandMenuServiceTest.java`

**Interfaces:**
- Produces: `LocaleProvider.getLocale(User user)`; `BotLabels.resolve(String, CommandContext)`, `BotLabels.resolve(String, User)`, `BotLabels.helpDescription(User)`; `RegisteredCommand.getDescription(User)`; `CommandMenus.toBotCommands(List<RegisteredCommand>, User)`.

- [ ] **Step 1: Write the failing tests.** In `i18n/BotLabelsTest.java` replace the test `resolveByUserName` with

```java
    @DisplayName("resolve(text, user) and resolve(text, context) hand the User to the LocaleProvider")
    @Test
    void resolveByUser() {
        var ms = new ResourceBundleMessageSource();
        ms.setBasenames("sessionbot-labels");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        var labels = new BotLabels(ms, user ->
            user != null && "bob".equals(user.getUserName()) ? Locale.forLanguageTag("uk") : Locale.ENGLISH);

        assertThat(labels.resolve("{help.title}", Fixtures.user("bob"))).isEqualTo("Довідка");
        assertThat(labels.resolve("{help.title}", Fixtures.user("alice"))).isEqualTo("Help");
        assertThat(labels.resolve("{help.title}", (User) null)).isEqualTo("Help");
        var bobsContext = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order"));
        assertThat(labels.resolve("{help.title}", bobsContext)).isEqualTo("Help"); // Fixtures' sender is "tester"
    }
```

Replace every remaining `(String) null` in `BotLabelsTest` with `(User) null`, and add imports `io.github.baevkir.sessionbot.CommandContext`, `io.github.baevkir.sessionbot.fixtures.Fixtures`, `org.telegram.telegrambots.meta.api.objects.User`. In `i18n/ConfiguredLocaleProviderTest.java` replace `provider.getLocale("alice")` with `provider.getLocale(Fixtures.user("alice"))` (import `Fixtures`). In `menu/CommandMenuServiceTest.java` replace `verify(admin).getDescription("tester");` with

```java
        verify(admin).getDescription(argThat(user -> user != null && "tester".equals(user.getUserName())));
```

and add `import static org.mockito.ArgumentMatchers.argThat;`.

- [ ] **Step 2: Run to see them fail**

Run: `mvn -o -q test -Dtest='BotLabelsTest,ConfiguredLocaleProviderTest,CommandMenuServiceTest'`
Expected: compilation failure — `resolve(String, User)` and `getLocale(User)` do not exist.

- [ ] **Step 3: Change the SPI and `BotLabels`.** Replace `i18n/LocaleProvider.java` with

```java
package io.github.baevkir.sessionbot.i18n;

import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Locale;

/** Resolves the bot's language for a caller. */
public interface LocaleProvider {
    /**
     * @param user the Telegram user the text is for; {@code null} for bot-wide text (the default command
     *             menu, out-of-band messages with no recipient). Implementations must tolerate null.
     */
    Locale getLocale(User user);
}
```

In `i18n/ConfiguredLocaleProvider.java` change the method to `public Locale getLocale(User user) {` and import `org.telegram.telegrambots.meta.api.objects.User`.

In `i18n/BotLabels.java`: change `helpDescription` to

```java
    public String helpDescription(User user)           { return getForUser("help.description", user); }
```

replace the two `resolve` methods and the private helpers with

```java
    public String resolve(String text, CommandContext ctx) {
        return resolve(text, user(ctx));
    }

    /**
     * Resolve for a user directly — for out-of-band messages and menus that have no incoming
     * {@link CommandContext}. Pass {@code null} for the configured/bot-wide locale.
     */
    public String resolve(String text, User user) {
        if (text == null) {
            return null;
        }
        var trimmed = text.trim();
        if (trimmed.length() > 2 && trimmed.startsWith("{") && trimmed.endsWith("}")
                && trimmed.indexOf('}') == trimmed.length() - 1) {
            var inner = trimmed.substring(1, trimmed.length() - 1);
            int sep = inner.indexOf(':');
            var code = (sep >= 0 ? inner.substring(0, sep) : inner).trim();
            var fallback = sep >= 0 ? inner.substring(sep + 1) : text;
            return messages.getMessage(code, null, fallback, localeProvider.getLocale(user));
        }
        return text;
    }

    private String get(String key, CommandContext ctx, Object... args) {
        return getForUser(key, user(ctx), args);
    }

    private String getForUser(String key, User user, Object... args) {
        return messages.getMessage(key, args, localeProvider.getLocale(user));
    }

    private static User user(CommandContext ctx) {
        return ctx == null ? null : ctx.getUser();
    }
```

Update the class javadoc's last sentence to "Locale comes from {@link LocaleProvider} for the caller (the {@link CommandContext}'s user; may be null)." Remove the imports `UpdateWrapper` and `java.util.Optional`.

- [ ] **Step 4: Thread `User` through descriptions and menus.**
  - `internal/RegisteredCommand.java`: `String getDescription(User user);` with javadoc `@param user the caller to localize for; {@code null} for the bot-wide language (the default command menu)`; import `org.telegram.telegrambots.meta.api.objects.User`.
  - `internal/HelpCommand.java`: `public String getDescription(User user) { return labels.helpDescription(user); }`; in `render` replace `var userName = userName(commandContext);` with `var user = commandContext.getUser();` and pass `user` to `getCommandPresenter`; change `getCommandPresenter(RegisteredCommand command, String userName)` to take `User user` and call `command.getDescription(user)`; delete the private `userName(CommandContext)` method and the now-unused `Optional` import.
  - `internal/DispatcherBotCommand.java`: `public String getDescription(User user) { return applicationContext.getBean(BotLabels.class).resolve(commandsDispatcher.getCommandDescription(), user); }`.
  - `internal/CommandMenus.java`: `toBotCommands(List<RegisteredCommand> commands, User user)` calling `command.getDescription(user)`; `defaultCommands` passes `null` — write it as `toBotCommands(..., (User) null)`.
  - `menu/CommandMenuService.java`: `.commands(CommandMenus.toBotCommands(permitted, user))`.

- [ ] **Step 5: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "feat!: resolve the bot's language from the Telegram User instead of the user name"
```

---

### Task 6: `TextHandler.supports` and consistent handler return types

**Files:**
- Modify: `handler/TextHandler.java`, `handler/DocumentHandler.java`, `handler/ContactHandler.java`, `internal/TelegramUpdateHandler.java`
- Test: `internal/TelegramUpdateHandlerTest.java`

**Interfaces:**
- Produces: `TextHandler.supports(String text)` (default `true`); all three handlers return `Publisher<? extends PartialBotApiMethod<?>>`.

- [ ] **Step 1: Write the failing tests.** Add to `internal/TelegramUpdateHandlerTest.java`:

```java
    @DisplayName("the first text handler that supports the text wins")
    @Test
    void firstSupportingTextHandlerWins() {
        var declining = new TextHandler() {
            @Override
            public boolean supports(String text) {
                return !text.startsWith("buy");
            }

            @Override
            public Publisher<PartialBotApiMethod<?>> handle(CommandContext context, String text) {
                return Flux.<PartialBotApiMethod<?>>just(SendMessage.builder().chatId(context.getChatId()).text("declining").build());
            }
        };
        TextHandler shopping = (context, text) -> Flux.just(SendMessage.builder().chatId(context.getChatId()).text("shopping").build());
        var handler = handler(ALLOW, List.of(), List.of(declining, shopping));

        StepVerifier.create(handler.handleUpdates(Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "buy milk")))))
            .assertNext(sent -> assertThat(((SendMessage) sent).getText()).isEqualTo("shopping"))
            .verifyComplete();
    }
```

(The anonymous `declining` handler deliberately declares the narrower `Publisher<PartialBotApiMethod<?>>` return type, pinning Review Focus 5. Add imports `org.reactivestreams.Publisher`, `org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod`, `io.github.baevkir.sessionbot.CommandContext`, `io.github.baevkir.sessionbot.handler.TextHandler` if missing.)

- [ ] **Step 2: Run to see it fail**

Run: `mvn -o -q test -Dtest=TelegramUpdateHandlerTest`
Expected: compilation failure — `supports` does not override anything.

- [ ] **Step 3: Change the handler interfaces.** Replace `handler/TextHandler.java` with

```java
package io.github.baevkir.sessionbot.handler;

import io.github.baevkir.sessionbot.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/**
 * Handles a plain text message sent outside any command flow. Implemented as Spring beans by the
 * host bot; the first handler (in bean order) whose {@link #supports} matches wins, and with none
 * matching the update falls through to the default help behavior. {@code text} is the message text
 * verbatim: unlike a command answer it is not split on {@code &} or {@code #}.
 */
public interface TextHandler {

    default boolean supports(String text) {
        return true;
    }

    Publisher<? extends PartialBotApiMethod<?>> handle(CommandContext context, String text);
}
```

In `handler/DocumentHandler.java` and `handler/ContactHandler.java` change the `handle` return type to `Publisher<? extends PartialBotApiMethod<?>>`.

In `internal/TelegramUpdateHandler.java`: in `dispatchOutsideCommand` replace

```java
        return messageText(update)
            .flatMap(text -> textHandlers.stream()
                .findFirst()
```

with

```java
        return messageText(update)
            .flatMap(text -> textHandlers.stream()
                .filter(handler -> handler.supports(text))
                .findFirst()
```

and in `dispatchBare` change the parameter to `Function<CommandContext, Publisher<? extends PartialBotApiMethod<?>>> handling` and the return inside `flatMapMany` to `return Flux.<PartialBotApiMethod<?>>from(handling.apply(context));`.

- [ ] **Step 4: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS (existing handlers in tests that declare `Publisher<PartialBotApiMethod<?>>` still compile).

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: let TextHandlers decline text and widen handler return types"
```

---

### Task 7: `permit-commands` defaults to empty

**Files:**
- Modify: `src/main/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotProperties.java`
- Test: `src/test/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotPropertiesTest.java` (create)

- [ ] **Step 1: Write the failing test** — create `SessionBotPropertiesTest.java`:

```java
package io.github.baevkir.sessionbot.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBotPropertiesTest {

    @Test
    void noCommandBypassesTheAuthInterceptorByDefault() {
        assertThat(new SessionBotProperties().getPermitCommands()).isEmpty();
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `mvn -o -q test -Dtest=SessionBotPropertiesTest`
Expected: FAIL — `Expecting empty but was: ["start"]`.

- [ ] **Step 3: Change the default** — replace

```java
    /**
     * Commands that run without consulting the {@code AuthInterceptor}. `/start` is here because a
     * person being invited has no access yet — the command itself decides whether to admit them.
     */
    private List<String> permitCommands = List.of("start");
```

with

```java
    /**
     * Commands that run without consulting the {@code AuthInterceptor} — typically an entry point such
     * as {@code start} that must run before the caller can be recognized. Empty by default.
     */
    private List<String> permitCommands = List.of();
```

- [ ] **Step 4: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS. (`TelegramUpdateHandlerAuthTest` passes its permit list explicitly, so it is unaffected; if any test relied on the property default, pass `permit-commands=start` to its context runner.)

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat!: no command bypasses the AuthInterceptor unless permit-commands says so"
```

---

### Task 8: Single-pass wire parser with a command addressee

**Files:**
- Rewrite: `src/main/java/io/github/baevkir/sessionbot/internal/MessageDescriptor.java`
- Modify: `internal/CommandConstants.java`, `UpdateWrapper.java`
- Test: `src/test/java/io/github/baevkir/sessionbot/internal/MessageDescriptorTest.java`, `UpdateWrapperTest.java`

**Interfaces:**
- Produces: `MessageDescriptor.getAddressee(): String` (null when absent or empty); `UpdateWrapper.getAddressee(): Optional<String>`; `CommandConstants.ADDRESSEE_SEPARATOR = "@"`.

- [ ] **Step 1: Write the failing tests.** Add to `internal/MessageDescriptorTest.java`:

```java
    @Nested
    @DisplayName("grammar")
    class Grammar {

        @Test
        void commandCarriesItsAddressee() {
            var descriptor = MessageDescriptor.parse("/order@MyBot?buy&book");
            assertThat(descriptor.getCommand()).isEqualTo("order");
            assertThat(descriptor.getAddressee()).isEqualTo("MyBot");
            assertThat(descriptor.getAnswers()).containsExactly("buy", "book");
        }

        @Test
        void commandWithoutAddresseeHasNone() {
            assertThat(MessageDescriptor.parse("/order?buy").getAddressee()).isNull();
        }

        @Test
        void anEmptyAddresseeCountsAsNone() {
            var descriptor = MessageDescriptor.parse("/order@?x");
            assertThat(descriptor.getCommand()).isEqualTo("order");
            assertThat(descriptor.getAddressee()).isNull();
            assertThat(descriptor.getAnswers()).containsExactly("x");
        }

        @Test
        void aSecondQuestionMarkStaysInTheAnswer() {
            assertThat(MessageDescriptor.parse("/a?b?c").getAnswers()).containsExactly("b?c");
        }

        @Test
        void aLoneHashParsesToNothing() {
            var descriptor = MessageDescriptor.parse("#");
            assertThat(descriptor.isCommand()).isFalse();
            assertThat(descriptor.getAnswers()).isEmpty();
            assertThat(descriptor.getDynamicParams().isEmpty()).isTrue();
        }

        @Test
        void aLoneSlashIsAnEmptyCommand() {
            var descriptor = MessageDescriptor.parse("/");
            assertThat(descriptor.isCommand()).isTrue();
            assertThat(descriptor.getCommand()).isEmpty();
        }

        @Test
        void typedCommandKeepsItsAddressee() {
            var descriptor = MessageDescriptor.parseTyped("/order@MyBot#approved");
            assertThat(descriptor.getAddressee()).isEqualTo("MyBot");
            assertThat(descriptor.getDynamicParams().isEmpty()).isTrue();
        }
    }
```

Add to `UpdateWrapperTest.java`:

```java
    @Test
    @DisplayName("getAddressee exposes the @bot a typed command names")
    void addressee() {
        assertThat(UpdateWrapper.wrap(Fixtures.messageUpdate(1, 1L, 1, "/order@MyBot")).getAddressee()).contains("MyBot");
        assertThat(UpdateWrapper.wrap(Fixtures.messageUpdate(1, 1L, 1, "/order")).getAddressee()).isEmpty();
        assertThat(UpdateWrapper.wrap(Fixtures.messageUpdate(1, 1L, 1, "hello")).getAddressee()).isEmpty();
    }
```

- [ ] **Step 2: Run to see them fail**

Run: `mvn -o -q test -Dtest='MessageDescriptorTest,UpdateWrapperTest'`
Expected: compilation failure — `getAddressee` does not exist.

- [ ] **Step 3: Rewrite the parser.** In `internal/CommandConstants.java` replace `String COMMAND_PARAMETERS_SEPARATOR_REGEX = "\\?";` with `String ADDRESSEE_SEPARATOR = "@";` (run `grep -rn COMMAND_PARAMETERS_SEPARATOR_REGEX src` first; after this task nothing may reference it). Replace `internal/MessageDescriptor.java` with

```java
package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.DynamicParameters;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.baevkir.sessionbot.internal.CommandConstants.*;

/**
 * Parses the callback/command wire format
 * {@code /command[@addressee][?answer1&answer2][#param:value&flag]} in one left-to-right pass: the
 * first {@code #} starts the dynamic parameters, the first {@code ?} after a command starts the
 * answers. Wire text comes from callback data the bot built itself; what a user types goes through
 * {@link #parseTyped} instead, so typed text can never forge extra answers or control parameters.
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageDescriptor {
    private String command;
    private String addressee;
    private List<String> answers;
    private DynamicParameters dynamicParams;

    public static MessageDescriptor parse(String text) {
        Assert.isTrue(StringUtils.hasText(text), "text is empty");
        var descriptor = new MessageDescriptor();
        var paramsStart = text.indexOf(DYNAMIC_PARAMETERS_SEPARATOR);
        var head = paramsStart < 0 ? text : text.substring(0, paramsStart);
        var params = paramsStart < 0 ? "" : text.substring(paramsStart + DYNAMIC_PARAMETERS_SEPARATOR.length());
        var answersPart = head;
        if (head.startsWith(COMMAND_START)) {
            var answersStart = head.indexOf(COMMAND_PARAMETERS_SEPARATOR);
            var name = answersStart < 0 ? head.substring(COMMAND_START.length()) : head.substring(COMMAND_START.length(), answersStart);
            answersPart = answersStart < 0 ? "" : head.substring(answersStart + COMMAND_PARAMETERS_SEPARATOR.length());
            var addresseeStart = name.indexOf(ADDRESSEE_SEPARATOR);
            descriptor.command = addresseeStart < 0 ? name : name.substring(0, addresseeStart);
            descriptor.addressee = addresseeStart < 0 ? null
                : StringUtils.hasText(name.substring(addresseeStart + 1)) ? name.substring(addresseeStart + 1) : null;
        }
        descriptor.answers = parseAnswers(answersPart);
        descriptor.dynamicParams = parseDynamicParams(params);
        log.debug("Parsed '{}' -> command={} addressee={} answers={} params={}",
            text, descriptor.command, descriptor.addressee, descriptor.answers, descriptor.dynamicParams);
        return descriptor;
    }

    /**
     * Parses a message the user typed. A typed command keeps its command, addressee and
     * {@code ?}-answers but never carries dynamic parameters; any other text is one answer, verbatim.
     */
    public static MessageDescriptor parseTyped(String text) {
        Assert.isTrue(StringUtils.hasText(text), "text is empty");
        MessageDescriptor descriptor;
        if (text.startsWith(COMMAND_START)) {
            descriptor = parse(text);
        } else {
            descriptor = new MessageDescriptor();
            descriptor.answers = List.of(text);
        }
        descriptor.dynamicParams = DynamicParameters.empty();
        return descriptor;
    }

    /** A descriptor for updates with no wire text to parse (e.g. a bare document message). */
    public static MessageDescriptor empty() {
        MessageDescriptor descriptor = new MessageDescriptor();
        descriptor.answers = List.of();
        descriptor.dynamicParams = DynamicParameters.empty();
        return descriptor;
    }

    public boolean isCommand() {
        return command != null;
    }

    private static List<String> parseAnswers(String answersPart) {
        if (!StringUtils.hasText(answersPart)) {
            return List.of();
        }
        return Arrays.stream(answersPart.split(PARAMETER_SEPARATOR)).map(WireFormat::decode).toList();
    }

    private static DynamicParameters parseDynamicParams(String params) {
        if (params.isEmpty()) {
            return DynamicParameters.empty();
        }
        return DynamicParameters.create(
            Arrays.stream(params.split(PARAMETER_SEPARATOR))
                .filter(param -> !param.isEmpty())
                .map(param -> param.split(KEY_VALUE_SEPARATOR, 2))
                .collect(Collectors.toMap(
                    param -> WireFormat.decode(param[ 0 ]),
                    param -> param.length > 1 ? WireFormat.decode(param[ 1 ]) : "",
                    (first, last) -> last))
        );
    }
}
```

In `UpdateWrapper.java` add, after `getCommand()`:

```java
    /** The bot a typed group command names ({@code /order@MyBot}); empty when it names none. */
    public Optional<String> getAddressee() {
        return Optional.ofNullable(messageDescriptor.getAddressee());
    }
```

- [ ] **Step 4: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS — including every pre-existing `MessageDescriptorTest`, `CommandBuilderTest` round-trip and `UpdateWrapperTest` case.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "refactor: single-pass wire parser that recognizes a command's @addressee"
```

---

### Task 9: Ignore commands addressed to another bot

**Files:**
- Modify: `internal/TelegramUpdateHandler.java`, `autoconfigure/SessionBotAutoConfiguration.java`
- Test: `internal/TelegramUpdateHandlerTest.java`

**Interfaces:**
- Consumes: `UpdateWrapper.getAddressee()` (Task 8).
- Produces: `TelegramUpdateHandler(CommandsFactory, AuthInterceptor, MessageExecutor, List<DocumentHandler>, List<ContactHandler>, List<TextHandler>, List<String> permitCommands, GuardDeniedHandler, String botUsername)` — the 7- and 8-argument constructors remain and pass `null` (no addressee filtering).

- [ ] **Step 1: Write the failing tests.** Add to `internal/TelegramUpdateHandlerTest.java`, next to the existing `handler(...)` helper (it uses the class's `commandsFactory`, `telegramClient` and `errorHandlerFactory` fields and the `ALLOW` interceptor):

```java
    private TelegramUpdateHandler handlerFor(String botUsername) {
        return new TelegramUpdateHandler(
            commandsFactory, ALLOW,
            new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory),
            List.of(), List.of(), List.of(), List.of(),
            new HelpGuardDeniedHandler(commandsFactory.getHelpCommand()), botUsername);
    }
```

Then add:

```java
    @DisplayName("a group command addressed to this bot runs, whatever the case of the name")
    @ParameterizedTest
    @ValueSource(strings = {"/order@MyBot?buy&book", "/order@mybot?buy&book", "/order@MYBOT?buy&book", "/order@?buy&book", "/order?buy&book"})
    void commandAddressedToThisBotRuns(String text) {
        var handler = handlerFor("MyBot");

        StepVerifier.create(handler.handleUpdates(Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, text))))
                .filter(message -> message instanceof SendMessage)
                .map(message -> ((SendMessage) message).getText()))
            .expectNext("buy:book")
            .verifyComplete();
    }

    @DisplayName("a group command addressed to another bot is ignored: no reply, no /help")
    @Test
    void commandAddressedToAnotherBotIsIgnored() {
        var handler = handlerFor("MyBot");

        StepVerifier.create(handler.handleUpdates(Flux.just(
                Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order@OtherBot?buy&book")))))
            .verifyComplete();
    }
```

(Add imports `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.ValueSource`.)

- [ ] **Step 2: Run to see them fail**

Run: `mvn -o -q test -Dtest=TelegramUpdateHandlerTest`
Expected: compilation failure — no 9-argument constructor.

- [ ] **Step 3: Implement.** In `internal/TelegramUpdateHandler.java` add the field `private final String botUsername;`, make the 8-argument constructor delegate `this(..., guardDeniedHandler, null)`, and add the canonical constructor

```java
    public TelegramUpdateHandler(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        MessageExecutor messageExecutor,
        List<DocumentHandler> documentHandlers,
        List<ContactHandler> contactHandlers,
        List<TextHandler> textHandlers,
        List<String> permitCommands,
        GuardDeniedHandler guardDeniedHandler,
        String botUsername
    ) {
        this.commandsFactory = commandsFactory;
        this.authInterceptor = authInterceptor;
        this.messageExecutor = messageExecutor;
        this.documentHandlers = documentHandlers;
        this.contactHandlers = contactHandlers;
        this.textHandlers = textHandlers;
        this.permitCommands = permitCommands == null ? List.of() : List.copyOf(permitCommands);
        this.guardDeniedHandler = Objects.requireNonNull(guardDeniedHandler, "guardDeniedHandler");
        this.botUsername = botUsername;
    }
```

(the 8-argument constructor's old body moves here). In `handleUpdates` insert `.filter(this::addressedToThisBot)` before `.scanWith(...)`, and add the private helper

```java
    /** In a group, {@code /order@OtherBot} is meant for another bot: leave it alone. */
    private boolean addressedToThisBot(UpdateWrapper update) {
        var addressee = update.getAddressee();
        if (botUsername == null || addressee.isEmpty() || addressee.get().equalsIgnoreCase(botUsername)) {
            return true;
        }
        log.debug("Ignoring /{} addressed to @{} in chat {}", update.getCommand(), addressee.get(), update.getChatId());
        return false;
    }
```

In `autoconfigure/SessionBotAutoConfiguration.java` change the `telegramUpdateHandler` bean to call the 9-argument constructor, passing `properties.getBotUsername()` last.

- [ ] **Step 4: Run the full suite**

Run: `mvn -o -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: ignore group commands addressed to another bot"
```

---

### Task 10: README, CHANGELOG and CLAUDE.md

**Files:**
- Modify: `README.md`, `CLAUDE.md`
- Create: `CHANGELOG.md`

- [ ] **Step 1: README.**
  - Replace the dependency block under **Requirements** with

````markdown
The library is published on JitPack. Add the repository and the dependency:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.baevkir</groupId>
    <artifactId>sessionbot-telegram-spring-boot-starter</artifactId>
    <version>v0.1.0</version>
</dependency>
```

Java packages live under `io.github.baevkir.sessionbot`; everything under
`io.github.baevkir.sessionbot.internal` is implementation detail with no compatibility guarantee.
````

  - After the **Parameters** section's `CommandBuilder` paragraphs add

````markdown
### The conversation: `CommandContext`

A command method, guard, handler or renderer can take a `CommandContext` — a read-only view of the
chat's conversation: `getChatId()`, `getUser()`, `getCommand()`, `getAnswers()`,
`getCommandUpdate()` (the command, or the bare update itself outside a command),
`getCurrentUpdate()`, `getCallbackMessage()` (the message whose button was tapped) and
`getDynamicParams()`. Only the library advances the conversation. In tests, build one with
`CommandContext.of(update)`.
````

  - In **Authentication** replace "(default `start`)" with "(empty by default)" and append the sentence "A bot whose `/start` must admit unknown callers sets `permit-commands: [start]`."
  - After **Per-chat menus** add

````markdown
## Group chats

In a group, Telegram delivers commands as `/order@MyBot`. The bot runs a command addressed to its
own `bot-username` (case-insensitive) or to no one, and ignores one addressed to another bot — no
reply, no `/help`.
````

  - In the configuration table set `permit-commands`' default to `*(empty)*`.
  - In **Overriding beans** replace `CommandsSessionBotConfiguration` with `SessionBotAutoConfiguration`, and remove `TelegramUpdateHandler`, `HelpCommand` and `CommandsFactory` from the by-type list (they are internal; to change the reply to unknown or refused commands override `GuardDeniedHandler`, or handle plain text with a `TextHandler`).
  - Everywhere else, fix class and package names: `IBotCommand` → (remove the mention), `com.kb.sessionbot` → `io.github.baevkir.sessionbot`. Verify with `grep -n "com.kb\|IBotCommand\|scipAnswer\|CommandsSessionBotConfiguration" README.md` → no output.

- [ ] **Step 2: CHANGELOG.** Create `CHANGELOG.md`:

```markdown
# Changelog

## 0.1.0 — unreleased

First public release.

### Breaking changes and migration

- **Coordinates and packages.** The artifact is `sessionbot-telegram-spring-boot-starter`
  (`com.github.baevkir` on JitPack, `io.github.baevkir` on Maven Central), and every package moved
  from `com.kb.sessionbot` to `io.github.baevkir.sessionbot`:

  | Types | Package |
  |---|---|
  | `CommandContext`, `UpdateWrapper`, `DynamicParameters`, `CommandBuilder`, `AuthInterceptor`, `MessageExecutor`, `OutboundMessageBus`, `InboundUpdateBus`, `ChatUpdateStream` | `io.github.baevkir.sessionbot` |
  | `@BotCommand`, `@CommandMethod`, `@Parameter`, `@Rendering`, `@RenderingOption` | `…sessionbot.annotation` |
  | `@Guarded`, `CommandGuard`, `GuardContext`, `GuardDeniedHandler` | `…sessionbot.guard` |
  | `TextHandler`, `DocumentHandler`, `ContactHandler` | `…sessionbot.handler` |
  | `ParameterRenderer`, `ParameterRequest`, `ParameterOption`, built-in renderers | `…sessionbot.render` |
  | `ErrorHandler`, `BotCommandException`, `BotAuthException`, default error handlers | `…sessionbot.error` |
  | `LocaleProvider`, `ConfiguredLocaleProvider`, `BotLabels` | `…sessionbot.i18n` |
  | `CommandMenuService` | `…sessionbot.menu` |
  | `SessionBotAutoConfiguration`, `SessionBotProperties` | `…sessionbot.autoconfigure` |

  Everything else is in `…sessionbot.internal` and carries no compatibility guarantee.
- **`CommandContext` is read-only.** `getChatId()`, `getCommandUpdate()` and `getCurrentUpdate()`
  work as before; `getUser()` and `getCallbackMessage()` are new — replace hand-written helpers that
  derive the tapped message from `getCurrentUpdate()`/`getCommandUpdate()` with
  `getCallbackMessage()`. Mutators, `getState()`, `getUpdates()`, `getQuestionMessages()` and
  `getInitialUpdate()` are gone. In tests replace `CommandContext.create(…)`/`forUpdate(…)` with
  `CommandContext.of(update)`.
- **`permit-commands` defaults to empty.** Add `sessionbot.telegram.permit-commands: [start]` if
  `/start` must run before the `AuthInterceptor` recognizes the caller.
- **`LocaleProvider.getLocale(User)`** replaces `getLocale(String userName)`;
  `BotLabels.resolve(text, User)` replaces `resolve(text, String)`.
- **Renamed:** `CommandBuilder.scipAnswer` → `skipAnswer`, `DynamicParameters.canScipAnswer` →
  `canSkipAnswer` (wire key `skip`), `ParameterRendererFactory` → `CompositeParameterRenderer`,
  `Option` → `ParameterOption(value, label)`.
- **Removed:** `CommandBuilder.commandApproved()`/`setInitiator()` and
  `DynamicParameters.commandApproved()`/`getInitiator()` — use `addParam`/`getParam`;
  `BotMethodPresenter`/`AbstractMessagePresenter`; overriding the `HelpCommand` or `CommandsFactory`
  beans.
- **Tests** that mock `CommandsSessionBot` can drop the mock when the token is empty (the bot does
  not start), or import it from `…sessionbot.internal`. Tests touching `MethodMatcher`,
  `MessageDescriptor` or `GuardResolver` now depend on internals.

### Added

- Group chats: `/cmd@ThisBot` runs, `/cmd@OtherBot` is ignored.
- `TextHandler.supports(text)` — several text handlers can split the work.
- `CommandContext.getUser()`, `getCallbackMessage()`, `CommandContext.of(update)`.

### Fixed

- Typed text can no longer forge answers or control parameters; `CommandBuilder` escapes reserved
  characters.
- Commands wrapped in AOP proxies (`@Transactional`, …) are dispatched, with their advice.
- Error replies never show exception messages; error handlers match exception subclasses.
- `/help` releases the chat at once; bare updates no longer accumulate in one context.
- An update with no resolvable chat no longer terminates the inbound stream.
- HTML in command descriptions and prompts is escaped.
```

- [ ] **Step 3: CLAUDE.md.** Bring it in line with the code:
  - "What this is": artifact `io.github.baevkir:sessionbot-telegram-spring-boot-starter`, published via JitPack (Maven Central later); drop the `baevkir/library-project` / `mvn-repo` sentence.
  - "How it wires up": `SessionBotAutoConfiguration` in `io.github.baevkir.sessionbot.autoconfigure`; `HelpCommand`/`CommandsFactory` are internal, not overridable.
  - "Core processing flow": the fold state is `internal.ConversationState`; bots see the read-only `CommandContext`; `/help` closes its conversation; each bare update gets its own state.
  - "Command dispatch": replace `IBotCommand` with `internal.RegisteredCommand`; the return type is `Publisher<? extends PartialBotApiMethod<?>>` (there is no `BotCommandResult`).
  - "Wire format": add `@addressee`; dynamic params are `refreshContext` and `skip:<index>` only; typed text never carries dynamic params.
  - "Parameter renderers": the default is `CompositeParameterRenderer` (bean `defaultParameterRenderer`); there is no `@RenderingMethod` and no `createChild` hierarchy — custom renderers are beans referenced by `@Rendering(name = …)` or `type = …`.
  - Package names throughout: `com.kb.sessionbot` → `io.github.baevkir.sessionbot`.
  - Verify: `grep -n "com.kb\|IBotCommand\|BotCommandResult\|RenderingMethod\|createChild\|mvn-repo" CLAUDE.md` → no output.

- [ ] **Step 4: Commit**

```bash
git add README.md CHANGELOG.md CLAUDE.md
git commit -m "docs: document the 0.1.0 API, migration notes and group chats"
```

---

### Task 11: Check the migration against family-iot (read-only)

**Files:** none in this repo unless gaps are found (then `CHANGELOG.md`).

- [ ] **Step 1: Install the snapshot locally**

Run: `mvn -o -q install -DskipTests`
Expected: BUILD SUCCESS; `~/.m2/repository/io/github/baevkir/sessionbot-telegram-spring-boot-starter/0.1.0-SNAPSHOT/` exists.

- [ ] **Step 2: Compile family-iot against it in a throwaway worktree**

```bash
cd "/Users/kirillbaev/Workspace/Pet projects/family-iot"
git worktree add -q "$TMPDIR/family-iot-migration" HEAD
cd "$TMPDIR/family-iot-migration"
sed -i '' 's|<groupId>com.kb</groupId>|<groupId>io.github.baevkir</groupId>|; s|<artifactId>telegram-session-bot</artifactId>|<artifactId>sessionbot-telegram-spring-boot-starter</artifactId>|; s|<telegram-session-bot.version>0.0.1-SNAPSHOT</telegram-session-bot.version>|<telegram-session-bot.version>0.1.0-SNAPSHOT</telegram-session-bot.version>|' pom.xml */pom.xml
grep -rl "com.kb.sessionbot" --include=*.java . | xargs sed -i '' 's/com\.kb\.sessionbot/io.github.baevkir.sessionbot/g'
mvn -o -q test-compile 2>&1 | grep -E "ERROR.*\.java" | sed 's|.*/family-iot-migration/||' | sort | uniq | head -100
```

The blanket `com.kb.sessionbot` → `io.github.baevkir.sessionbot` rewrite gets the root right but not the sub-packages; the remaining errors are exactly the migration work.

- [ ] **Step 3: Compare with the CHANGELOG.** Group the compile errors by cause (wrong sub-package import, `CallbackMessages`, `CommandContext.create/forUpdate`, `@MockitoBean CommandsSessionBot`, `internal` usage in tests, anything else). Every cause must be covered by a bullet in `CHANGELOG.md`'s migration list; add a bullet for any that is not. Note that `permit-commands` is a runtime, not compile-time, change: confirm `grep -rn "permit-commands" --include=*.yml .` shows the shopping bot still needs it.

- [ ] **Step 4: Remove the worktree**

```bash
cd "/Users/kirillbaev/Workspace/Pet projects/family-iot"
git worktree remove --force "$TMPDIR/family-iot-migration"
git status --short   # family-iot's own working tree is untouched
```

- [ ] **Step 5: Commit any CHANGELOG additions** (skip if none)

```bash
cd "/Users/kirillbaev/Workspace/Pet projects/telegram-session-bot"
git add CHANGELOG.md
git commit -m "docs: complete the 0.1.0 migration notes"
```

Report to the owner: the grouped error list and the number of files per bot that need changes.
