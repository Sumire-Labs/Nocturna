package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.prompt;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i;
import org.jetbrains.annotations.Nullable;

@Deprecated(forRemoval = true)
public interface ScreenPromptable {
    void setPrompt(@Nullable ScreenPrompt prompt);

    @Nullable
    ScreenPrompt getPrompt();

    Dim2i getDimensions();
}
