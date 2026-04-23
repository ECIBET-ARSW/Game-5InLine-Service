package com.ecibet.game5inline.websocket.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountdownStartMessage {
    private String type;
    private Integer count;

    public static CountdownStartMessage from(Integer count) {
        return CountdownStartMessage.builder()
                .type("COUNTDOWN_START")
                .count(count)
                .build();
    }
}