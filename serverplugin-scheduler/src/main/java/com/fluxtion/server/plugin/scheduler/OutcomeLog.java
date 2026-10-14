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
import java.util.List;
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

    /** Whether a record for this item is already in the log (current file or the latest rolled one). */
    public synchronized boolean contains(String itemId) {
        String needle = "itemId: " + quote(itemId) + ",";
        String name = file.getFileName().toString();
        String base = name.endsWith(".yaml") ? name.substring(0, name.length() - 5) : name;
        for (Path p : List.of(file, file.resolveSibling(base + "-1.yaml"))) {
            try {
                if (Files.exists(p) && Files.readString(p, StandardCharsets.UTF_8).contains(needle)) return true;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return false;
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
        StringBuilder sb = new StringBuilder(Math.min(one.length(), 600) + 2).append('"');
        int count = 0;
        for (int i = 0; i < one.length(); ) {
            int cp = one.codePointAt(i);
            i += Character.charCount(cp);
            if (++count > 500) {
                sb.append("...");
                break;
            }
            if (Character.isSurrogate((char) cp) && cp <= 0xFFFF) cp = 0xFFFD; // a lone surrogate cannot be written
            if (cp == '"') sb.append("\\\"");
            else if (cp == '\\') sb.append("\\\\");
            else if (yamlPrintable(cp)) sb.appendCodePoint(cp);
            else sb.append(String.format("\\u%04x", cp)); // only BMP code points reach here
        }
        return sb.append('"').toString();
    }

    /** YAML's printable set, less tab, NEL and BOM, which are escaped to be safe. */
    private static boolean yamlPrintable(int cp) {
        return (cp >= 0x20 && cp <= 0x7E) || (cp >= 0xA0 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD && cp != 0xFEFF)
                || (cp >= 0x10000 && cp <= 0x10FFFF);
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
