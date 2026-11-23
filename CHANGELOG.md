# Changelog

## [Unreleased]

## [0.1.0]

First release.

### Added

- Smarty 3/4/5 templates (`.tpl`, `.smarty`) on top of HTML: highlighting, completion, formatting, error checks with quick fixes, folding, brace and tag-pair matching, live templates, TODO comments
- Custom delimiters per project
- Template inheritance: `{block}` name completion, navigation and gutter icons
- `{include}` variables, `{#config#}` variables from `{config_load}` files, the project's Smarty plugins
- Rename of template variables, also where they are passed in
- With the PHP plugin: variables passed from PHP with their types, member completion and navigation, hover documentation, navigation from `render()` to the template
- Yii2: widgets as tags and in configuration arrays, `$app` / `$this`, the yii2-smarty functions and blocks, routes and translations
