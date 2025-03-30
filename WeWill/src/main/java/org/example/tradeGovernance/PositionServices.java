package org.example.tradeGovernance;

import org.example.tradeGovernance.model.Position;
import org.example.tradeGovernance.model.PositionResponse;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

public class PositionServices {

    public List<Position> getPositions(String jwtToken) {
        try {
            String url = "https://developer.paytmmoney.com/orders/v1/position";

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-jwt-token", jwtToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<PositionResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    PositionResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getData();
            } else {
                System.err.println("⚠️ Failed to fetch positions. Status: " + response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            System.err.println("❌ Exception in getPositions(): " + e.getMessage());
            return null;
        }
    }
}
