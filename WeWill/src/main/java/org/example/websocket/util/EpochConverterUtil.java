package org.example.websocket.util;

import org.apache.commons.lang3.math.NumberUtils;
import org.example.websocket.constant.ApplicationConstants;

public class EpochConverterUtil {

    public static long epochConverter(long inputEpochValue) {
        try {
            if (inputEpochValue > NumberUtils.INTEGER_ZERO){
                return inputEpochValue + ApplicationConstants.NINETY_EIGHTY_CONSTANT;
            }
            return NumberUtils.INTEGER_ZERO;
        } catch (Exception e) {
            e.printStackTrace();
            return inputEpochValue;
        }
    }

}
