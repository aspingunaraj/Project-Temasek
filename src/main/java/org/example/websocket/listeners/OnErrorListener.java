package org.example.websocket.listeners;

public interface OnErrorListener {
    void onError(String errorMessage);
    void onError(Exception e);
    void onError(RuntimeException re);
}