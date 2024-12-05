/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.pnl.dto;

import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SymbolDto {

    private String symbol;
    private String dealt;
    private String contra;

    public Symbol toSymbol() {
        return new Symbol(symbol, dealt, contra);
    }
}
