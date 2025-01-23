package com.fluxtion.server.lib.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Function;

public class JsonSerializer implements Function<Object, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String apply(Object o) {
        try {
            return o == null ? "" : objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
