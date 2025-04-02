package org.example.websocket.dataPreparation;


import org.example.websocket.model.PreferenceDto;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionPreferenceBuilder {

    // ✅ List of scrip IDs to subscribe to — modify this as needed
    private static final List<String> scripIdsToSubscribe = List.of(
            "3787","2475"
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
