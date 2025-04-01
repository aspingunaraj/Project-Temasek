package org.example.tradeGovernance;

import org.example.Main;
import org.example.tradeGovernance.model.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Handles communication with Paytm Money order-related APIs.
 * Supports placing and cancelling both bracket and normal orders,
 * as well as fetching the full order book.
 */
public class OrderServices {

    // --- Paytm Money API Endpoints ---
    private static final String BRACKET_ORDER_URL = "https://developer.paytmmoney.com/orders/v1/place/bracket";
    private static final String NORMAL_ORDER_URL = "https://developer.paytmmoney.com/orders/v1/place/regular";
    private static final String CANCEL_ORDER_URL = "https://developer.paytmmoney.com/orders/v1/cancel/regular";
    private static final String ORDER_BOOK_URL = "https://developer.paytmmoney.com/orders/v1/orders";

    private final RestTemplate restTemplate;

    /**
     * Initializes RestTemplate with HTTP Client Factory (supports PATCH, timeouts, etc.)
     */
    public OrderServices() {
        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

    /**
     * Places a bracket order with profit and stop-loss legs.
     *
     * @param request BracketOrderRequest object with all order params
     * @return BracketOrderResponse from the server
     */
    public BracketOrderResponse placeBracketOrder(BracketOrderRequest request) {
        try {
            System.out.println("📤 Placing Bracket Order → " + request.getSecurityId());
            HttpEntity<BracketOrderRequest> entity = buildEntity(request);

            ResponseEntity<BracketOrderResponse> response = restTemplate.exchange(
                    BRACKET_ORDER_URL,
                    HttpMethod.POST,
                    entity,
                    BracketOrderResponse.class
            );

            BracketOrderResponse body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                System.out.println("✅ Bracket Order Placed: " + body.getMessage());
                return body;
            } else {
                System.out.println("⚠️ Bracket Order Failed - Status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Bracket Order Exception: " + e.getMessage());
        }
        return null;
    }

    /**
     * Places a regular (market/limit/SL) order.
     *
     * @param request NormalOrderRequest object
     * @return NormalOrderResponse returned from Paytm Money
     */
    public NormalOrderResponse placeNormalOrder(NormalOrderRequest request) {
        try {
            System.out.println("📤 Placing Normal Order → " + request.getSecurityId() + " | Type: " + request.getOrderType());
            HttpEntity<NormalOrderRequest> entity = buildEntity(request);

            ResponseEntity<NormalOrderResponse> response = restTemplate.exchange(
                    NORMAL_ORDER_URL,
                    HttpMethod.POST,
                    entity,
                    NormalOrderResponse.class
            );

            NormalOrderResponse body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                System.out.println("✅ Normal Order Placed: " + body.getMessage());
                return body;
            } else {
                System.out.println("⚠️ Normal Order Failed - Status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Normal Order Exception: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetches the current state of all placed orders (open, pending, executed).
     *
     * @return OrderBookResponse containing a list of all orders
     */
    public OrderBookResponse getOrderBook(String jwtToken) {
        try {
            System.out.println("🔄 Fetching Order Book...");
            HttpHeaders headers = buildHeaders();
            headers.set("x-jwt-token", jwtToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<OrderBookResponse> response = restTemplate.exchange(
                    ORDER_BOOK_URL,
                    HttpMethod.GET,
                    entity,
                    OrderBookResponse.class
            );


            OrderBookResponse body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                System.out.println("📘 Order Book Fetched: " + body.getData().size() + " orders");
                return body;
            } else {
                System.out.println("⚠️ Order Book Fetch Failed - Status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Order Book Exception: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cancels a previously placed normal order using order_no, serial_no, group_id.
     *
     * @param request OrderCancelRequest containing order identifiers
     * @return OrderCancelResponse indicating success/failure
     */
    public OrderCancelResponse cancelNormalOrder(OrderCancelRequest request) {
        try {
            System.out.println("🚫 Sending Cancel Request for Order: " + request.getOrderNo());
            HttpEntity<OrderCancelRequest> entity = buildEntity(request);

            ResponseEntity<OrderCancelResponse> response = restTemplate.exchange(
                    CANCEL_ORDER_URL,
                    HttpMethod.POST,
                    entity,
                    OrderCancelResponse.class
            );

            OrderCancelResponse body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                System.out.println("✅ Cancel Request Submitted: " + body.getMessage());
                return body;
            } else {
                System.out.println("⚠️ Cancel Request Failed - Status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Cancel Order Exception: " + e.getMessage());
        }
        return null;
    }

    /**
     * Builds HTTP headers with JWT token and content type.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-jwt-token", Main.accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Generic utility to wrap request body with headers.
     */
    private <T> HttpEntity<T> buildEntity(T body) {
        return new HttpEntity<>(body, buildHeaders());
    }
}
