/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The allow-list: the commands the scheduler may run, their argument types and where choices come from. Only a
 * live command matching an entry can become an action.
 */
@Data
public class Catalogue {

    private static final ObjectMapper YAML = YAMLMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The scheduler's own command prefix: never schedulable and never a choices source (it would call back in). */
    static final String SELF = "scheduler.";

    private List<CatalogueEntry> commands = new ArrayList<>();

    public static Catalogue load(Path file) throws IOException {
        Catalogue catalogue = YAML.readValue(Files.readAllBytes(file), Catalogue.class);
        catalogue.check();
        return catalogue;
    }

    public static Catalogue parse(String yaml) throws IOException {
        Catalogue catalogue = YAML.readValue(yaml, Catalogue.class);
        catalogue.check();
        return catalogue;
    }

    /** Structural checks a typo would otherwise hide until someone tries to schedule the command. */
    void check() {
        for (CatalogueEntry e : commands) {
            if (e.getCommand() == null || e.getCommand().isBlank()) {
                throw new IllegalArgumentException("catalogue entry without a command");
            }
            if (e.getCommand().startsWith(SELF)) {
                throw new IllegalArgumentException(e.getCommand() + ": the scheduler's own commands cannot be scheduled");
            }
            for (CatalogueArg a : e.getArguments()) {
                if (a.getType() == CatalogueArg.Type.ENUM && a.getValues().isEmpty()) {
                    throw new IllegalArgumentException(e.getCommand() + ": enum argument '" + a.getName() + "' has no values");
                }
                if (a.getType() == CatalogueArg.Type.CHOICE && (a.getChoicesFrom() == null || a.getChoicesFrom().getCommand() == null)) {
                    throw new IllegalArgumentException(e.getCommand() + ": choice argument '" + a.getName() + "' has no choicesFrom.command");
                }
                if (a.getType() == CatalogueArg.Type.CHOICE && a.getChoicesFrom().getRegex() != null) {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(a.getChoicesFrom().getRegex());
                    for (String group : a.getChoicesFrom().getValues()) {
                        if (!p.pattern().contains("(?<" + group + ">")) {
                            throw new IllegalArgumentException(e.getCommand() + ": choice argument '" + a.getName()
                                    + "' regex has no named group '" + group + "'");
                        }
                    }
                }
            }
        }
    }

    /** A live command matched to its catalogue entry. */
    public record Match(CatalogueEntry entry, String command, Map<String, String> params) { }

    /** The entry a command name matches, or {@code null}. First matching entry wins. */
    public Match find(String commandName) {
        if (commandName == null || commandName.startsWith(SELF)) {
            return null;
        }
        for (CatalogueEntry e : commands) {
            Map<String, String> params = e.match(commandName);
            if (params != null) {
                return new Match(e, commandName, params);
            }
        }
        return null;
    }

    /** Every live command that matches an entry, in live-list order. */
    public List<Match> matches(Collection<String> liveCommands) {
        List<Match> out = new ArrayList<>();
        for (String c : liveCommands) {
            Match m = find(c);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    /** Entries with no live command at all: the catalogue names something this server does not register. */
    public List<String> unmatchedEntries(Collection<String> liveCommands) {
        List<String> out = new ArrayList<>();
        for (CatalogueEntry e : commands) {
            if (liveCommands.stream().noneMatch(c -> e.match(c) != null)) {
                out.add(e.getCommand());
            }
        }
        return out;
    }

    /**
     * The choices for a CHOICE argument: runs its {@code choicesFrom.command} (placeholders filled from the match)
     * through {@code runCommand} and reads each element's {@code values} fields from the JSON answer.
     */
    public static List<List<String>> choices(Match match, CatalogueArg arg, Function<String, String> runCommand) throws IOException {
        CatalogueArg.ChoicesFrom from = arg.getChoicesFrom();
        String source = CatalogueEntry.fill(from.getCommand(), match.params());
        if (source.startsWith(SELF)) {
            throw new IllegalArgumentException("choices cannot come from the scheduler's own command " + source);
        }
        String answer = runCommand.apply(source);
        if (from.getRegex() != null && !from.getRegex().isBlank()) {
            return textChoices(answer, from);
        }
        JsonNode node = JSON.readTree(answer);
        if (from.getPath() != null && !from.getPath().isBlank()) {
            for (String part : from.getPath().split("\\.")) {
                node = node == null ? null : node.get(part);
            }
        }
        List<List<String>> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode element : node) {
            List<String> tuple = new ArrayList<>();
            if (from.getValues().isEmpty()) {
                tuple.add(element.isValueNode() ? element.asText() : element.toString());
            } else {
                for (String field : from.getValues()) {
                    JsonNode v = element.get(field);
                    tuple.add(v == null || v.isNull() ? "" : v.asText());
                }
            }
            out.add(tuple);
        }
        return out;
    }

    private static List<List<String>> textChoices(String answer, CatalogueArg.ChoicesFrom from) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(from.getRegex());
        java.util.LinkedHashSet<List<String>> out = new java.util.LinkedHashSet<>();
        long deadline = System.nanoTime() + REGEX_BUDGET_NANOS;
        for (String line : answer == null ? new String[0] : answer.split("\\R")) {
            java.util.regex.Matcher m = pattern.matcher(new Deadline(line, deadline));
            if (!m.find()) continue;
            List<String> tuple = new ArrayList<>();
            if (from.getValues().isEmpty()) {
                String v = m.groupCount() > 0 ? m.group(1) : m.group();
                tuple.add(v == null ? "" : v.trim());
            } else {
                for (String group : from.getValues()) {
                    String v = m.group(group); // null when an optional group did not take part
                    tuple.add(v == null ? "" : v.trim());
                }
            }
            out.add(tuple);
        }
        return new ArrayList<>(out);
    }

    /** All lines of one answer must be matched within this time: a pathological regex fails, it does not hang. */
    static final long REGEX_BUDGET_NANOS = 2_000_000_000L;

    /**
     * Text that stops a regex match once the deadline has passed. The JDK matcher reads it char by char, so the
     * clock is checked every 1024 reads, whichever positions backtracking revisits.
     */
    private record Deadline(CharSequence text, long deadline, long[] reads) implements CharSequence {
        Deadline(CharSequence text, long deadline) {
            this(text, deadline, new long[1]);
        }

        @Override
        public char charAt(int index) {
            if ((++reads[0] & 0x3FF) == 0 && System.nanoTime() > deadline) {
                throw new IllegalStateException("choices regex took longer than " + REGEX_BUDGET_NANOS / 1_000_000 + "ms");
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new Deadline(text.subSequence(start, end), deadline, reads);
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }

    /**
     * Why {@code args} are not valid for the matched entry, or {@code null} when they are. CHOICE arguments are
     * checked against {@code choicesFor} when given (null skips that check).
     */
    public static String validate(Match match, List<String> args, Function<CatalogueArg, List<List<String>>> choicesFor) {
        CatalogueEntry e = match.entry();
        if (args.size() != e.argumentCount()) {
            return match.command() + " takes " + e.argumentCount() + " argument(s), got " + args.size();
        }
        int i = 0;
        for (CatalogueArg a : e.getArguments()) {
            List<String> slice = args.subList(i, i + a.width());
            i += a.width();
            String value = slice.get(0);
            switch (a.getType()) {
                case NUMBER -> {
                    double d;
                    try {
                        d = Double.parseDouble(value);
                    } catch (NumberFormatException ex) {
                        return a.getName() + " must be a number, got '" + value + "'";
                    }
                    if (Double.isNaN(d) || Double.isInfinite(d)) return a.getName() + " must be a finite number, got '" + value + "'";
                    if (a.isInteger() && !value.trim().matches("-?\\d+")) return a.getName() + " must be a whole number, got '" + value + "'";
                    if (a.getMin() != null && d < a.getMin()) return a.getName() + " must be >= " + a.getMin();
                    if (a.getMax() != null && d > a.getMax()) return a.getName() + " must be <= " + a.getMax();
                }
                case ENUM -> {
                    if (!a.getValues().contains(value)) return a.getName() + " must be one of " + a.getValues() + ", got '" + value + "'";
                }
                case CHOICE -> {
                    if (choicesFor != null) {
                        List<List<String>> choices = choicesFor.apply(a);
                        if (choices != null && choices.isEmpty()) {
                            // an empty list can mean "anything" to the command; a schedulable action needs an explicit one
                            return a.getName() + ": the server offers no choices (" + a.getChoicesFrom().getCommand()
                                    + " returned an empty " + a.getChoicesFrom().getPath() + "), so none can be scheduled";
                        }
                        if (choices != null && !choices.contains(slice)) {
                            return a.getName() + " " + slice + " is not one of the server's choices " + choices;
                        }
                    }
                }
                case TEXT -> {
                    if (value == null || value.isBlank()) return a.getName() + " must not be blank";
                }
            }
        }
        return null;
    }
}
