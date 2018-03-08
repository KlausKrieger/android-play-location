package com.google.android.gms.location.sample.geofencing;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;

// TODO s
// distanzcheck zu anderen wps einbauen beim bauen
// warumkommen zwei berechtigungsabfragen zu beginn?
// TODO s und warnings durchsehen

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

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private SettingsClient mSettingsClient;
    private LocationSettingsRequest mLocationSettingsRequest;
    private Location mCurrentLocation;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        // TODO und location updates ausschalten oder abschwächen, sobald alle gebaut sind

        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(4 * 1000); // in ms
        mLocationRequest.setFastestInterval(1 * 1000);
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

        // Empty list for storing geofences.
        mGeofenceList = new ArrayList<>();

        // Initially set the PendingIntent used in addGeofences() and removeGeofences() to null.
        mGeofencePendingIntent = null;

       mGeofencingClient = LocationServices.getGeofencingClient(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!checkPermissions()) {
            requestPermissions();
        } else {
            populateGeofenceList(); // TODO zu häufig? vorher entfernen oder nur in create?
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
                    public void onComplete(@NonNull Task<Void> task) {}
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
                    @SuppressLint("MissingPermission")
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

        // resources in home-storage
        String castleStatus = "";
        castleStatus += "Ressourcen:";
        for(int i=0; i<Waypoint.RES_NAMES.length; i++){
            castleStatus+="\t\t";
            castleStatus += Waypoint.RES_NAMES[i] + ": " + Castle.getRes(i);
        }
        //castleStatus += "\n";
        TextView castleTextView = (TextView) findViewById(R.id.castleTextView);
        castleTextView.setText(castleStatus);

        // soldiers
        TextView soldierTextView = (TextView) findViewById(R.id.soldierTextView);
        soldierTextView.setText("Soldaten: " + Castle.getSoldiers());

        // update ui of fragments
        for(Fragment f : getSupportFragmentManager().getFragments()){
            if (f instanceof PlaceholderFragment){ // TODO per tag oder id finden?
                ((PlaceholderFragment) f).updateUI();
            }
        }

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
     * Removes geofences. This method should be called after the user has granted the location
     * permission.
     */
    @SuppressWarnings("MissingPermission")
    private void removeGeofences() {
        if (!checkPermissions()) {
            showSnackbar(getString(R.string.insufficient_permissions));
            return;
        }
        // wird aktuell nie aufgerufen
        //mGeofencingClient.removeGeofences(getGeofencePendingIntent()).addOnCompleteListener(TODO oder this überarbeiten);
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


    private void handleWaypointButton(final int wpId) {
        if (Castle.getWaypoints().size()==wpId) {
            final String name = Waypoint.DEFAULT_NAMES[wpId];
            // fetch location and create new Waypoint...

                if (mCurrentLocation != null) {
                    // create waypoint, register geofences and refresh:
                    if (mCurrentLocation.getAccuracy() <= 20) { // TODO Konstante
                        // TODO check distance to other waypoints
                        Waypoint wp = new Waypoint(wpId, name, new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
                        Castle.getWaypoints().put(wp.getNr(), wp);
                        Castle.saveWorld(MainActivity.this);
                        populateGeofenceList();
                        updateUI();
                    } else {
                        Toast.makeText(MainActivity.this, "GPS aktuell zu ungenau", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Failed to determine location!", Toast.LENGTH_LONG).show();
                }

        } else {
            upgradeWaypoint(Castle.getWaypoints().get(wpId));
        }
    }

    public void soldierButtonHandler(View view){
        if (Castle.getRes(0) >=1 && Castle.getRes(3)>=0) {
            Castle.setRes(0, Castle.getRes(0) - 1);
            Castle.setRes(3, Castle.getRes(3) - 1);
            Castle.setSoldiers(Castle.getSoldiers() + 1);
            Castle.saveWorld(this);
            updateUI();
        } else {
            Toast.makeText(this, "Es werden 1 Gold und 1 Eisen benötigt, um einen Anwärter aufnehmen und ausstatten zu können.", Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Runs when the result of calling {@link #addGeofences()} and/or {@link #removeGeofences()}
     * is available.
     * @param task the resulting Task, containing either a result or error.
     */
    @Override
    public void onComplete(@NonNull Task<Void> task) {
        if (task.isSuccessful()) {
            Toast.makeText(this, getString(R.string.geofences_added), Toast.LENGTH_SHORT).show();
        } else {
            // Get the status code for the error and log it using a user-friendly message.
            String errorMessage = GeofenceErrorMessages.getErrorString(this, task.getException());
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
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
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        mGeofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }


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
        if(!mGeofenceList.isEmpty()){
            addGeofences();
        }

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
                populateGeofenceList();
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
            }
        }
    }

    public void buildButtonHandler(View view){
        handleWaypointButton(mViewPager.getCurrentItem());
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        private View rootView;

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given outpost.
         */
        public static PlaceholderFragment newInstance(int outpostNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, outpostNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.fragment_main, container, false);

            updateUI();

            return rootView;
        }

        public void updateUI(){
            int outpostNr = getArguments().getInt(ARG_SECTION_NUMBER);
            if (rootView != null) {
                Waypoint w = Castle.getWaypoints().get(outpostNr);

                Button buildButton = (Button) rootView.findViewById(R.id.buildButton);

                if (w == null) {
                    buildButton.setText("Land beanspruchen!");
                    TextView growDuration = (TextView) rootView.findViewById(R.id.growDuration);
                    growDuration.setText(Waypoint.RES_NAMES[outpostNr]);
                } else {
                    buildButton.setText("Befestigung verstärken");
                    TextView name = (TextView) rootView.findViewById(R.id.name);
                    name.setText(w.getName());
                    TextView upgradeName = (TextView) rootView.findViewById(R.id.upgradeName);
                    upgradeName.setText("Ausbaustufe: " + Waypoint.getUpgNames()[w.getUpgrades()]);
                    TextView growDuration = (TextView) rootView.findViewById(R.id.growDuration);
                    growDuration.setText("1 " + Waypoint.RES_NAMES[outpostNr] + " alle " + w.getGrowDurationAsString());
                    TextView lager = (TextView) rootView.findViewById(R.id.lager);
                    lager.setText(w.calcStorage() + "/" + w.getStorageCap());
                    TextView distance = (TextView) rootView.findViewById(R.id.distance);
                    DecimalFormat df = new DecimalFormat("#,###m");
                    float distanceInMeters = w.distanceInMeters(((MainActivity)getActivity()).mCurrentLocation);
                    if(distanceInMeters == Float.MAX_VALUE){
                        distance.setText("distance unknown");
                    } else {
                        distance.setText("Distanz: " + df.format(distanceInMeters));
                    }

                }

                if (Castle.getWaypoints().size() < outpostNr){
                    buildButton.setEnabled(false);
                } else {
                    buildButton.setEnabled(true);
                }
            }
        }

    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position);
        }

        @Override
        public int getCount() {
            // Show 4 total pages.
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Gold";
                case 1:
                    return "Holz";
                case 2:
                    return "Steine";
                case 3:
                    return "Eisen";
            }
            return null;
        }
    }


}
