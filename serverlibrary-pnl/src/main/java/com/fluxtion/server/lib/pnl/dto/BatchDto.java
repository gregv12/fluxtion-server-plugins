/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchDto<T> {
    protected final List<T> batchData = new ArrayList<>();

    public void addBatchItem(T batchItem) {
        batchData.add(batchItem);
    }
}
