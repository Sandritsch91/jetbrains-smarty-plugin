# Smarty Templates for IntelliJ IDEA

An IntelliJ Platform plugin for Smarty 3/4/5 templates (`.tpl`, `.smarty`).

## Features

| Area | What you get |
|---|---|
| Highlighting | Smarty tags, variables, modifiers, attributes, strings, operators, `{* comments *}` on top of full HTML/CSS/JS highlighting. Inside double-quoted strings, embedded `$variables`, `` `$backtick.expressions` `` and `{$tags\|modifiers}` are highlighted like code. Colors are configurable in *Settings, Editor, Color Scheme, Smarty*. |
| Completion | Tag names (block tags insert their `{/tag}`), closing tags (innermost open block first), attributes per tag, modifiers, `$variables` used or assigned in the file, `$smarty.*`, `@iteration`-style loop properties. All functions, blocks and modifiers of the yii2-smarty extension are included: `{url}`, `{path}`, `{use}` (with `as`), `{set}`, `{meta}`, `{js}`, `{registerJsFile}`, `{registerCssFile}`, the blocks `{title}`, `{description}`, `{registerJs}`, `{registerCss}` and the `void` modifier. The content of `{registerJs}` is edited as JavaScript and of `{registerCss}` as CSS (highlighting, completion, inspections); Smarty tags inside stay Smarty. |
| Formatting | *Reformat Code* formats the HTML and indents Smarty block bodies (`{if}`, `{foreach}`, `{block}`, custom block plugins) together with it. Inside tags it normalises spacing (`{if $a==1}` → `{if $a == 1}`, `{include file="x"   title=$t }` → `{include file="x" title=$t}`, `{$a \| escape}` → `{$a\|escape}`) and indents multi-line tags: continuation lines one level, the contents of `[...]` / `(...)` one more. Line breaks are kept; modifier arguments, `$var=` assignments and `.` are left as written. The code in `{registerJs}` / `{registerCss}` is formatted by the JavaScript / CSS formatter with your code style, also around Smarty tags in it (`var id = {$model->id};`). |
| Errors | Unclosed blocks, stray closing tags, misplaced `{else}` / `{elseif}` / `{foreachelse}`, `{if}` without a condition. |
| Editing | Typing `}` on an opening block tag inserts the closing tag. Typing `{/` completes the innermost open block. Enter between `{if}` and `{/if}` splits them onto separate lines. *Comment with Block Comment* uses `{* *}`. Folding of blocks and comments. Brace matching, and highlighting of the matching `{if}`/`{else}`/`{/if}` when the caret is on a tag name. |
| Navigation | Ctrl+Click on paths in `{include}` / `{extends}` / `{config_load}`. Paths resolve against the template's folder and every parent folder up to the content root, which approximates Smarty's `template_dir`. |
| Documentation | Hover / Quick Documentation (Ctrl+Q): Smarty tags and modifiers show their syntax and description; with the PHP plugin, classes, members, widget attributes and configuration keys show PHP's own documentation, and `$variables` the documentation of their class. |
| PHP | With the PHP plugin installed (bundled in PhpStorm), variables passed in from PHP are completed with their type, and Ctrl+Click goes to where they are passed. Recognised: Yii2 `$this->render('index', ['user' => $user])` (also `renderPartial`, `renderAjax`, `renderFile`, `compact('user')`; view names resolve against `views/<controller-id>/`, the calling view, `//path` and `@alias/path`) and Smarty `$smarty->assign(...)` + `display()`/`fetch()` in the same function. `$user->` completes public fields, methods and Yii getters (`getFullName()` as `fullName`), also along chains like `$user->profile->city` and for `{foreach $users as $u}` items; Ctrl+Click on a member opens its PHP declaration. Variables passed by `{$this->render('_form', [...])}` in another template are known in `_form.tpl` too. Classes imported with Yii's `{use class="yii\bootstrap5\Html"}` (or global ones like `Yii`) complete their public static methods and constants after `Html::`; Ctrl+Click works on the class name, the member and the `use` string. Widgets used as tags (`{Select2 ...}`, `{ActiveForm}...{/ActiveForm}` after `{use class="..." type="function\|block"}`): attributes complete the widget's public properties and setters (`setTheme()` as `theme`), and so do the keys of a widget configuration array (`Select2::widget([...])`, `ActiveForm::begin([...])`, `->widget(Select2::class, [...])`); Ctrl+Click on the tag name opens the class, on an attribute its property or setter. Inside `{ActiveForm assign="form"}...{/ActiveForm}`, `$form` has the widget's type. In Yii projects every template knows `$app` (`yii\web\Application`) and `$this` (`yii\web\View`). Ctrl+Click on a variable defined in the template (`assign="form"`, `{assign var="x"}`, `{foreach $a as $x}`) goes to its definition. |
| Delimiters | Custom `left_delimiter` / `right_delimiter` (e.g. `{{` `}}` or `<!--{` `}-->`) are set per project in *Settings, Languages & Frameworks, Smarty* and stored in `.idea/smarty.xml`. Highlighting, parsing, comments (`{{* *}}`), `{{literal}}`, auto-closing, completion and formatting follow them. |
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

- Delimiters are configured, not detected: set them in the project settings to match your Smarty configuration.
- PHP-side variables are found by matching render/assign calls to the template path; other ways of passing variables (custom renderers, `$view->params`, variables assigned in a different function than `display()`) are not followed.
- JetBrains ships its own *Smarty* plugin (bundled in PhpStorm, optional in IntelliJ IDEA Ultimate). Both register `.tpl`; when both are enabled this plugin takes `.tpl` and the IDE may show a notice about the reassignment. To go back, pick *Smarty* for `*.tpl` in *Settings, Editor, File Types*, or disable one of the plugins.

## Layout

```
src/main/kotlin/com/smartyplugin/   (Kotlin; tests in src/test/kotlin)
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
