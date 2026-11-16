package org.embeddedt.embeddium.impl.extra.compat;

import java.util.List;

public record DebugScreenDisplayer(List<String> lines, List<String> priorityLines) {
    public void addLine(String line) {
        lines.add(line);
    }

    public void addPriorityLine(String line) {
        priorityLines.add(line);
    }
}
