package com.fluxtion.server.plugin.rest.service;

public interface RestController {

    <S, T> void addCommand(String command, CommandProcessor<S, T> commandProcessor);
}
