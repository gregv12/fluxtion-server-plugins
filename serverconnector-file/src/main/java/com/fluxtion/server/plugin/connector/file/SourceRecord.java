package com.fluxtion.server.plugin.connector.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SourceRecord <T>{
    private String topic;
    private T value;
}
