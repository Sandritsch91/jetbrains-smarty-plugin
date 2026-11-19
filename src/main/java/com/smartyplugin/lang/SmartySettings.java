package com.smartyplugin.lang;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.FileContentUtil;
import org.jetbrains.annotations.NotNull;

/** Project settings, stored in .idea/smarty.xml so they can be shared with the team. */
@Service(Service.Level.PROJECT)
@State(name = "SmartySettings", storages = @Storage("smarty.xml"))
public final class SmartySettings implements PersistentStateComponent<SmartySettings.State> {

    public static final class State {
        public String leftDelimiter = SmartyDelimiters.DEFAULT.left();
        public String rightDelimiter = SmartyDelimiters.DEFAULT.right();
    }

    private State state = new State();

    public static @NotNull SmartySettings getInstance(@NotNull Project project) {
        return project.getService(SmartySettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public @NotNull SmartyDelimiters delimiters() {
        String left = state.leftDelimiter;
        String right = state.rightDelimiter;
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) return SmartyDelimiters.DEFAULT;
        return new SmartyDelimiters(left, right);
    }

    /** Changes the delimiters and re-parses open files, so highlighting and the PSI follow. */
    public void setDelimiters(@NotNull SmartyDelimiters delimiters) {
        if (delimiters.equals(delimiters())) return;
        state.leftDelimiter = delimiters.left();
        state.rightDelimiter = delimiters.right();
        FileContentUtil.reparseOpenedFiles();
    }
}
