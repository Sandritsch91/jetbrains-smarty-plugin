# Smarty Templates for IntelliJ IDEA

An IntelliJ Platform plugin for Smarty 3/4/5 templates (`.tpl`, `.smarty`).

## Features

| Area | What you get |
|---|---|
| Highlighting | Smarty tags, variables, modifiers, attributes, strings, operators, `{* comments *}` on top of full HTML/CSS/JS highlighting. Colors are configurable in *Settings, Editor, Color Scheme, Smarty*. |
| Completion | Tag names (block tags insert their `{/tag}`), closing tags (innermost open block first), attributes per tag, modifiers, `$variables` used or assigned in the file, `$smarty.*`, `@iteration`-style loop properties. A few Yii2 tags (`url`, `use`, `set`, ...) are included. |
| Formatting | *Reformat Code* formats the HTML and indents Smarty block bodies (`{if}`, `{foreach}`, `{block}`, custom block plugins) together with it. |
| Errors | Unclosed blocks, stray closing tags, misplaced `{else}` / `{elseif}` / `{foreachelse}`, `{if}` without a condition. |
| Editing | Typing `}` on an opening block tag inserts the closing tag. Typing `{/` completes the innermost open block. Enter between `{if}` and `{/if}` splits them onto separate lines. *Comment with Block Comment* uses `{* *}`. Folding of blocks and comments. Brace matching, and highlighting of the matching `{if}`/`{else}`/`{/if}` when the caret is on a tag name. |
| Navigation | Ctrl+Click on paths in `{include}` / `{extends}` / `{config_load}`. Paths resolve against the template's folder and every parent folder up to the content root, which approximates Smarty's `template_dir`. |
| Other | *New, Smarty Template* action. The template data language can be changed in *Settings, Languages & Frameworks, Template Data Languages*. |

Smarty's auto-literal rule is respected: `{` followed by whitespace is plain text, so inline JS/CSS works without `{literal}`.

## Building

Requires a JDK 21+. The IntelliJ IDEA bundled runtime works:

```powershell
$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\jbr"
.\gradlew.bat buildPlugin     # -> build\distributions\jetbrains-smarty-plugin-0.1.0.zip
.\gradlew.bat test            # headless tests
.\gradlew.bat runIde          # sandbox IDE with the plugin installed
```

`gradle.properties` points `platformLocalPath` at the locally installed IDE so nothing big is downloaded.
Clear it to build against a downloaded `platformType`/`platformVersion` instead.

### PhpStorm

The plugin only depends on the platform and XML modules, so it runs in PhpStorm and other IDEs with HTML support (WebStorm, ...).

```powershell
.\gradlew.bat test -PplatformLocalPath= -PplatformType=PS   # run the tests inside PhpStorm (downloads it once)
.\gradlew.bat verifyPlugin                                  # JetBrains Plugin Verifier: PhpStorm 2026.2, IDEA 2026.3, local IDEA
```

Install the zip in the IDE via *Settings, Plugins, ⚙, Install Plugin from Disk…*

## Limitations

- Default delimiters `{` `}` only. Custom `left_delimiter` / `right_delimiter` are not supported yet.
- Tag internals are not reformatted (spacing inside `{...}` is kept as written).
- Variables inside double-quoted strings (`"Hello {$name}"`) are highlighted as part of the string.
- No PHP-side resolution (variables assigned from PHP controllers are not known).
- JetBrains ships its own *Smarty* plugin (bundled in PhpStorm, optional in IntelliJ IDEA Ultimate). Both register `.tpl`; when both are enabled this plugin takes `.tpl` and the IDE may show a notice about the reassignment. To go back, pick *Smarty* for `*.tpl` in *Settings, Editor, File Types*, or disable one of the plugins.

## Layout

```
src/main/java/com/smartyplugin/
  lang/         language, file type, view provider (Smarty + HTML trees), tag/modifier registry
  lexer/        hand-written restartable lexer and token types
  parser/       PsiBuilder parser: blocks, tags, attributes, modifiers
  psi/          PSI classes, string literal with file references, utilities
  highlighting/ syntax highlighter, layered HTML highlighter, color settings page
  editor/       annotator, folding, commenter, brace matcher, typing/enter handlers, tag-pair highlight
  completion/   completion contributor
  formatter/    template-language formatter on top of the HTML formatter
  actions/      "New Smarty Template" action
```
