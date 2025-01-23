package com.fluxtion.server.lib.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.util.function.Function;

@Data
@Log4j2
public class JsonDeserializer implements Function<String, Object> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Class<?> clazz;

    @SneakyThrows
    public JsonDeserializer(String className) {
        clazz = Class.forName(className);
    }

    @Override
    public Object apply(String s) {
        try {
            Object value = mapper.readValue(s, clazz);
            log.debug("Deserialized " + value);
            return value;
        } catch (JsonProcessingException e) {
            log.error(e);
        }
        return null;
    }
}
