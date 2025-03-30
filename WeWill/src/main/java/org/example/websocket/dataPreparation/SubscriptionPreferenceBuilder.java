package org.example.websocket.dataPreparation;


import org.example.websocket.model.PreferenceDto;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionPreferenceBuilder {

    // ✅ List of scrip IDs to subscribe to — modify this as needed
    private static final List<String> scripIdsToSubscribe = List.of(
            "10753", "4668", "5900", "3045"
    );

    /**
     * Builds a list of PreferenceDto for WebSocket subscription using "FULL" mode.
     * @return List of subscription preferences
     */
    public static List<PreferenceDto> buildPreferences() {
        List<PreferenceDto> preferences = new ArrayList<>();
        for (String scripId : scripIdsToSubscribe) {
            preferences.add(new PreferenceDto("ADD", "FULL", "EQUITY", "NSE", scripId));
        }
        return preferences;
    }
}
