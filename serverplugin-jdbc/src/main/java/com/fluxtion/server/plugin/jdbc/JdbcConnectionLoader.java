/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

public interface JdbcConnectionLoader {

    Connection getConnection(String name) throws SQLException;
}
