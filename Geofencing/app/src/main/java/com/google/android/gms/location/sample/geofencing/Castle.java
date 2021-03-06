package com.google.android.gms.location.sample.geofencing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Klaus on 22.08.2017.
 */

public class Castle {

    private static long[] resources = new long[Waypoint.RES_NAMES.length];

    private static Map<Integer, Waypoint> waypoints = new HashMap<>();

    private static long soldiers = 0;


    /**
     *
     * @param id waypoint nr
     * @return amount
     */
    public static long getRes(int id){
        return resources[id];
    }

    public static void setRes(int id, long amount){
        resources[id] = amount;
    }

    public static long incRes(int id, long amount){
        resources[id] += amount;
        return resources[id];
    }

    public static long[] getResources() {
        return resources;
    }

    public static void setResources(long[] resources) {
        Castle.resources = resources;
    }

    public static Map<Integer, Waypoint> getWaypoints(){
        return waypoints;
    }

    public static void setWaypoints(Map<Integer, Waypoint> waypoints) {
        Castle.waypoints = waypoints;
    }

    public static long getSoldiers() {
        return soldiers;
    }

    public static void setSoldiers(long soldiers) {
        Castle.soldiers = soldiers;
    }

    public static void loadWorld(Context ctx){
        try {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);

            // resources
            for (int i = 0; i < resources.length; i++) {
                resources[i] = p.getLong("RES." + Waypoint.RES_NAMES[i], 0);
            }

            // waypoints
            String wps = p.getString("waypoints", null);
            if (wps == null) {
                ; // nix, müssen später vom user angelegt werden
            } else {
                JSONObject allWPs = new JSONObject(wps);
                JSONArray arr = allWPs.getJSONArray("waypoints");
                for(int i=0; i<arr.length(); i++){
                    JSONObject o = arr.getJSONObject(i);
                    Waypoint w = Waypoint.fromJSONObject(o);
                    waypoints.put(w.getNr(), w);
                }
            }

            // soldiers
            soldiers = p.getLong("soldiers", 0);

        } catch (Exception e) {
            String errortext = "error while loading: " + e.getMessage();
            Log.e("load", errortext);
            Toast.makeText(ctx, errortext, Toast.LENGTH_LONG).show();
        }
    }

    public static void saveWorld(Context ctx){
        try {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            SharedPreferences.Editor editor = sharedPref.edit();

            // resources
            for (int i = 0; i < resources.length; i++) {
                editor.putLong("RES." + Waypoint.RES_NAMES[i], resources[i]);
            }

            // waypoints
            JSONArray arr = new JSONArray();
            for (Waypoint w : waypoints.values()) {
                arr.put(w.toJSONObject());
            }
            JSONObject allWPs = new JSONObject();
            allWPs.put("waypoints", arr);
            editor.putString("waypoints", allWPs.toString());

            // soldiers
            editor.putLong("soldiers", soldiers);

            editor.commit();
        } catch (Exception e) {
            String errortext = "error while saving: " + e.getMessage();
            Log.e("save", errortext);
            Toast.makeText(ctx, errortext, Toast.LENGTH_LONG).show();
        }
    }
}
