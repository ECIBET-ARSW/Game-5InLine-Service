package com.ecibet.game5inline.websocket.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountdownTickMessage {
    private String type;
    private Integer count;

    public static CountdownTickMessage from(Integer count) {
        return CountdownTickMessage.builder()
                .type("COUNTDOWN_TICK")
                .count(count)
                .build();
    }
}