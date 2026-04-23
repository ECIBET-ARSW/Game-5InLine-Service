package com.ecibet.game5inline.websocket.dto.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerActionMessage {
    private String action;
    private Long timestamp;
}