/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.jdbc.impl;

import lombok.Data;
import lombok.ToString;

@Data
public class JdbcConnectionConfig {

    private String url;
    private String username;
    @ToString.Exclude
    private String password;

    public String getUsername() {
        if (username != null && username.startsWith("$ENV.")) {
            return System.getProperty(username);
        }
        return username;
    }

    public String getPassword() {
        if (password != null && password.startsWith("$ENV.")) {
            return System.getProperty(password);
        }
        return password;
    }
}
