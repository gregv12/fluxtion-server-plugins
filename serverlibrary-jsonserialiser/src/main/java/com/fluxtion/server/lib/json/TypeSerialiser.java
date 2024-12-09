/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.server.batch.BatchDto;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Log4j2
public class TypeSerialiser implements Function<List<String>, Object> {

    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public Object apply(List<String> lines) {
        String lineInput = "NULL";
        if (lines.size() == 0) {
            return null;
        }
        if (lines.size() == 1) {
            lineInput = lines.get(0);
            Object dto = toObject(lineInput);
            log.debug("dto  - {}", dto);
            return dto;
        } else {
            BatchDto tradeBatchDTO = new BatchDto();
            for (String line : lines) {
                lineInput = line;
                tradeBatchDTO.addBatchItem(toObject(lineInput));
            }
            return tradeBatchDTO;
        }
    }

    public Object toObject(String json) {
        // convert JSON string to Map
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (map.containsKey("type")) {
                Class<?> type = Class.forName((String) map.get("type"));
                return objectMapper.readValue(json, type);
            } else {
                return map;
            }
        } catch (JsonProcessingException | ClassNotFoundException e) {
            log.error("{} error processing input:'{}' error message:{}", getClass().getName(), json, e.getMessage());
            return null;
        }
    }
}
