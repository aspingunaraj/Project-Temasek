package org.example.websocket.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceDto {
    private String actionType;
    private String modeType;
    private String scripType;
    private String exchangeType;
    private String scripId;
}

