/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command the scheduler may run. {@code command} is a name pattern: {@code {placeholder}} segments match one
 * dotted segment of a live command name, e.g. {@code strat.{strategy}.setPriceSource}.
 */
@Data
public class CatalogueEntry {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");

    private String command;
    private String title;
    private String description;
    private List<CatalogueArg> arguments = new ArrayList<>();

    /** The placeholder values when {@code commandName} matches this entry, else {@code null}. */
    public Map<String, String> match(String commandName) {
        if (command == null || commandName == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        StringBuilder regex = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(command);
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(command.substring(last, m.start())));
            regex.append("([^.\\s]+)");
            names.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(command.substring(last)));
        Matcher cm = Pattern.compile(regex.toString()).matcher(commandName);
        if (!cm.matches()) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            params.put(names.get(i), cm.group(i + 1));
        }
        return params;
    }

    /** {@code template} with each {@code {placeholder}} replaced from {@code params}. */
    public static String fill(String template, Map<String, String> params) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = params.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Number of command arguments the entry takes. */
    public int argumentCount() {
        return arguments.stream().mapToInt(CatalogueArg::width).sum();
    }
}
