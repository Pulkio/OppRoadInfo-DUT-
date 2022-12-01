package fr.ubs.opproadinfo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import fr.ubs.opproadinfo.model.Event;
import fr.ubs.opproadinfo.model.InternalNotif;
import fr.ubs.opproadinfo.utils.Network;

/**
 * Main activity of the app
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener, LocationListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private String mapStyle;
    private Style mMapStyle;

    private PermissionsManager permissionsManager;

    private SwitchCompat accidentSwitch;
    private SwitchCompat trafficSwitch;
    private SwitchCompat workSwitch;
    private SwitchCompat checkZoneSwitch;
    private SwitchCompat voiceSwitch;
    private SwitchCompat darkModeButton;
    private SeekBar alertRadius;

    private float kmRadius;
    private TextView kmTextView;

    private boolean isVoice;
    private boolean isAccident;
    private boolean isTraffic;
    private boolean isWork;
    private boolean isCheckZone;
    private boolean isDark;
    private boolean isForeground = true;

    private AlertDialog eventAlertDialog;
    private FloatingActionButton floatingEventButton;
    private FloatingActionButton floatingSettingsButton;
    private FloatingActionButton floatingRefocusButton;

    private SharedPreferences preferences;
    private Network network;

    private int cmpNotifExterne = 0;
    private final ArrayList<Event> waitingConfirmation = new ArrayList<>();
    private final ArrayList<InternalNotif> notifDisplayQueue = new ArrayList<>();
    private final ArrayList<Event> waitingNotifications = new ArrayList<>();
    private boolean animationEnCours = false;
    private MediaPlayer mediaPlayer;

    private static final String ACCIDENT_TYPE = "accident";
    private static final String WORK_TYPE = "work";
    private static final String TRAFFIC_TYPE = "traffic";
    private static final String CHECK_TYPE = "check";

    private static final int THUMB_UP = 1;
    private static final int THUMB_DOWN = 2;
    private static final int THUMB_QUESTION = 3;

    private float speed = -1;
    private final Handler handler = new Handler();

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Load the map with the token
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set up the map
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        this.floatingRefocusButton = findViewById(R.id.camera_focus);
        floatingRefocusButton.setVisibility(View.INVISIBLE);

        this.floatingEventButton = findViewById(R.id.report);
        this.floatingSettingsButton = findViewById(R.id.parameters);
        setUpFloatingButtons();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.kmRadius = preferences.getFloat(getString(R.string.save_radius), -1);
        if (this.kmRadius == -1) this.kmRadius = 5;

        isDark = preferences.getBoolean(getString(R.string.save_dark), false);
        isAccident = preferences.getBoolean(getString(R.string.save_accident), true);
        isTraffic = preferences.getBoolean(getString(R.string.save_embouteillage), true);
        isWork = preferences.getBoolean(getString(R.string.save_travaux), true);
        isCheckZone = preferences.getBoolean(getString(R.string.save_zoneDeControle), true);
        isVoice = preferences.getBoolean(getString(R.string.save_voix), false);

        setGpsOffMessage();
        setUpNetwork();

        confirmationThread.start();
    }

    /**
     * Ran when the map is ready
     *
     * @param mapboxMap map object
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }

        if (!isDark) mapStyle = Style.MAPBOX_STREETS;
        else mapStyle = Style.DARK;

        //Set position
        CameraPosition startCamera = new CameraPosition.Builder()
                .target(new LatLng(47.646290, -2.774186))
                .zoom(17)
                .build();
        mapboxMap.setCameraPosition(startCamera);

        //Set style
        mapboxMap.setStyle(mapStyle, style -> {
            style.addImage(TRAFFIC_TYPE, getDrawable(R.drawable.traffic));
            style.addImage(WORK_TYPE, getDrawable(R.drawable.work));
            style.addImage(ACCIDENT_TYPE, getDrawable(R.drawable.accident));
            style.addImage(CHECK_TYPE, getDrawable(R.drawable.check_zone));
            mMapStyle = style;
            enableLocationComponent(style);
        });
    }

    /**
     * Enable the location of the device
     *
     * @param loadedMapStyle map's style
     */
    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {

        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            LocationComponent locationComponent = mapboxMap.getLocationComponent();

            locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(this, loadedMapStyle).build());
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING_GPS);
            locationComponent.setRenderMode(RenderMode.GPS);
            locationComponent.addOnCameraTrackingChangedListener(trackingModeListener);

            mapboxMap.getUiSettings().setCompassEnabled(false);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    /**
     * Set up the message asking to enable GPS
     */
    private void setGpsOffMessage() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this, R.style.Theme_AppCompat_Dialog_Alert);
            dialog.setTitle(R.string.noGps);
            String text = getResources().getString(R.string.plsStartGps);
            dialog.setMessage(text);
            dialog.setPositiveButton(R.string.ok, (dialog1, which) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            dialog.show();
        }
    }

    private void addMark(String type, double latitude, double longitude) {
        MainActivity.this.mapView.getMapAsync(mapboxMap -> {
            SymbolManager sm = new SymbolManager(MainActivity.this.mapView, MainActivity.this.mapboxMap, MainActivity.this.mMapStyle);
            try{
                sm.setIconAllowOverlap(true);
            }catch (com.mapbox.mapboxsdk.exceptions.CalledFromWorkerThreadException e){
                e.printStackTrace();
            }
            sm.create(new SymbolOptions()
                    .withLatLng(new LatLng(latitude, longitude))
                    .withIconImage(type)
            );
        });
    }

    /**
     * Set up the permission for location
     *
     * @param requestCode  request code of the query
     * @param permissions  permissions concerned
     * @param grantResults result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Explanation of demand to access to location
     */
    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    /**
     * Event of the location permission's result
     *
     * @param granted true if the permission has been granted
     */
    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(this::enableLocationComponent);
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Set the floatings buttons
     */
    private void setUpFloatingButtons() {

        /*
        Event button
         */
        this.floatingEventButton.setOnClickListener(new View.OnClickListener() {
            /**
             * This method detects a click on the event icon
             */
            @Override
            public void onClick(View view) {

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog);

                View dialogContentView = getLayoutInflater().inflate(R.layout.event_screen, null);
                builder.setView(dialogContentView);

                dialogContentView.findViewById(R.id.accident_button).setOnClickListener(eventListener);
                dialogContentView.findViewById(R.id.traffic_button).setOnClickListener(eventListener);
                dialogContentView.findViewById(R.id.work_button).setOnClickListener(eventListener);
                dialogContentView.findViewById(R.id.check_button).setOnClickListener(eventListener);

                eventAlertDialog = builder.show();
            }
        });

        /*
        Settings button
         */
        this.floatingSettingsButton.setOnClickListener(new View.OnClickListener() {
            /**
             * This method detects a click on the settings icon
             */
            @Override
            public void onClick(View view) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialog);

                View dialogContentView = getLayoutInflater().inflate(R.layout.settings_screen, null);

                alertRadius = dialogContentView.findViewById(R.id.alertRadius);
                alertRadius.setProgress((int) (MainActivity.this.kmRadius * 10));
                kmTextView = dialogContentView.findViewById(R.id.kmTextView);
                String text = MainActivity.this.kmRadius + getResources().getString(R.string.km);
                MainActivity.this.kmTextView.setText(text);

                setRadiusListener();

                darkModeButton = dialogContentView.findViewById(R.id.darkMode);
                darkModeButton.setChecked(isDark);

                accidentSwitch = dialogContentView.findViewById(R.id.accident_switch);
                accidentSwitch.setChecked(isAccident);

                trafficSwitch = dialogContentView.findViewById(R.id.embouteillages_switch);
                trafficSwitch.setChecked(isTraffic);

                workSwitch = dialogContentView.findViewById(R.id.travaux_switch);
                workSwitch.setChecked(isWork);

                checkZoneSwitch = dialogContentView.findViewById(R.id.zone_de_controle_switch);
                checkZoneSwitch.setChecked(isCheckZone);

                voiceSwitch = dialogContentView.findViewById(R.id.voix_switch);
                voiceSwitch.setChecked(isVoice);

                dialogContentView.findViewById(R.id.offLineButtonParameter).setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, OffLineMapMenu.class);
                    intent.putExtra("isDark", isDark);
                    startActivity(intent);
                    onPause();
                });

                SwitchCompat[] switchTab = {MainActivity.this.darkModeButton, MainActivity.this.accidentSwitch,
                        MainActivity.this.trafficSwitch, MainActivity.this.workSwitch,
                        MainActivity.this.checkZoneSwitch, MainActivity.this.voiceSwitch};

                for (SwitchCompat button : switchTab) {
                    button.setOnCheckedChangeListener(switchParameterListener);
                }

                dialog.setView(dialogContentView).create();
                dialog.show();
            }
        });

        /*
        Refocus button
         */
        this.floatingRefocusButton.setOnClickListener(new View.OnClickListener() {
            /**
             * this method detects a click on the refocus button
             */
            @Override
            public void onClick(View view) {
                mapboxMap.getLocationComponent().setCameraMode(CameraMode.TRACKING_GPS);
            }
        });
    }

    /**
     * The Event listener.
     */
    View.OnClickListener eventListener = new View.OnClickListener() {
        @SuppressLint("NonConstantResourceId")
        @Override
        public void onClick(View v) {
            eventAlertDialog.dismiss();

            String type;

            switch (v.getId()) {

                case R.id.accident_button:
                    type = ACCIDENT_TYPE;
                    break;
                case R.id.traffic_button:
                    type = TRAFFIC_TYPE;
                    break;
                case R.id.work_button:
                    type = WORK_TYPE;
                    break;
                case R.id.check_button:
                    type = CHECK_TYPE;
                    break;
                default:
                    type = "";
            }

            //Get the position from GPS if it's possible
            if (!(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(MainActivity.this, location -> {
                            if (location != null) {
                                double latitude = location.getLatitude();
                                double longitude = location.getLongitude();

                                addEvent(type, latitude, longitude);
                            }
                        });
            }
            //Or get it from camera location if not
            else {
                LatLng position = MainActivity.this.mapboxMap.getCameraPosition().target;
                double latitude = position.getLatitude();
                double longitude = position.getLongitude();

                addEvent(type, latitude, longitude);
            }
        }
    };

    /**
     * Add an new event on the map end send it to the Raspberry PI
     *
     * @param type      Event type
     * @param latitude  coordinate
     * @param longitude coordinate
     */
    private void addEvent(String type, double latitude, double longitude) {
        addMark(type, latitude, longitude);
        JSONObject message = new JSONObject();
        double end;
        Date d = new Date();

        switch (type) {
            case WORK_TYPE:
                end = (d.getTime() / 1000.0) + 10800;
                break;
            case CHECK_TYPE:
                end = (d.getTime() / 1000.0) + 2700;
                break;
            default:
                end = (d.getTime() / 1000.0) + 3600; //Same value for TRAFFIC_TYPE
        }
        try {
            message.put("latitude", latitude);
            message.put("longitude", longitude);
            message.put("type", type);
            message.put("end", end);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        network.sendMessage(message.toString());
    }

    /**
     * Send the event confirmation to the raspberry Pi
     *
     * @param eventId     of the event to confirm
     * @param reliability Must be 1, -1 or 0
     */
    private void sendEventConfirmation(int eventId, int reliability) {
        try {
            if (reliability == 1 || reliability == -1 || reliability == 0) {

                JSONObject response = new JSONObject();
                response.put("id", eventId);
                response.put("reliability", reliability);
                network.sendMessage(response.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * This thread checks every 5s that the user is 50 meters away from an event, and if so it call the notification methods
     */
    private final Thread confirmationThread = new Thread(() -> {
        while (true) {
            try {
                Thread.sleep(5000);
                ArrayList<Integer> eventsToRemove = new ArrayList<>();

                LatLng position = MainActivity.this.mapboxMap.getCameraPosition().target;
                double cLat = position.getLatitude();
                double cLong = position.getLongitude();

                for (Event event : MainActivity.this.waitingConfirmation) {

                    double eLat = event.getLatitude();
                    double eLong = event.getLongitude();

                    if (MainActivity.this.computeDistance(cLat, cLong, eLat, eLong) < 0.05) {

                        int index = -1;
                        int i = 0;
                        while (i < MainActivity.this.waitingConfirmation.size()) {
                            int id = MainActivity.this.waitingConfirmation.get(i).getId();

                            if (id == event.getId()) {
                                index = i;
                                break;
                            }
                        }
                        if (index != -1) {
                            eventsToRemove.add(index);
                        }
                        handler.post(() -> {
                            InternalNotif internalNotif = new InternalNotif(event, true);

                            if (!MainActivity.this.animationEnCours) {
                                MainActivity.this.confirmPopUp(event.getType(), event.getId());
                            } else {
                                MainActivity.this.notifDisplayQueue.add(internalNotif);
                            }
                        });
                    }
                }

                for (int index : eventsToRemove) {
                    MainActivity.this.waitingConfirmation.remove(index);
                }
                eventsToRemove = new ArrayList<>();

                for (Event event : waitingNotifications) {
                    if (eventTriggered(event)) {
                        //To remove the concurrent  exception
                        eventsToRemove.add(waitingNotifications.indexOf(event));
                    }
                }
                for (int index : eventsToRemove) {
                    MainActivity.this.waitingConfirmation.remove(index);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    });

    /**
     * this method set the radiusListener
     */
    private void setRadiusListener() {
        this.alertRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            /**
             * This method displays the number of kilometers above the seekbar, depending on its state
             */
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float km = (float) Math.round((float) progress) / 10;
                MainActivity.this.kmRadius = km;
                String text = km + getResources().getString(R.string.km);
                MainActivity.this.kmTextView.setText(text);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            /**
             * this method calculate the number of kilometers corresponding to the progress of the seekbar
             * @param seekBar the seekbar
             */
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float km = (float) Math.round((float) seekBar.getProgress()) / 10;
                MainActivity.this.saveSetting(km, getString(R.string.save_radius));
            }
        });
    }

    /**
     * Change the style of the map. Might be light or dark
     *
     * @param isChecked true if dark mode is enabled
     */
    private void changeStyle(boolean isChecked) {
        if (isChecked) {
            this.mapStyle = Style.DARK;
            this.isDark = true;
        } else {
            this.mapStyle = Style.MAPBOX_STREETS;
            this.isDark = false;
        }
        mapboxMap.setStyle(mapStyle);
    }

    /**
     * Listener of the parameters's switch
     */
    private final CompoundButton.OnCheckedChangeListener switchParameterListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            int buttonID = buttonView.getId();
            String saveName = null;
            if (accidentSwitch.getId() == buttonID) {
                saveName = getString(R.string.save_accident);
                isAccident = isChecked;
            } else if (trafficSwitch.getId() == buttonID) {
                saveName = getString(R.string.save_embouteillage);
                isTraffic = isChecked;
            } else if (workSwitch.getId() == buttonID) {
                saveName = getString(R.string.save_travaux);
                isWork = isChecked;
            } else if (checkZoneSwitch.getId() == buttonID) {
                saveName = getString(R.string.save_zoneDeControle);
                isCheckZone = isChecked;
            } else if (voiceSwitch.getId() == buttonID) {
                saveName = getString(R.string.save_voix);
                isVoice = isChecked;
            } else if (darkModeButton.getId() == buttonID) {
                saveName = getString(R.string.save_dark);
                changeStyle(isChecked);
            }
            saveSetting(isChecked, saveName);
        }
    };

    /**
     * Tracking mode's listener
     */
    private final OnCameraTrackingChangedListener trackingModeListener = new OnCameraTrackingChangedListener() {
        @Override
        public void onCameraTrackingDismissed() {
        }

        @Override
        public void onCameraTrackingChanged(int currentMode) {
            //If not tracking mode, then display refocus button
            if (CameraMode.TRACKING_GPS != currentMode)
                MainActivity.this.floatingRefocusButton.setVisibility(View.VISIBLE);
            else MainActivity.this.floatingRefocusButton.setVisibility(View.INVISIBLE);
        }
    };

    /**
     * Set up the bluetooth connection between the Raspberry Pi and the phone
     */
    private void setUpNetwork() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        network = new Network(MainActivity.this);
        network.start();
    }

    /**
     * Computes the e data sent from the Raspberry
     *
     * @param e - the data to compute
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void processEvent(JSONObject e) {
        try {
            Event event = new Event(e.getDouble("latitude"), e.getDouble("longitude"), e.getString("type"), e.getDouble("end"), e.getInt("id"));
            waitingConfirmation.add(event);
            waitingNotifications.add(event);

            if (eventTriggered(event)) {
                waitingNotifications.remove(event);
            }
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Calls a notification if necessary (checks distance between user and events)
     * @param event an event
     * @return true if an event needs to be show to the user
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean eventTriggered(Event event) {
        if (mapboxMap != null) {
            boolean display = false;

            switch (event.getType()) {
                case TRAFFIC_TYPE:
                    if (isTraffic) display = true;
                    break;
                case WORK_TYPE:
                    if (isWork) display = true;
                    break;
                case CHECK_TYPE:
                    if (isCheckZone) display = true;
                    break;
                default:
                    if (isAccident) display = true;
            }

            LatLng position = mapboxMap.getCameraPosition().target;
            double latitudePos = position.getLatitude();
            double longitudePos = position.getLongitude();

            if (isDistanceOk(event.getLatitude(), event.getLongitude(), latitudePos, longitudePos, kmRadius) && display) {
                addMark(event.getType(), event.getLatitude(), event.getLongitude());

                int thumb = computeThumb(event);
                InternalNotif internalNotif = new InternalNotif(event, false);

                if (!animationEnCours) {
                    notif(event.getType(), thumb);
                } else {
                    if (notifDisplayQueue.size() < 8) {
                        notifDisplayQueue.add(internalNotif);
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Computes the value of the thumb (green, red of question mark) depending on the quality of the event
     * @param event an event
     * @return 1,2 or 3
     */
    private int computeThumb(Event event) {
        double end = event.getEndTime();
        Date d = new Date();
        double cTime = (double) (d.getTime() / 1000);
        int thumb = THUMB_UP;
        int delay;
        switch (event.getType()) {
            case CHECK_TYPE:
                delay = 900;
                break;
            case WORK_TYPE:
                delay = 3600;
                break;
            default:
                delay = 1200;   //Same delay for ACCIDENT_TYPE and TRAFFIC_TYPE
        }

        if ((end - cTime) < delay) thumb = THUMB_DOWN;
        else if ((end - cTime) >= delay && (end - cTime) <= (delay * 2)) thumb = THUMB_QUESTION;
        return thumb;
    }

    /**
     * This method computes the distance between the coordinates
     * @param lat1 latitude of the coordinate 1
     * @param long1 longitude of the coordinate 1
     * @param lat2 latitude of the coordinate 2
     * @param long2 longitude of the coordinate 2
     * @return the distance in meters
     */
    private double computeDistance(double lat1, double long1, double lat2, double long2) {
        double r = 6378.137;

        lat1 = lat1 * Math.PI / 180;
        lat2 = lat2 * Math.PI / 180;
        long1 = long1 * Math.PI / 180;
        long2 = long2 * Math.PI / 180;
        return r * Math.acos(Math.sin(lat2) * Math.sin(lat1) + Math.cos(lat2) * Math.cos(lat1) * Math.cos(long2 - long1));
    }

    /**
     * This method checks if the distance between the coordinates are lower than the maximum distance
     *
     * @param lat1      latitude of the coordinate 1
     * @param long1     longitude of the coordinate 1
     * @param lat2      latitude of the coordinate 2
     * @param long2     longitude of the coordinate 2
     * @param maxDistance the max distance
     * @return true if the distance between coordinates is lower than the maximum distance
     */
    public boolean isDistanceOk(double lat1, double long1, double lat2, double long2, double maxDistance) {
        return computeDistance(lat1, long1, lat2, long2) < maxDistance;
    }

    /**
     * Save a settings
     *
     * @param value   value of the setting
     * @param setting setting key
     */
    private void saveSetting(float value, String setting) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat(setting, value);
        editor.apply();
    }

    /**
     * Save a setting
     *
     * @param bool    value of the setting
     * @param setting setting key
     */
    private void saveSetting(boolean bool, String setting) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(setting, bool);
        editor.apply();
    }

    /**
     * Creates internal notifications that show events to the user (look a little bit like pop ups)
     * @param eventType event type
     * @param thumb     the value of the thumb, which represents the quality of the information
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressLint({"UseCompatLoadingForDrawables", "ResourceType"})
    private void notif(String eventType, int thumb) {
        if (isVoice) {
            if (isForeground) notifSonore(eventType);
            else notifExterne(eventType);
        } else if (!isForeground) notifExterne(eventType);

        animationEnCours = true;

        Animation startAnimation = AnimationUtils.loadAnimation(this, R.anim.fadein);
        Animation endAnimation = AnimationUtils.loadAnimation(this, R.anim.fadeout);

        ConstraintLayout notif = findViewById(R.id.notif_layout);
        ImageView imgNotif = findViewById(R.id.notifImage);
        TextView textView = findViewById(R.id.notifText);

        ImageView pouce = findViewById(R.id.notifPouce);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setProgress(0, true);
        progressBar.setMax(100);
        progressBar.setIndeterminate(false);

        Drawable progressDrawable = progressBar.getProgressDrawable().mutate();
        progressDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        progressBar.setProgressDrawable(progressDrawable);


        notif.startAnimation(startAnimation);

        class MyCountDownTimer extends CountDownTimer {

            public MyCountDownTimer(long millisInFuture, long countDownInterval) {
                super(millisInFuture, countDownInterval);
            }

            @Override
            public void onTick(long millisUntilFinished) {
                int progress = (int) (millisUntilFinished / 100);
                progressBar.setProgress(progressBar.getMax() - progress, true);
                progressBar.setIndeterminate(false);
            }

            @Override
            public void onFinish() {
                notif.startAnimation(endAnimation);
                notif.setVisibility(View.GONE);
                animationEnCours = false;

                if (!notifDisplayQueue.isEmpty()) {
                    Event event = notifDisplayQueue.get(0).getEvent();

                    String type = event.getType();

                    if (notifDisplayQueue.get(0).isConfirmation()) {
                        notifDisplayQueue.remove(0);
                        confirmPopUp(type, event.getId());
                    } else {
                        notifDisplayQueue.remove(0);
                        notif(type, computeThumb(event));
                    }
                }
            }
        }

        MyCountDownTimer myCountDownTimer = new MyCountDownTimer(10000, 10);
        myCountDownTimer.start();

        switch (thumb) {
            case THUMB_UP:
                pouce.setImageResource(R.drawable.green_thumb_big);
                break;
            case THUMB_DOWN:
                pouce.setImageResource(R.drawable.red_thumb_big);
                break;
            default:
                pouce.setImageResource(R.drawable.question_thumb_big);
        }

        switch (eventType) {
            case TRAFFIC_TYPE:
                imgNotif.setImageResource(R.drawable.traffic_notif);
                textView.setText(R.string.traffic_notif);
                textView.setTextSize(17);
                notif.setBackgroundResource(R.drawable.notifs_traffic);
                break;
            case CHECK_TYPE:
                imgNotif.setImageResource(R.drawable.check_notif);
                textView.setText(R.string.check_notif);
                textView.setTextSize(15);
                notif.setBackgroundResource(R.drawable.notifs_check);
                break;
            case WORK_TYPE:
                imgNotif.setImageResource(R.drawable.work_notif);
                textView.setText(R.string.work_notif);
                textView.setTextColor(getResources().getColor(R.color.grey));
                progressBar.getProgressDrawable().setColorFilter(Color.parseColor(getResources().getString(R.color.grey)), android.graphics.PorterDuff.Mode.SRC_IN);
                notif.setBackgroundResource(R.drawable.notifs_work);
                break;
            default:
                imgNotif.setImageResource(R.drawable.accident_notif);
                textView.setText(R.string.accident_notif);
                notif.setBackgroundResource(R.drawable.notifs_accident);
        }
        notif.setVisibility(View.VISIBLE);
    }

    /**
     * Creates sounds for internal notifications
     * @param eventType event type
     */
    private void notifSonore(String eventType) {
        switch (eventType) {
            case TRAFFIC_TYPE:
                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.emb);
                break;
            case CHECK_TYPE:
                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.zdc);
                break;
            case WORK_TYPE:
                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.trav);
                break;
            default:
                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.acc);
        }
        mediaPlayer.start();
    }

    /**
     * This method creates external notifications( push notifications )
     * @param eventType event id
     */
    private void notifExterne(String eventType) {
        String titre;
        String description;
        switch (eventType) {
            case TRAFFIC_TYPE:
                titre = getString(R.string.external_traffic_title);
                description = getString(R.string.external_traffic);
                break;
            case CHECK_TYPE:
                titre = getString(R.string.external_check_title);
                description = getString(R.string.external_check);
                break;
            case WORK_TYPE:
                titre = getString(R.string.external_work_title);
                description = getString(R.string.external_work);
                break;
            default:
                titre = getString(R.string.external_accident_title);
                description = getString(R.string.external_accident);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.app_name))
                .setSmallIcon(R.drawable.car_icon)
                .setContentTitle(titre)
                .setAutoCancel(true)
                .setContentText(description)
                .setContentIntent(contentIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(getResources().getString(R.string.package_name));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    getResources().getString(R.string.package_name),
                    getResources().getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        assert manager != null;
        manager.notify(cmpNotifExterne++, builder.build());
    }

    /**
     * This method displays confirmation pop-ups that can be use by the user ton confirm an event or not
     * @param eventType the type of event
     * @param eventId   event id
     */
    @SuppressLint("ResourceType")
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void confirmPopUp(String eventType, int eventId) {
        animationEnCours = true;

        Animation startAnimation = AnimationUtils.loadAnimation(this, R.anim.fadein);
        Animation endAnimation = AnimationUtils.loadAnimation(this, R.anim.fadeout);

        ConstraintLayout notif = findViewById(R.id.confirmation_layout);
        ImageView imgNotif = findViewById(R.id.notifImageConfirm);
        TextView textView = findViewById(R.id.notifTextConfirm1);
        TextView textView2 = findViewById(R.id.notifTextConfirm2);
        textView2.setText(R.string.confirm_question);

        ImageView thumb_up = findViewById(R.id.notifThumbUp);
        ImageView thumb_down = findViewById(R.id.notifThumbDown);
        ImageView close = findViewById(R.id.close);

        thumb_up.setImageResource(R.drawable.green_thumb_large);
        thumb_down.setImageResource(R.drawable.red_thumb_large);

        ProgressBar progressBar = findViewById(R.id.progressBarConfirm);
        progressBar.setProgress(100, true);
        progressBar.setMax(200);
        progressBar.setIndeterminate(false);

        Drawable progressDrawable = progressBar.getProgressDrawable().mutate();
        progressDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        progressBar.setProgressDrawable(progressDrawable);

        notif.startAnimation(startAnimation);

        class MyCountDownTimer extends CountDownTimer {

            public MyCountDownTimer(long millisInFuture, long countDownInterval) {
                super(millisInFuture, countDownInterval);
            }

            @Override
            public void onTick(long millisUntilFinished) {
                int progress = (int) (millisUntilFinished / 100);
                progressBar.setProgress(progressBar.getMax() - progress, true);
                progressBar.setIndeterminate(false);
            }

            @Override
            public void onFinish() {
                if (notif.getVisibility() != View.GONE) {
                    notif.startAnimation(endAnimation);
                }
                notif.setVisibility(View.GONE);
                animationEnCours = false;

                if (!notifDisplayQueue.isEmpty()) {
                    Event event = notifDisplayQueue.get(0).getEvent();

                    String type = event.getType();

                    if (notifDisplayQueue.get(0).isConfirmation()) {
                        notifDisplayQueue.remove(0);
                        confirmPopUp(type, event.getId());
                    } else {
                        notifDisplayQueue.remove(0);
                        notif(type, computeThumb(event));
                    }
                }
            }
        }

        MyCountDownTimer myCountDownTimer = new MyCountDownTimer(20000, 10);
        myCountDownTimer.start();

        switch (eventType) {
            case TRAFFIC_TYPE:
                imgNotif.setImageResource(R.drawable.traffic_notif);
                textView.setText(R.string.traffic_confirm);
                notif.setBackgroundResource(R.drawable.notifs_traffic);
                break;
            case CHECK_TYPE:
                imgNotif.setImageResource(R.drawable.check_notif);
                textView.setText(R.string.check_confirm);
                notif.setBackgroundResource(R.drawable.notifs_check);
                break;
            case WORK_TYPE:
                imgNotif.setImageResource(R.drawable.work_notif);
                textView.setText(R.string.worlk_confirm);

                textView.setTextColor(getResources().getColor(R.color.grey));
                textView2.setTextColor(getResources().getColor(R.color.grey));
                progressBar.getProgressDrawable().setColorFilter(Color.parseColor(getResources().getString(R.color.grey)), android.graphics.PorterDuff.Mode.SRC_IN);

                notif.setBackgroundResource(R.drawable.notifs_work);
                break;
            default:
                imgNotif.setImageResource(R.drawable.accident_notif);
                textView.setText(R.string.accident_confirm);
                notif.setBackgroundResource(R.drawable.notifs_accident);
        }
        notif.setVisibility(View.VISIBLE);

        close.setOnClickListener(v -> {
            notif.startAnimation(endAnimation);
            notif.setVisibility(View.GONE);
        });

        thumb_up.setOnClickListener(v -> {
            notif.startAnimation(endAnimation);
            notif.setVisibility(View.GONE);
            sendEventConfirmation(eventId, 1);
        });

        thumb_down.setOnClickListener(v -> {
            notif.startAnimation(endAnimation);
            notif.setVisibility(View.GONE);
            sendEventConfirmation(eventId, -1);
        });
    }

    /**
     * This method changes the zoom of the map depending of the speed of the user
     * @param location user location
     */
    public void onLocationChanged(Location location) {
        float cSpeed = (float) (location.getSpeed() * 3.6);

        if (speed == -1) speed = cSpeed;

        CameraPosition position = null;

        if (cSpeed < 40 && speed >= 40) {
            speed = cSpeed;
            position = new CameraPosition.Builder().zoom(17).build();

        } else if (cSpeed >= 40 && cSpeed < 60 && (speed < 40 || speed >= 60)) {
            speed = cSpeed;
            position = new CameraPosition.Builder().zoom(16).build();

        } else if (cSpeed >= 60 && cSpeed < 100 && (speed < 60 || speed >= 100)) {
            speed = cSpeed;
            position = new CameraPosition.Builder().zoom(15).build();

        } else if (cSpeed > 100 && speed <= 100) {
            speed = cSpeed;
            position = new CameraPosition.Builder().zoom(14).build();
        }

        if (position != null) {
            mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000, new MapboxMap.CancelableCallback() {
                @Override
                public void onCancel() {
                }

                @Override
                public void onFinish() {
                    mapboxMap.getLocationComponent().setCameraMode(CameraMode.TRACKING_GPS);
                }
            });
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
}