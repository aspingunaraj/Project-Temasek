package org.example.tradeGovernance;

import org.example.Main;
import org.example.tradeGovernance.model.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import java.util.List;
import java.util.Optional; // ← ✅ Required for Optional
import java.util.stream.Collectors;

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
    private static final String ORDER_BOOK_URL = "https://developer.paytmmoney.com/orders/v1/user/orders";


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

    /**
     * Handles intelligent order placement logic for a given symbol:
     * 1. Checks existing position.
     * 2. Compares with trade action (BUY/SELL).
     * 3. Cancels or exits mismatched positions if needed.
     * 4. Ensures no duplicate/pending orders exist before placing.
     */

    public void orderManagement(String securityId, TradeAnalysis.Action action, float ltp) {
        // 1️⃣ Check for existing position in Main.currentPositions
        Optional<Position> existingPositionOpt = Main.currentPositions.stream()
                .filter(pos -> pos.getSecurity_id().equals(securityId))
                .findFirst();

        if (existingPositionOpt.isPresent()) {
            Position pos = existingPositionOpt.get();
            int netQty = pos.getNet_qty();

            // ✅ Case 1: Position exists and matches action (e.g., long position + BUY)
            if ((netQty > 0 && action == TradeAnalysis.Action.BUY) ||
                    (netQty < 0 && action == TradeAnalysis.Action.SELL)) {
                System.out.println("⚖️ Existing position already matches action. No new order needed.");
                return;
            }

            // 🔁 Case 2: Opposite position exists — must exit by placing reverse order
            if ((netQty > 0 && action == TradeAnalysis.Action.SELL) ||
                    (netQty < 0 && action == TradeAnalysis.Action.BUY)) {
                System.out.println("🔁 Exiting opposite position with reverse order.");

                // Reverse transaction type
                String reverseTxnType = (netQty > 0) ? "S" : "B";

                // Create and send reverse market order to square off
                NormalOrderRequest exitOrder = new NormalOrderRequest(
                        reverseTxnType, "NSE", "E", "I", securityId,
                        Math.abs(netQty), // use quantity equal to open position
                        "DAY", "MKT", 0.0, null, "N", "false"
                );

                NormalOrderResponse exitResponse = placeNormalOrder(exitOrder);
                if (exitResponse != null && exitResponse.getMessage() != null) {
                    System.out.println("✅ Exit Order Placed: " + exitResponse.getMessage());
                } else {
                    System.err.println("❌ Failed to exit existing position for " + securityId);
                }

                return; // Exit early to avoid placing new order until position is flattened
            }
        }

        // 2️⃣ No open position — Check if a pending order already exists in order book
        OrderBookResponse orderBook = Main.latestOrderBook;
        if (orderBook != null && orderBook.getData() != null) {
            boolean pendingExists = orderBook.getData().stream()
                    .anyMatch(order ->
                            order.getSecurityId().equals(securityId)
                                    && order.getDisplayStatus() != null
                                    && order.getDisplayStatus().equalsIgnoreCase("Pending")
                    );

            if (pendingExists) {
                System.out.println("⏳ Pending order already exists for " + securityId + ". Skipping placement.");
                return;
            }
        }

        // 3️⃣ No matching position or pending order — Place fresh normal market order
        System.out.println("🚀 No position or pending order. Proceeding to place new normal order.");
        String txnType = (action == TradeAnalysis.Action.BUY) ? "B" : "S";

        NormalOrderRequest request = new NormalOrderRequest(
                txnType, "NSE", "E", "I", securityId, 1,
                "DAY", "MKT", 0.0, null, "N", "false"
        );

        NormalOrderResponse response = placeNormalOrder(request);
        if (response != null && response.getMessage() != null) {
            System.out.println("✅ Order Placed → " + response.getMessage());
        } else {
            System.err.println("❌ Failed to place order for " + securityId);
        }
    }


}
