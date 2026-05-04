package com.ecibet.game5inline.websocket;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GameEvent extends ApplicationEvent {
    private final String type;
    private final String lobbyCode;
    private final Object data;

    public GameEvent(Object source, String type, String lobbyCode, Object data) {
        super(source);
        this.type = type;
        this.lobbyCode = lobbyCode;
        this.data = data;
    }
}