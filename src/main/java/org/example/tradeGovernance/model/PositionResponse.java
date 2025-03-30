package org.example.tradeGovernance.model;


import lombok.Data;

import java.util.List;

@Data
public class PositionResponse {
    private String status;
    private String message;
    private List<Position> data;
}
