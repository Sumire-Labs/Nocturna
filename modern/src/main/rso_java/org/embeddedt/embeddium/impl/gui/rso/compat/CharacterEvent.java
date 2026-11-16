package org.embeddedt.embeddium.impl.gui.rso.compat;

public record CharacterEvent(int codepoint, int modifiers) {
    public boolean isAllowedChatCharacter() {
        return codepoint >= 32 && codepoint != 127 && codepoint != 167;
    }

    public String codepointAsString() {
        return new String(Character.toChars(codepoint));
    }
}
