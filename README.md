# Smarty Templates for IntelliJ IDEA

An IntelliJ Platform plugin for Smarty 3/4/5 templates (`.tpl`, `.smarty`).

## Features

| Area | What you get |
|---|---|
| Highlighting | Smarty tags, variables, modifiers, attributes, strings, operators, `{* comments *}` on top of full HTML/CSS/JS highlighting. Inside double-quoted strings, embedded `$variables`, `` `$backtick.expressions` `` and `{$tags|modifiers}` are highlighted like code. Colors are configurable in *Settings, Editor, Color Scheme, Smarty*. |
| Completion | Tag names (block tags insert their `{/tag}`), closing tags (innermost open block first), attributes per tag, modifiers, `$variables` used or assigned in the file, `$smarty.*`, `@iteration`-style loop properties. A few Yii2 tags (`url`, `use`, `set`, ...) are included. |
| Formatting | *Reformat Code* formats the HTML and indents Smarty block bodies (`{if}`, `{foreach}`, `{block}`, custom block plugins) together with it. Inside tags it normalises spacing (`{if $a==1}` → `{if $a == 1}`, `{include file="x"   title=$t }` → `{include file="x" title=$t}`, `{$a | escape}` → `{$a\|escape}`) and indents multi-line tags: continuation lines one level, the contents of `[...]` / `(...)` one more. Line breaks are kept; modifier arguments, `$var=` assignments and `.` are left as written. |
| Errors | Unclosed blocks, stray closing tags, misplaced `{else}` / `{elseif}` / `{foreachelse}`, `{if}` without a condition. |
| Editing | Typing `}` on an opening block tag inserts the closing tag. Typing `{/` completes the innermost open block. Enter between `{if}` and `{/if}` splits them onto separate lines. *Comment with Block Comment* uses `{* *}`. Folding of blocks and comments. Brace matching, and highlighting of the matching `{if}`/`{else}`/`{/if}` when the caret is on a tag name. |
| Navigation | Ctrl+Click on paths in `{include}` / `{extends}` / `{config_load}`. Paths resolve against the template's folder and every parent folder up to the content root, which approximates Smarty's `template_dir`. |
| PHP | With the PHP plugin installed (bundled in PhpStorm), variables passed in from PHP are completed with their type, and Ctrl+Click goes to where they are passed. Recognised: Yii2 `$this->render('index', ['user' => $user])` (also `renderPartial`, `renderAjax`, `renderFile`, `compact('user')`; view names resolve against `views/<controller-id>/`, the calling view, `//path` and `@alias/path`) and Smarty `$smarty->assign(...)` + `display()`/`fetch()` in the same function. `$user->` completes public fields, methods and Yii getters (`getFullName()` as `fullName`), also along chains like `$user->profile->city` and for `{foreach $users as $u}` items; Ctrl+Click on a member opens its PHP declaration. |
| Other | *New, Smarty Template* action. The template data language can be changed in *Settings, Languages & Frameworks, Template Data Languages*. |

Smarty's auto-literal rule is respected: `{` followed by whitespace is plain text, so inline JS/CSS works without `{literal}`.

## Building

Requires a JDK 21+ in `JAVA_HOME` (the runtime bundled with IntelliJ IDEA, its `jbr` folder, works).

```powershell
.\gradlew.bat buildPlugin     # -> build\distributions\jetbrains-smarty-plugin-0.1.0.zip
.\gradlew.bat test            # headless tests
.\gradlew.bat runIde          # sandbox IDE with the plugin installed
```

By default the build downloads IntelliJ IDEA `platformVersion` from `gradle.properties` once (about 1.5 GB).
To build against an IDE you already have installed instead, set `platformLocalPath` for your machine only,
for example in `~/.gradle/gradle.properties`:

```properties
platformLocalPath=C:/Program Files/JetBrains/IntelliJ IDEA
```

### PhpStorm

The plugin only depends on the platform and XML modules, so it runs in PhpStorm and other IDEs with HTML support (WebStorm, ...).

```powershell
.\gradlew.bat test -PplatformLocalPath= -PplatformType=PS   # run the tests inside PhpStorm (downloads it once)
.\gradlew.bat verifyPlugin                                  # JetBrains Plugin Verifier: PhpStorm and IDEA 2026.2/2026.3
```

Install the zip in the IDE via *Settings, Plugins, ⚙, Install Plugin from Disk…*

## Limitations

- Default delimiters `{` `}` only. Custom `left_delimiter` / `right_delimiter` are not supported yet.
- PHP-side variables are found by matching render/assign calls to the template path; other ways of passing variables (custom renderers, `$view->params`, variables assigned in a different function than `display()`) are not followed.
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
