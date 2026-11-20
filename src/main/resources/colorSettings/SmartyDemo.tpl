{* Page template *}
{extends file="layouts/main.tpl"}
{block name="content"}
  <h1>{$page.title|escape:'html'|default:"Untitled"}</h1>
  {if $user->isGuest() && #showLogin#}
    {include file="partials/login.tpl" redirect=true}
  {elseif $items|@count gt 0}
    <ul>
    {foreach $items as $key => $item}
      <li class="{cycle values='odd,even'}">{$item@iteration}: {$item.name|truncate:30:"..."}</li>
    {foreachelse}
      <li>No items</li>
    {/foreach}
    </ul>
  {/if}
  {include file="partials/greeting.tpl" text="Hello $name, `$user.email` has {$count|default:0} new messages"}
  {Html::a($page.title|escape, $user->profileUrl(), ['class' => Select2::THEME_BOOTSTRAP])}
  {if count($items) > $user->limit}…{/if}
{/block}
