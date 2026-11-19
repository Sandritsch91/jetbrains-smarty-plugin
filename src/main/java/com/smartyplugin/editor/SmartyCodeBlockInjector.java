package com.smartyplugin.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.smartyplugin.psi.SmartyCodeText;
import com.smartyplugin.psi.SmartyPsiElement;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY;
import static com.smartyplugin.parser.SmartyElementTypes.CODE_TEXT;

/**
 * The content of <code>{registerJs}...{/registerJs}</code> is JavaScript and of <code>{registerCss}...{/registerCss}</code>
 * CSS (yii2-smarty blocks). The text pieces of the block body ({@link SmartyCodeText}) form one injected script;
 * Smarty tags between them stay Smarty. The languages are looked up by id, so IDEs without JavaScript or CSS
 * support simply get no injection.
 */
public final class SmartyCodeBlockInjector implements MultiHostInjector {
    private static final Map<String, String> BLOCK_LANGUAGES = Map.of(
            "registerJs", "JavaScript",
            "registerCss", "CSS");

    /**
     * Registered on the block body, the common parent of all pieces: the platform asks the ancestors of every
     * piece, so each piece finds the one injection that spans them all.
     */
    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(SmartyPsiElement.class);
    }

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        ASTNode body = context.getNode();
        if (body == null || body.getElementType() != BLOCK_BODY || body.getTreeParent() == null) return;
        String languageId = BLOCK_LANGUAGES.get(SmartyPsiUtil.blockName(body.getTreeParent()));
        Language language = languageId == null ? null : Language.findLanguageByID(languageId);
        if (language == null) return;

        List<SmartyCodeText> pieces = new ArrayList<>();
        for (ASTNode c = body.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getElementType() == CODE_TEXT && c.getPsi() instanceof SmartyCodeText text) pieces.add(text);
        }
        if (pieces.isEmpty()) return;
        registrar.startInjecting(language);
        for (SmartyCodeText piece : pieces) {
            // a Smarty tag after the piece is a value at run time ("var id = {$model->id};", "width: {$w}px"):
            // stand in a neutral value so the script parses instead of showing "var id = ;" as an error
            String suffix = followedByTag(piece) ? PLACEHOLDER : null;
            registrar.addPlace(null, suffix, piece, TextRange.from(0, piece.getTextLength()));
        }
        registrar.doneInjecting();
    }

    private static final String PLACEHOLDER = "0";

    private static boolean followedByTag(SmartyCodeText piece) {
        ASTNode next = piece.getNode().getTreeNext();
        while (next != null && next.getTextLength() == 0) next = next.getTreeNext();
        return next != null && next.getElementType() != CODE_TEXT;
    }
}
