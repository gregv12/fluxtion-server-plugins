/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One argument of a catalogue command. A {@code choice} is picked from a list the server answers at run time and
 * may expand to several command arguments (one per name in {@code choicesFrom.values}).
 */
@Data
public class CatalogueArg {

    public enum Type { CHOICE, NUMBER, ENUM, TEXT }

    private String name;
    private Type type = Type.TEXT;
    private String help;
    /** ENUM: the allowed values. */
    private List<String> values = new ArrayList<>();
    /** NUMBER: inclusive bounds, either may be absent. */
    private Double min;
    private Double max;
    /** NUMBER: a whole number only (for commands that parse a long or int). */
    private boolean integer;
    /** CHOICE: where the choices come from. */
    private ChoicesFrom choicesFrom;

    /** How many command arguments this catalogue argument fills. */
    public int width() {
        return type == Type.CHOICE && choicesFrom != null && !choicesFrom.getValues().isEmpty()
                ? choicesFrom.getValues().size() : 1;
    }

    /**
     * An admin command whose answer lists the choices. A JSON answer: {@code path} names the array (dotted, empty =
     * the answer itself), {@code values} the fields of each element, in command-argument order. A plain-text answer:
     * {@code regex} is applied to each line, {@code values} name its named groups (none = the first group, or the
     * whole match); lines that do not match are skipped and repeats are dropped.
     */
    @Data
    public static class ChoicesFrom {
        private String command;
        private String path = "";
        private String regex;
        private List<String> values = new ArrayList<>();
    }
}
