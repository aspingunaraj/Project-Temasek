package org.example.logger;

import java.io.PrintStream;

/**
 * Custom PrintStream that redirects System.out to both:
 * 1. the original console output
 * 2. WebSocket clients connected to /logs-stream
 */
public class WebSocketLogRedirector extends PrintStream {

    private static final PrintStream ORIGINAL_OUT = System.out; // Save the real System.out

    public WebSocketLogRedirector() {
        super(System.out); // Needed but we’ll override println
    }

    @Override
    public void println(String message) {
        // 🔁 Broadcast to WebSocket clients
        LogWebSocketHandler.broadcast(message);

        // ✅ Also print to actual console
        ORIGINAL_OUT.println(message);
    }

    @Override
    public void print(String message) {
        // For print() calls (if needed)
        LogWebSocketHandler.broadcast(message);
        ORIGINAL_OUT.print(message);
    }
}
