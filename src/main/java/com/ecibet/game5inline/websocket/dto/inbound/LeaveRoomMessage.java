package com.ecibet.game5inline.websocket.dto.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRoomMessage {
    private String lobbyCode;
}
