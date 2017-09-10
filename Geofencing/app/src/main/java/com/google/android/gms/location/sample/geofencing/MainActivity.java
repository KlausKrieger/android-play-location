/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.location.sample.geofencing;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Map;

/**
 * Demonstrates how to create and remove geofences using the GeofencingApi. Uses an IntentService
 * to monitor geofence transitions and creates notifications whenever a device enters or exits
 * a geofence.
 * <p>
 * This sample requires a device's Location settings to be turned on. It also requires
 * the ACCESS_FINE_LOCATION permission, as specified in AndroidManifest.xml.
 * <p>
 */
public class MainActivity extends AppCompatActivity implements OnCompleteListener<Void> {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    /**
     * Tracks whether the user requested to add or remove geofences, or to do neither.
     */
    private enum PendingGeofenceTask {
        ADD, REMOVE, NONE
    }

    /**
     * Provides access to the Geofencing API.
     */
    private GeofencingClient mGeofencingClient;

    /**
     * The list of geofences used in this sample.
     */
    private ArrayList<Geofence> mGeofenceList;

    /**
     * Used when requesting to add or remove geofences.
     */
    private PendingIntent mGeofencePendingIntent;

    // Buttons for kicking off the process of adding or removing geofences.
    private Button mAddGeofencesButton;
    private Button mRemoveGeofencesButton;

    // Buttons for build/upgrade castle and towers
    private Button mCastleButton;
    private Button mSoldierButton;
    private Button mTower1Button;
    private Button mTower2Button;
    private Button mTower3Button;

    private PendingGeofenceTask mPendingGeofenceTask = PendingGeofenceTask.NONE;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private SettingsClient mSettingsClient;
    private LocationSettingsRequest mLocationSettingsRequest;
    private Location mCurrentLocation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        // TODO und location updates ausschalten oder abschwächen, sobald alle gebaut sind

        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(4 * 1000); // in ms
        mLocationRequest.setFastestInterval(2 * 1000);
        // TODO weitere Parameter für request ?
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    ((TextView) findViewById(R.id.curLocTextView)).setText("you are at "
                    + location.getLatitude() + " " + location.getLongitude() + " +/-" + location.getAccuracy() + "m");
                    mCurrentLocation = location;
                }
            };
        };


        Castle.loadWorld(this);

        // Get the UI widgets.
        mAddGeofencesButton = (Button) findViewById(R.id.add_geofences_button);
        mRemoveGeofencesButton = (Button) findViewById(R.id.remove_geofences_button);
        mCastleButton = (Button) findViewById(R.id.castleButton);
        mSoldierButton = (Button) findViewById(R.id.soldierButton);
        mTower1Button = (Button) findViewById(R.id.tower1Button);
        mTower2Button = (Button) findViewById(R.id.tower2Button);
        mTower3Button = (Button) findViewById(R.id.tower3Button);

        // Empty list for storing geofences.
        mGeofenceList = new ArrayList<>();

        // Initially set the PendingIntent used in addGeofences() and removeGeofences() to null.
        mGeofencePendingIntent = null;

        // Get the geofences used. Geofence data is hard coded in this sample.
        populateGeofenceList();

       mGeofencingClient = LocationServices.getGeofencingClient(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!checkPermissions()) {
            requestPermissions();
        } else {
            performPendingGeofenceTask();
        }



        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "User chose not to make required location settings changes.");
                        Toast.makeText(this, "dann wirds wohl nix mit uns...", Toast.LENGTH_LONG).show();
                        break;
                }
                break;
        }
    }

    private void stopLocationUpdates() {

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        setButtonsEnabledState();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Within {@code onPause()}, we remove location updates. Here, we resume receiving
        // location updates if the user has requested them.
        if (checkPermissions()) {
            startLocationUpdates();
        } else if (!checkPermissions()) {
            requestPermissions();
        }
        updateUI();
    }

    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        //noinspection MissingPermission
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());

                        updateUI();
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                //mRequestingLocationUpdates = false; // TODO aufhören mit locationupdates
                        }

                        updateUI();
                    }
                });
    }

    private void updateUI(){

        setButtonsEnabledState();

        // homebase text
        String castleStatus = "Hauptsitz: ";
        if(Castle.getWaypoints().isEmpty()){
            castleStatus += "- nicht erstellt -";
        } else {
            Waypoint w = Castle.getWaypoints().get(0);
            castleStatus += Waypoint.UPG_NAMES[w.getUpgrades()];
            castleStatus += "\n\"" + w.getName() + "\"\n";
            castleStatus += "bereit liegende Steuern: " + w.calcStorage() + "/" + w.getStorageCap() + " Gold.";
        }
        // resources in home-storage
        castleStatus += "\n\nRessourcen:";
        for(int i=0; i<Waypoint.RES_NAMES.length; i++){
            if(i%2==0){
                castleStatus+="\n";
            }else{
                castleStatus+="\t\t\t";
            }
            castleStatus += Waypoint.RES_NAMES[i] + ": " + Castle.getRes(i);
        }
        castleStatus += "\n";
        TextView castleTextView = (TextView) findViewById(R.id.castleTextView);
        castleTextView.setText(castleStatus);

        // soldiers
        TextView soldierTextView = (TextView) findViewById(R.id.soldierTextView);
        soldierTextView.setText("Soldaten: " + Castle.getSoldiers());

        // outpost 1
        TextView tower1TextView = (TextView) findViewById(R.id.tower1TextView);
        String tower1Status = "";
        if (Castle.getWaypoints().size() >=2){
            Waypoint w = Castle.getWaypoints().get(1);
            tower1Status += "\"" + w.getName() + "\"\n";
            tower1Status += "Wald gesichert durch: ";
            tower1Status += Waypoint.UPG_NAMES[w.getUpgrades()];
            tower1Status += "\nlagernd: " + w.calcStorage() + "/" + w.getStorageCap() + " " + Waypoint.RES_NAMES[1] + "\n";
        } else {
            tower1Status += "- Wald -";
        }
        tower1TextView.setText(tower1Status);

        // outpost 2
        TextView tower2TextView = (TextView) findViewById(R.id.tower2TextView);
        String tower2Status = "";
        if (Castle.getWaypoints().size() >=3){
            Waypoint w = Castle.getWaypoints().get(2);
            tower2Status += "\"" + w.getName() + "\"\n";
            tower2Status += "Steinbruch gesichert durch: ";
            tower2Status += Waypoint.UPG_NAMES[w.getUpgrades()];
            tower2Status += "\nlagernd: " + w.calcStorage() + "/" + w.getStorageCap() + " " + Waypoint.RES_NAMES[2] + "\n";
        } else {
            tower2Status += "- Steinbruch -";
        }
        tower2TextView.setText(tower2Status);

        // outpost 3
        TextView tower3TextView = (TextView) findViewById(R.id.tower3TextView);
        String tower3Status = "";
        if (Castle.getWaypoints().size() >=4){
            Waypoint w = Castle.getWaypoints().get(3);
            tower3Status += "\"" + w.getName() + "\"\n";
            tower3Status += "Minen gesichert durch: ";
            tower3Status += Waypoint.UPG_NAMES[w.getUpgrades()];
            tower3Status += "\nlagernd: " + w.calcStorage() + "/" + w.getStorageCap() + " " + Waypoint.RES_NAMES[3] + "\n";
        } else {
            tower3Status += "- Minen -";
        }
        tower3TextView.setText(tower3Status);



    }

    /**
     * Builds and returns a GeofencingRequest. Specifies the list of geofences to be monitored.
     * Also specifies how the geofence notifications are initially triggered.
     */
    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();

        // The INITIAL_TRIGGER_ENTER flag indicates that geofencing service should trigger a
        // GEOFENCE_TRANSITION_ENTER notification when the geofence is added and if the device
        // is already inside that geofence.
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);

        // Add the geofences to be monitored by geofencing service.
        builder.addGeofences(mGeofenceList);

        // Return a GeofencingRequest.
        return builder.build();
    }

    /**
     * Adds geofences, which sets alerts to be notified when the device enters or exits one of the
     * specified geofences. Handles the success or failure results returned by addGeofences().
     */
    public void addGeofencesButtonHandler(View view) {
        if (!checkPermissions()) {
            mPendingGeofenceTask = PendingGeofenceTask.ADD;
            requestPermissions();
            return;
        }
        addGeofences();
    }

    /**
     * Adds geofences. This method should be called after the user has granted the location
     * permission.
     */
    @SuppressWarnings("MissingPermission")
    private void addGeofences() {
        if (!checkPermissions()) {
            showSnackbar(getString(R.string.insufficient_permissions));
            return;
        }

        mGeofencingClient.addGeofences(getGeofencingRequest(), getGeofencePendingIntent())
                .addOnCompleteListener(this);
    }

    /**
     * Removes geofences, which stops further notifications when the device enters or exits
     * previously registered geofences.
     */
    public void removeGeofencesButtonHandler(View view) {
        if (!checkPermissions()) {
            mPendingGeofenceTask = PendingGeofenceTask.REMOVE;
            requestPermissions();
            return;
        }
        removeGeofences();
    }

    /**
     * Removes geofences. This method should be called after the user has granted the location
     * permission.
     */
    @SuppressWarnings("MissingPermission")
    private void removeGeofences() {
        if (!checkPermissions()) {
            showSnackbar(getString(R.string.insufficient_permissions));
            return;
        }

        mGeofencingClient.removeGeofences(getGeofencePendingIntent()).addOnCompleteListener(this);
    }

    private void upgradeWaypoint(Waypoint w) {
        long holz = Castle.getRes(1);
        long stein = Castle.getRes(2);
        int targetLevel = w.getUpgrades()+1;
        long kostenHolz = (long)Math.pow(5, targetLevel-1);
        long kostenStein = (long)Math.pow(5, targetLevel-1);
        if(kostenStein <=stein && kostenHolz <= holz){
            Castle.setRes(1, holz-kostenHolz);
            Castle.setRes(2, stein-kostenStein);
            w.setUpgrades(targetLevel);
            w.setStorageCap(w.getStorageCap()+1);
            w.setGrowDuration((long)(w.getGrowDuration()*7.0/8.0));
            Castle.saveWorld(this);
            updateUI();
        } else {
            Toast.makeText(this, kostenHolz+" Holz und "+kostenStein+" Stein benötigt.", Toast.LENGTH_LONG).show();
        }
    }


    private void handleWaypointButton(final int wpId, final String name) {
        if (Castle.getWaypoints().size()==wpId) {
            // fetch location and create new Waypoint...
            try {
                mFusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                // Got last known location. In some rare situations this can be null.
                                if (location != null) {
                                    // create waypoint, register geofences and refresh:
                                    if (location.getAccuracy() <= 20) { // TODO Konstante
                                        // TODO check distance to other waypoints
                                        Waypoint wp = new Waypoint(wpId, name, new LatLng(location.getLatitude(), location.getLongitude()));
                                        Castle.getWaypoints().put(wp.getNr(), wp);
                                        Castle.saveWorld(MainActivity.this);
                                        populateGeofenceList();
                                        performPendingGeofenceTask();
                                        updateUI();
                                    } else {
                                        Toast.makeText(MainActivity.this, "GPS aktuell zu ungenau", Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, "Failed to determine location!", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            } catch (SecurityException e){
                Toast.makeText(this, "Access to location required!", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            upgradeWaypoint(Castle.getWaypoints().get(wpId));
        }
    }

    public void castleButtonHandler(View view){
        handleWaypointButton(0, "Solace");
    }

    public void soldierButtonHandler(View view){
        Castle.setRes(0, Castle.getRes(0)-1);
        Castle.setRes(3, Castle.getRes(3)-1);
        Castle.setSoldiers(Castle.getSoldiers()+1);
        Castle.saveWorld(this);
        updateUI();
    }

    public void tower1ButtonHandler(View view){
        handleWaypointButton(1, "Düsterwald");
    }

    public void tower2ButtonHandler(View view){
        handleWaypointButton(2, "Hohe Klamm");
    }

    public void tower3ButtonHandler(View view){
        handleWaypointButton(3, "Eherne Minen");
    }

    /**
     * Runs when the result of calling {@link #addGeofences()} and/or {@link #removeGeofences()}
     * is available.
     * @param task the resulting Task, containing either a result or error.
     */
    @Override
    public void onComplete(@NonNull Task<Void> task) {
        mPendingGeofenceTask = PendingGeofenceTask.NONE;
        if (task.isSuccessful()) {
            updateGeofencesAdded(!isGeofencesAdded());
            setButtonsEnabledState();

            int messageId = isGeofencesAdded() ? R.string.geofences_added :
                    R.string.geofences_removed;
            Toast.makeText(this, getString(messageId), Toast.LENGTH_SHORT).show();
        } else {
            // Get the status code for the error and log it using a user-friendly message.
            String errorMessage = GeofenceErrorMessages.getErrorString(this, task.getException());
            Log.w(TAG, errorMessage);
        }
    }

    /**
     * Gets a PendingIntent to send with the request to add or remove Geofences. Location Services
     * issues the Intent inside this PendingIntent whenever a geofence transition occurs for the
     * current list of geofences.
     *
     * @return A PendingIntent for the IntentService that handles geofence transitions.
     */
    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * This sample hard codes geofence data. A real app might dynamically create geofences based on
     * the user's location.
     */
    private void populateGeofenceList() {
        mGeofenceList.clear();
        for (Map.Entry<Integer, Waypoint> entry : Castle.getWaypoints().entrySet()) {
            mGeofenceList.add(new Geofence.Builder()
                    // Set the request ID of the geofence. This is a string to identify this
                    // geofence.
                    .setRequestId(entry.getKey()+"")

                    // Set the circular region of this geofence.
                    .setCircularRegion(
                            entry.getValue().getKoords().latitude,
                            entry.getValue().getKoords().longitude,
                            Constants.GEOFENCE_RADIUS_IN_METERS
                    )

                    // Set the expiration duration of the geofence. This geofence gets automatically
                    // removed after this period of time.
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)

                    // Set the transition types of interest. Alerts are only generated for these
                    // transition. We track entry and exit transitions in this sample.
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER
                          //  | Geofence.GEOFENCE_TRANSITION_EXIT
                        )

                    // Create the geofence.
                    .build());
        }
    }

    /**
     * Ensures that only one button is enabled at any time. The Add Geofences button is enabled
     * if the user hasn't yet added geofences. The Remove Geofences button is enabled if the
     * user has added geofences.
     */
    private void setButtonsEnabledState() {

        // location tracking on/off buttons
        if(Castle.getWaypoints().isEmpty()){
            mAddGeofencesButton.setEnabled(false);
            mRemoveGeofencesButton.setEnabled(false);
        }else{
            if (isGeofencesAdded()) {
                mAddGeofencesButton.setEnabled(false);
                mRemoveGeofencesButton.setEnabled(true);
            } else {
                mAddGeofencesButton.setEnabled(true);
                mRemoveGeofencesButton.setEnabled(false);
            }
        }

        // castle Button:
        if(Castle.getWaypoints().isEmpty()){
            mCastleButton.setEnabled(true);
            mCastleButton.setText("Fahne setzen");
        } else {
            mCastleButton.setEnabled(true);
            mCastleButton.setText("befestigen");
        }

        // soldier button
        if(Castle.getRes(0)>0 && Castle.getRes(3)>0){
            mSoldierButton.setEnabled(true);
        }else {
            mSoldierButton.setEnabled(false);
        }

        // tower 1 button:
        if(Castle.getWaypoints().size()==1){
            mTower1Button.setEnabled(true);
            mTower1Button.setText("Fahne setzen");
        } else {
            if(Castle.getWaypoints().size()<1){
                mTower1Button.setEnabled(false);
                mTower1Button.setText("");
            } else {
                mTower1Button.setEnabled(true);
                mTower1Button.setText("befestigen");
            }
        }

        // tower 2 button:
        if(Castle.getWaypoints().size()==2){
            mTower2Button.setEnabled(true);
            mTower2Button.setText("Fahne setzen");
        } else {
            if(Castle.getWaypoints().size()<2){
                mTower2Button.setEnabled(false);
                mTower2Button.setText("");
            } else {
                mTower2Button.setEnabled(true);
                mTower2Button.setText("befestigen");
            }

        }

        // tower 3 button:
        if(Castle.getWaypoints().size()==3){
            mTower3Button.setEnabled(true);
            mTower3Button.setText("Fahne setzen");
        } else {
            if(Castle.getWaypoints().size()<3){
                mTower3Button.setEnabled(false);
                mTower3Button.setText("");
            } else {
                mTower3Button.setEnabled(true);
                mTower3Button.setText("befestigen");
            }
        }
        // TODO einblenden wieviel ein upgrade kostet und button ggfs ausgrauen
    // TODO absichern, damit niemand 8 oder öfter mal upgradet

    }

    /**
     * Shows a {@link Snackbar} using {@code text}.
     *
     * @param text The Snackbar text.
     */
    private void showSnackbar(final String text) {
        View container = findViewById(android.R.id.content);
        if (container != null) {
            Snackbar.make(container, text, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Shows a {@link Snackbar}.
     *
     * @param mainTextStringId The id for the string resource for the Snackbar text.
     * @param actionStringId   The text of the action item.
     * @param listener         The listener associated with the Snackbar action.
     */
    private void showSnackbar(final int mainTextStringId, final int actionStringId,
                              View.OnClickListener listener) {
        Snackbar.make(
                findViewById(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), listener).show();
    }

    /**
     * Returns true if geofences were added, otherwise false.
     */
    private boolean isGeofencesAdded() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                Constants.GEOFENCES_ADDED_KEY, false);
    }

    /**
     * Stores whether geofences were added ore removed in {@link SharedPreferences};
     *
     * @param added Whether geofences were added or removed.
     */
    private void updateGeofencesAdded(boolean added) {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(Constants.GEOFENCES_ADDED_KEY, added)
                .apply();
    }

    /**
     * Performs the geofencing task that was pending until location permission was granted.
     */
    private void performPendingGeofenceTask() {
        if (mPendingGeofenceTask == PendingGeofenceTask.ADD) {
            addGeofences();
        } else if (mPendingGeofenceTask == PendingGeofenceTask.REMOVE) {
            removeGeofences();
        }
    }

    /**
     * Return the current state of the permissions needed.
     */
    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            showSnackbar(R.string.permission_rationale, android.R.string.ok,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Request permission
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    });
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission granted.");
                performPendingGeofenceTask();
            } else {
                // Permission denied.

                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                showSnackbar(R.string.permission_denied_explanation, R.string.settings,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        });
                mPendingGeofenceTask = PendingGeofenceTask.NONE;
            }
        }
    }
}
