package com.google.android.gms.location.sample.geofencing;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants used
 */

final class Constants {

    private Constants() {
    }

    private static final String PACKAGE_NAME = "com.google.android.gms.location.Geofence";

    static final String GEOFENCES_ADDED_KEY = PACKAGE_NAME + ".GEOFENCES_ADDED_KEY";

    public static final float GEOFENCE_RADIUS_IN_METERS = 150;

    public static final long INITIAL_GROW_DURATION = 8 * 60 * 60 * 1000; // 8h

}
