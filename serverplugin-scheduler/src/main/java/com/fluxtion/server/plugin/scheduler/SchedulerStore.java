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
                for (String line : Files.readAllLines(h, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) history.add(JSON.readValue(line, Outcome.class));
                }
                trimHistory();
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

    public void saveActions() { write("actions.json", envelope(actions.values())); }
    public void saveSchedules() { write("schedules.json", envelope(schedules.values())); }
    public void saveOpen() { write("open.json", envelope(open.values())); }
    public void saveState() { write("state.json", state); }

    /** Append one outcome to the capped history (rewritten whole when it outgrows the cap). */
    public void appendHistory(Outcome outcome) {
        history.add(outcome);
        try {
            if (history.size() > historyCap) {
                trimHistory();
                StringBuilder sb = new StringBuilder();
                for (Outcome o : history) sb.append(JSON.writeValueAsString(o)).append('\n');
                atomicWrite("history.jsonl", sb.toString());
            } else {
                Files.writeString(dir.resolve("history.jsonl"), JSON.writeValueAsString(outcome) + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void trimHistory() {
        while (history.size() > historyCap) history.remove(0);
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
