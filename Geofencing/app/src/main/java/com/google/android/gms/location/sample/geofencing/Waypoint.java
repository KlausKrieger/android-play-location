package com.google.android.gms.location.sample.geofencing;

import android.location.Location;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Created by Klaus on 22.08.2017.
 */

public class Waypoint {

    public static final String[] RES_NAMES = {"Gold", "Holz", "Stein", "Eisen"};

    public static final String[] UPG_NAMES = {"Fahne", "Wachhäuschen", "Wachgebäude", "Wachgebäude mit Palisaden", "Kaserne", "Burg", "Festung", "Drachenhort"};

    public static final String[] DEFAULT_NAMES = {"Solace", "Düsterwald", "Hohe Klamm", "Eherne Minen"};

    private int nr;
    private String name;
    private LatLng koords;
    private Date lastVisitDate = null;
    private long lastHarvestTime = 0;
    private long visitCounts = 0;
    private long growDuration = Constants.INITIAL_GROW_DURATION;
    private long storageCap = 3;
    private int upgrades = 0;

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
                ", upgrades=" + upgrades +
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
        ob.put("upgrades", getUpgrades());
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
        w.setUpgrades(o.getInt("upgrades"));
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

    public String getGrowDurationAsString(){
        long h = growDuration / 1000 / 60 / 60;
        long m = growDuration / 1000 / 60 % 60;
        return h + "h " + m + "min";
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

    public int getUpgrades() {
        return upgrades;
    }

    public void setUpgrades(int upgrades) {
        this.upgrades = upgrades;
    }

    public static String[] getUpgNames() {
        return UPG_NAMES;
    }

    public float distanceInMeters(Location loc){
        if (loc != null) {
            float[] erg = new float[1];
            Location.distanceBetween(koords.latitude, koords.longitude, loc.getLatitude(), loc.getLongitude(), erg);
            return erg[0];
        } else {
            return Float.MAX_VALUE; // loc not known
        }
    }
    public float distanceInMeters(Waypoint other){
        float[] erg = new float[1];
        Location.distanceBetween(koords.latitude, koords.longitude, other.getKoords().latitude, other.getKoords().longitude, erg);
        return erg[0];
    }
}
