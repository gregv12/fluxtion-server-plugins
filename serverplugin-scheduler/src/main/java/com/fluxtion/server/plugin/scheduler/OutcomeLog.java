/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Append-only outcome log in the server's audit-record layout ({@code ---} separated records, one
 * {@code eventLogRecord} each), so the existing audit tooling can open, filter and chart it. Rolls by size,
 * keeping {@code maxFiles} files. Values are written on one line with structural characters replaced.
 */
public class OutcomeLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault()); // as log4j stamps the other audit logs
    private final Path file;
    private final long maxBytes;
    private final int maxFiles;

    public OutcomeLog(Path file, long maxBytes, int maxFiles) {
        this.file = file;
        this.maxBytes = maxBytes;
        this.maxFiles = maxFiles;
    }

    public synchronized void append(Outcome o) {
        String record = "---\n"
                + "#" + TIME.format(Instant.ofEpochMilli(o.getDecidedAt())) + " [scheduler] INFO  scheduler \n"
                + "eventLogRecord: \n"
                + "    eventTime: " + o.getDecidedAt() + "\n"
                + "    logTime: " + o.getDecidedAt() + "\n"
                + "    groupingId: null\n"
                + "    event: SchedulerOutcome\n"
                + "    eventToString: " + quote(o.getTitle()) + "\n"
                + "    nodeLogs: \n"
                + "        - scheduler: { state: " + o.getState()
                + ", title: " + quote(o.getTitle())
                + ", command: " + quote(o.getCommand())
                + ", arguments: " + quote(String.join(" ", o.getArguments()))
                + ", decidedBy: " + quote(o.getDecidedBy())
                + ", dueAt: " + o.getDueAt()
                + ", durationMillis: " + o.getDurationMillis()
                + ", restartSet: " + quote(o.getRestartSetId())
                + ", itemId: " + quote(o.getItemId())
                + ", detail: " + quote(o.getDetail()) + "}\n"
                + "    endTime: " + o.getDecidedAt() + "\n";
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            roll();
            Files.writeString(file, record, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A double-quoted YAML scalar on one line, bounded length: any command output (JSON, quotes, '*', '- ', '|')
     * stays a plain string to a YAML reader. JSON string escaping is a subset of YAML double-quoted escaping.
     */
    static String quote(String s) {
        if (s == null) return "null";
        String one = s.replaceAll("[\\r\\n]+", " | ").trim();
        if (one.length() > 500) one = one.substring(0, 500) + "...";
        StringBuilder sb = new StringBuilder(one.length() + 2).append('"');
        for (char c : one.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private void roll() throws IOException {
        if (!Files.exists(file) || Files.size(file) < maxBytes) return;
        String name = file.getFileName().toString();
        String base = name.endsWith(".yaml") ? name.substring(0, name.length() - 5) : name;
        for (int i = maxFiles - 1; i >= 1; i--) {
            Path from = file.resolveSibling(base + "-" + i + ".yaml");
            if (Files.exists(from)) Files.move(from, file.resolveSibling(base + "-" + (i + 1) + ".yaml"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(file, file.resolveSibling(base + "-1.yaml"), StandardCopyOption.REPLACE_EXISTING);
    }
}
