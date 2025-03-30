package org.example.websocket.listeners;

import org.example.websocket.model.Tick;

import java.util.ArrayList;

public interface OnMessageListener {
    void onMessage(ArrayList<Tick> response);
}