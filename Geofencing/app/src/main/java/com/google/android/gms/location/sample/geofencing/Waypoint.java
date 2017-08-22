package com.google.android.gms.location.sample.geofencing;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Created by Klaus on 22.08.2017.
 */

public class Waypoint {

    public static final float GEOFENCE_RADIUS_IN_METERS = 100;

    public static final String[] RES_NAMES = {"Gold", "Holz", "Stein", "Eisen"};

    private int nr;
    private String name;
    private LatLng koords;
    private Date lastVisitDate = null;
    private long lastHarvestTime = 0;
    private long visitCounts = 0;
    private long growDuration = 20 * 1000; // 20sec
    private long storageCap = 3;

    public Waypoint(int nr, String name, LatLng koords){
        this.nr = nr;
        this.name = name;
        this.koords = koords;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Waypoint waypoint = (Waypoint) o;
        return nr == waypoint.nr;
    }

    @Override
    public int hashCode() {
        return nr;
    }

    @Override
    public String toString() {
        return "Waypoint{" +
                "nr=" + nr +
                ", name='" + name + '\'' +
                ", koords=" + koords +
                ", lastVisitDate=" + lastVisitDate +
                ", visitCounts=" + visitCounts +
                ", growDuration=" + growDuration/1000 + "sec" +
                '}';
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject ob = new JSONObject();
        ob.put("nr", getNr());
        ob.put("name", getName());
        ob.put("growDuration", getGrowDuration());
        ob.put("latitude", getKoords().latitude);
        ob.put("longitude", getKoords().longitude);
        ob.put("lastHarvestTime", getLastHarvestTime());
        if(lastVisitDate != null){
            ob.put("lastVisitDate", getLastVisitDate().getTime());
        }else {
            ob.put("lastVisitDate", -1);
        }
        ob.put("storageCap", getStorageCap());
        ob.put("visitCounts", getVisitCounts());
        return ob;
    }
    public static Waypoint fromJSONObject(JSONObject o) throws JSONException {
        Waypoint w = new Waypoint(o.getInt("nr"), o.getString("name"), new LatLng(o.getDouble("latitude"), o.getDouble("longitude")));
        w.setGrowDuration(o.getLong("growDuration"));
        w.setLastHarvestTime(o.getLong("lastHarvestTime"));

        long lvd = o.getLong("lastVisitDate");
        if (lvd > -1) {
            w.setLastVisitDate(new Date(o.getLong("lastVisitDate")));
        } else {
            w.setLastVisitDate(null);
        }
        w.setStorageCap(o.getLong("storageCap"));
        w.setVisitCounts(o.getLong("visitCounts"));
        return w;
    }

    public static Waypoint fromGeofence(Geofence g){
        return Castle.getWaypoints().get(Integer.parseInt(g.getRequestId()));
    }

    public long calcStorage(){
        long inStorage = (System.currentTimeMillis()-lastHarvestTime)/growDuration;
        inStorage = inStorage>storageCap?storageCap:inStorage; // cap
        return inStorage;
    }

    public long harvest(){
        long harvest = calcStorage();

        // reset last Harvest Time
        if (harvest == storageCap) {
            lastHarvestTime = System.currentTimeMillis();
        } else {
            lastHarvestTime += harvest * growDuration;
        }

        return harvest;
    }

    public int getNr() {
        return nr;
    }

    public void setNr(int nr) {
        this.nr = nr;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LatLng getKoords() {
        return koords;
    }

    public void setKoords(LatLng koords) {
        this.koords = koords;
    }

    public long getVisitCounts() {
        return visitCounts;
    }

    public void setVisitCounts(long visitCounts) {
        this.visitCounts = visitCounts;
    }

    public Date getLastVisitDate() {
        return lastVisitDate;
    }

    public void setLastVisitDate(Date lastVisitDate) {
        this.lastVisitDate = lastVisitDate;
    }

    public long getLastHarvestTime() {
        return lastHarvestTime;
    }

    public void setLastHarvestTime(long lastHarvestTime) {
        this.lastHarvestTime = lastHarvestTime;
    }

    public long getGrowDuration() {
        return growDuration;
    }

    public void setGrowDuration(long growDuration) {
        this.growDuration = growDuration;
    }

    public long getStorageCap() {
        return storageCap;
    }

    public void setStorageCap(long storageCap) {
        this.storageCap = storageCap;
    }
}