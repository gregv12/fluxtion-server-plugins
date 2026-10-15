/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scheduler's live state on disk: one JSON file per kind, each written whole to a temp file and atomically
 * renamed, so a crash mid-write leaves the previous version intact. Unknown fields are ignored on read, so an
 * older or newer file still loads. History is a capped JSON-lines file of the latest outcomes.
 * Not thread-safe: the engine calls it under its own lock.
 */
public class SchedulerStore {

    static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path dir;
    private final int historyCap;
    private final Map<String, SchedulerAction> actions = new LinkedHashMap<>();
    private final Map<String, SchedulerSchedule> schedules = new LinkedHashMap<>();
    private final Map<String, OpenItem> open = new LinkedHashMap<>();
    private final List<Outcome> history = new ArrayList<>();
    private State state = new State();
    private int skippedHistoryLines;
    private Long historyRollbackTo;

    /** Small flags that must survive a restart. */
    @Data
    public static class State {
        private boolean paused;
        /** Expired outcomes decided at or before this time have been acknowledged. */
        private long expiredAcknowledgedUpTo;
    }

    @Data
    static class Envelope<T> {
        private int schemaVersion = SCHEMA_VERSION;
        private List<T> items = new ArrayList<>();
    }

    public SchedulerStore(Path dir, int historyCap) {
        this.dir = dir;
        this.historyCap = historyCap;
    }

    public void load() {
        try {
            Files.createDirectories(dir);
            for (SchedulerAction a : read("actions.json", new TypeReference<Envelope<SchedulerAction>>() { }).getItems()) actions.put(a.getId(), a);
            for (SchedulerSchedule s : read("schedules.json", new TypeReference<Envelope<SchedulerSchedule>>() { }).getItems()) schedules.put(s.getId(), s);
            for (OpenItem i : read("open.json", new TypeReference<Envelope<OpenItem>>() { }).getItems()) open.put(i.getId(), i);
            Path st = dir.resolve("state.json");
            if (Files.exists(st)) state = JSON.readValue(st.toFile(), State.class);
            Path h = dir.resolve("history.jsonl");
            if (Files.exists(h)) {
                // read leniently: a stop mid-append can cut a line, or a multi-byte character, short
                byte[] bytes = Files.readAllBytes(h);
                String text = new String(bytes, StandardCharsets.UTF_8);
                boolean tornTail = bytes.length > 0 && bytes[bytes.length - 1] != '\n';
                String[] lines = text.split("\n", -1);
                if (tornTail) {
                    skippedHistoryLines++;
                    lines[lines.length - 1] = "";
                }
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    try {
                        history.add(JSON.readValue(line, Outcome.class));
                    } catch (IOException torn) {
                        skippedHistoryLines++; // a line cut short by a crash mid-append: the outcome log still has it
                    }
                }
                trimHistory();
                if (skippedHistoryLines > 0) {
                    rewriteHistory(); // so the next append starts on a clean line
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("scheduler store " + dir + " unreadable", e);
        }
    }

    private <T> Envelope<T> read(String name, TypeReference<Envelope<T>> type) throws IOException {
        Path f = dir.resolve(name);
        if (!Files.exists(f) || Files.size(f) == 0) return new Envelope<>();
        return JSON.readValue(f.toFile(), type);
    }

    public Map<String, SchedulerAction> actions() { return actions; }
    public Map<String, SchedulerSchedule> schedules() { return schedules; }
    public Map<String, OpenItem> open() { return open; }
    public List<Outcome> history() { return history; }
    public State state() { return state; }
    /** History lines that could not be read at load (cut short by a crash). */
    public int skippedHistoryLines() { return skippedHistoryLines; }

    public void saveActions() { write("actions.json", envelope(actions.values())); }
    public void saveSchedules() { write("schedules.json", envelope(schedules.values())); }
    public void saveOpen() { write("open.json", envelope(open.values())); }
    public void saveState() { write("state.json", state); }

    /** Append one outcome; it joins the in-memory history only once it is on disk. */
    public void appendHistory(Outcome outcome) {
        try {
            repairHistoryTail();
            if (history.size() + 1 > historyCap) {
                List<Outcome> next = new ArrayList<>(history);
                next.add(outcome);
                int keep = Math.max(1, historyCap * 4 / 5);
                next = new ArrayList<>(next.subList(next.size() - keep, next.size()));
                writeHistory(next);
                history.clear();
                history.addAll(next);
            } else {
                Path file = dir.resolve("history.jsonl");
                long before = Files.exists(file) ? Files.size(file) : 0;
                try {
                    writeHistoryLine((JSON.writeValueAsString(outcome) + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // A failed append may have written a prefix, or even the entire line. Undo that
                    // attempt before retrying, so it cannot become a malformed or duplicate record.
                    historyRollbackTo = before;
                    try {
                        repairHistoryTail();
                    } catch (IOException rollback) {
                        e.addSuppressed(rollback);
                    }
                    throw e;
                }
                history.add(outcome);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One append, separated so a partial write can be exercised by the persistence tests. */
    protected void writeHistoryLine(byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(dir.resolve("history.jsonl"), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private void repairHistoryTail() throws IOException {
        Path file = dir.resolve("history.jsonl");
        if (!Files.exists(file)) {
            historyRollbackTo = null;
            return;
        }
        try (RandomAccessFile historyFile = new RandomAccessFile(file.toFile(), "rw")) {
            if (historyRollbackTo != null) {
                historyFile.setLength(historyRollbackTo);
                historyFile.getChannel().force(true);
                historyRollbackTo = null;
            }
            long end = historyFile.length();
            long clean = end;
            while (clean > 0) {
                historyFile.seek(clean - 1);
                if (historyFile.read() == '\n') break;
                clean--;
            }
            if (clean != end) {
                historyFile.setLength(clean);
                historyFile.getChannel().force(true);
            }
        }
    }

    private void rewriteHistory() throws IOException {
        writeHistory(history);
    }

    private void writeHistory(List<Outcome> outcomes) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Outcome o : outcomes) sb.append(JSON.writeValueAsString(o)).append('\n');
        atomicWrite("history.jsonl", sb.toString());
    }

    /** Trim to 80% of the cap, so a full history is rewritten once per many appends, not on every one. */
    private void trimHistory() {
        if (history.size() <= historyCap) return;
        int keep = Math.max(1, historyCap * 4 / 5);
        history.subList(0, history.size() - keep).clear();
    }

    private static <T> Envelope<T> envelope(java.util.Collection<T> items) {
        Envelope<T> e = new Envelope<>();
        e.setItems(new ArrayList<>(items));
        return e;
    }

    private void write(String name, Object value) {
        try {
            atomicWrite(name, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void atomicWrite(String name, String content) throws IOException {
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
