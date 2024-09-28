/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.jdbc.impl;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.plugin.jdbc.JdbcConnectionLoader;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Data
@Log4j2
public class JdbcConnectionLoaderService implements Lifecycle, JdbcConnectionLoader {

    private Map<String, JdbcConnectionConfig> connections = new HashMap<>();

    @Override
    public void init() {
        log.info("registered connections {}", connections);
    }

    @Override
    public Connection getConnection(String name) throws SQLException {

        Connection conn = null;
        if (connections.containsKey(name)) {
            JdbcConnectionConfig jdbcConnectionConfig = connections.get(name);
            var connUrl = jdbcConnectionConfig.getUrl();
            try {
                conn = DriverManager.getConnection(
                        connUrl,
                        jdbcConnectionConfig.getUsername(),
                        jdbcConnectionConfig.getPassword());
                log.info("Connected to {}", jdbcConnectionConfig);
            } catch (SQLException e) {
                log.error("failed to get jdbc connection:{}", jdbcConnectionConfig, e);
            }
        } else {
            log.warn("no connection registered with name:{}", name);
        }
        return conn;
    }

    @Override
    public void tearDown() {

    }
}
