package org.example.websocket.dataPreparation;


import org.example.websocket.model.PreferenceDto;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionPreferenceBuilder {

    // ✅ List of scrip IDs to subscribe to — modify this as needed
    private static final List<String> scripIdsToSubscribe = List.of(
            "3787","3499","10794","1624","10666","14977","18143","4668","4717","2475","11630","5097","27066","1406",
            "14366","8954","1491","12018","11915","24777","21951","383","14428","4973","11538","19084",
            "21401","10576","8080","11006","15032","19543"
    );

    /*"3499","10794","1624","10666","14977","18143","4668","4717"*/

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
