package com.fluxtion.server.plugin.trading.component.tradevenue;

import com.fluxtion.agrona.concurrent.Agent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractOrderExecutorWorker extends AbstractOrderExecutor implements Agent {

    @Getter
    @Setter
    private String roleName;

    @Override
    public String roleName() {
        return roleName;
    }
}
