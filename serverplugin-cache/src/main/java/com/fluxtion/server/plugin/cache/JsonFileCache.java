/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Data
@Log4j2
public class JsonFileCache implements Cache, Agent, Lifecycle, EventFlowService {

    private String fileName;
    private final AtomicBoolean updated = new AtomicBoolean(false);
    @Setter(AccessLevel.NONE)
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, TypedData> cacheMap = new ConcurrentHashMap<>();
    private static final TypedData TYPED_DATA_NULL = new TypedData();
    private File file;
    private File redoLogFile;
    private String serviceName;
    private AdminCommandRegistry registry;

    @SneakyThrows
    @Override
    public void init() {
        log.info("init");
        file = new File(fileName);
        if (file.exists() && file.length() > 0) {
            log.info("opened cache file:{}", fileName);
            cacheMap = mapper.readValue(file, new TypeReference<Map<String, TypedData>>() {
            });
            cacheMap.forEach((k, v) -> get(k));
        } else {
            file.getParentFile().mkdirs();
            log.info("no cache file:{} created:{}", fileName, file.createNewFile());
        }
    }

    @ServiceRegistered
    public void register(AdminCommandRegistry registry, String fileName) {
        log.info("Registering admin command registry {}", registry);
        this.registry = registry;
    }

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        log.info("setEventFlowManager serviceName:{}", serviceName);
        this.serviceName = serviceName;
        registry.registerCommand("cache." + serviceName + ".get", this::getCommand);
        registry.registerCommand("cache." + serviceName + ".keys", this::listKeys);
    }

    @Override
    public Collection<String> keys() {
        return cacheMap.keySet();
    }

    @Override
    public void put(String key, Object value) {
        updated.set(true);
        try {
            TypedData typedData = new TypedData();
            typedData.setType(value.getClass());
            typedData.setInstance(value);
            typedData.setData(mapper.writeValueAsString(value));
            cacheMap.put(key, typedData);
            doWork();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T get(String key) {
        TypedData typeData = cacheMap.getOrDefault(key, new TypedData());
        var data = typeData.getData();
        Class<T> clazz = (Class<T>) typeData.getType();
        if (clazz != null && data != null) {
            if (typeData.instance != null) {
                return (T) typeData.instance;
            }
            try {
                T t = mapper.readValue(data, clazz);
                typeData.setInstance(t);
                return t;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @SneakyThrows
    @Override
    public void remove(String key) {
        updated.set(true);
        cacheMap.remove(key);
        doWork();
    }

    @Override
    public int doWork() throws Exception {
        if (updated.get()) {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(fileName), cacheMap);
            log.info("cache updated:{} keys:{}", fileName, cacheMap.keySet().stream().mapToLong(Long::parseLong).sorted().toArray());
        }
        updated.set(false);
        return 0;
    }

    @Override
    public String roleName() {
        return "";
    }

    @SneakyThrows
    @Override
    public void tearDown() {
        mapper.writeValue(new File(fileName), cacheMap);
    }

    private void getCommand(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() >= 2) {
            String key = args.get(1);
            Object data = get(key);
            log.debug("key:{} data:{}", key, data);
            out.accept(key + " -> " + data);
        } else {
            err.accept("provide key as first argument");
        }
    }

    private void listKeys(List<String> args, Consumer<String> out, Consumer<String> err) {
        out.accept("keys:\n" + String.join("\n", cacheMap.keySet()));
    }

    @Data
    public static class TypedData {
        private Class<?> type;
        @JsonIgnore
        private Object instance;
        private String data;
    }
}
