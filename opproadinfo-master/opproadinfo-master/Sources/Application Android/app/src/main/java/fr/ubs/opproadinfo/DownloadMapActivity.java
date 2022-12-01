package fr.ubs.opproadinfo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.geocoding.v5.GeocodingCriteria;
import com.mapbox.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.core.exceptions.ServicesException;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.json.JSONObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

/**
 * Drop a marker at a specific location and then perform
 * reverse geocoding to retrieve and display the location's address
 */
public class DownloadMapActivity extends AppCompatActivity implements PermissionsListener, OnMapReadyCallback, View.OnClickListener, SeekBar.OnSeekBarChangeListener, OfflineRegion.OfflineRegionDeleteCallback {

    private static final String DROPPED_MARKER_LAYER_ID = "DROPPED_MARKER_LAYER_ID";
    /**
     * The constant JSON_FIELD_REGION_NAME.
     */
    public static final String JSON_FIELD_REGION_NAME = "FIELD_REGION_NAME";
    /**
     * The constant JSON_CHARSET.
     */
    public static final String JSON_CHARSET = "UTF-8";

    private MapView mapView;
    private MapboxMap mapboxMap;

    private PermissionsManager permissionsManager;

    private OfflineRegion offlineRegion;

    private ImageView hoveringMarker;
    private Button validateLocationButton;
    private ProgressBar progressBar;
    private SeekBar radiusSeekBar;
    private TextView radiusText;

    private boolean isDownloadFinish;

    private String regionName;
    private String mapStyle;
    private Style style;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_location_picker);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        validateLocationButton = findViewById(R.id.select_location_button);
        radiusSeekBar = findViewById(R.id.offlineMapRadius);
        radiusText = findViewById(R.id.offlineMapRadiusText);
        progressBar = findViewById(R.id.progress_bar);

        radiusSeekBar.setOnSeekBarChangeListener(this);

        isDownloadFinish = false;
    }

    /**
     * This method initializes the map and the picker to select an area
     *
     * @param mapboxMap the mapbox map
     */
    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        Bundle extras = getIntent().getExtras();
        this.regionName = extras.getString("regionName");
        this.mapStyle = Style.MAPBOX_STREETS;

        mapboxMap.setStyle(this.mapStyle, style -> {
            DownloadMapActivity.this.style = style;
            enableLocationPlugin(style);

            //Ajout du pointer de selection
            hoveringMarker = new ImageView(DownloadMapActivity.this);
            hoveringMarker.setImageResource(R.drawable.red_marker);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            hoveringMarker.setLayoutParams(params);
            mapView.addView(hoveringMarker);
            initDroppedMarker(style);

            validateLocationButton.setOnClickListener(DownloadMapActivity.this);
        });
    }

    /**
     * Called when the validate location button is pressed
     *
     * @param v validate location button
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onClick(View v) {
        Layer droppedMarkerLayer;

        //Validation
        if (hoveringMarker.getVisibility() == View.VISIBLE) {
            final LatLng mapTargetLatLng = mapboxMap.getCameraPosition().target;

            double latitude = mapTargetLatLng.getLatitude();
            double longitude = mapTargetLatLng.getLongitude();
            double radius = radiusSeekBar.getProgress();

            hoveringMarker.setVisibility(View.INVISIBLE);

            validateLocationButton.setBackgroundColor(ContextCompat.getColor(DownloadMapActivity.this, R.color.colorAccent));
            validateLocationButton.setText(getString(R.string.cancel));

            //Display the dropped button
            if (style.getLayer(DROPPED_MARKER_LAYER_ID) != null) {
                GeoJsonSource source = style.getSourceAs("dropped-marker-source-id");

                if (source != null) {
                    source.setGeoJson(Point.fromLngLat(longitude, latitude));
                }
                droppedMarkerLayer = style.getLayer(DROPPED_MARKER_LAYER_ID);
                if (droppedMarkerLayer != null) {
                    droppedMarkerLayer.setProperties(visibility(VISIBLE));
                }
            }
            reverseGeocode(Point.fromLngLat(mapTargetLatLng.getLongitude(), mapTargetLatLng.getLatitude()));

            createOfflineMap(latitude, longitude, radius);
        }

        //Cancel the process
        else {

            validateLocationButton.setBackgroundColor(ContextCompat.getColor(DownloadMapActivity.this, R.color.colorPrimary));
            validateLocationButton.setText(getString(R.string.select));

            DownloadMapActivity.this.offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE);

            hoveringMarker.setVisibility(View.VISIBLE);

            droppedMarkerLayer = style.getLayer(DROPPED_MARKER_LAYER_ID);
            if (droppedMarkerLayer != null) {
                droppedMarkerLayer.setProperties(visibility(NONE));
            }

            radiusSeekBar.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            progressBar.setProgress(progressBar.getMin());

            OfflineManager om = OfflineManager.getInstance(this);
            om.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
                @Override
                public void onList(OfflineRegion[] offlineRegions) {
                    offlineRegions[offlineRegions.length - 1].delete(DownloadMapActivity.this);
                }
                @Override
                public void onError(String error) { }
            });
        }
    }

    /**
     * this method set the Offline map
     *
     * @param lat the latitude of the picker
     * @param lon the longitude of the picker
     */
    private void createOfflineMap(double lat, double lon, double radius) {
        byte[] metadata;

        double[] coordinates = squareCoordinates(lat, lon, radius);
        OfflineManager offlineManager = OfflineManager.getInstance(this);

        //Get coordinates of the map to download
        LatLngBounds latLngBounds = new LatLngBounds.Builder()
                .include(new LatLng(coordinates[0], coordinates[1])) // Northeast
                .include(new LatLng(coordinates[2], coordinates[3])) // Southwest
                .build();


        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                this.mapStyle,
                latLngBounds,
                10,
                20,
                this.getResources().getDisplayMetrics().density);

        //Set metadata of the map
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(JSON_FIELD_REGION_NAME, this.regionName);
            String json = jsonObject.toString();
            metadata = json.getBytes(JSON_CHARSET);

        } catch (Exception exception) {
            metadata = null;
        }

        if (metadata != null) {
            offlineManager.createOfflineRegion(definition, metadata,
                    new OfflineManager.CreateOfflineRegionCallback() {

                        @Override
                        /*
                         * this method display the download progress bar, monitor the download progress ...
                         */
                        public void onCreate(OfflineRegion offlineRegion) {
                            DownloadMapActivity.this.offlineRegion = offlineRegion;
                            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);

                            startDownload();

                            offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
                                @Override
                                /*
                                 * this method calculate the download percentage and update the progress bar
                                 */
                                public void onStatusChanged(OfflineRegionStatus status) {

                                    double percentage = status.getRequiredResourceCount() >= 0
                                            ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) :
                                            0.0;

                                    if (status.isComplete() && !isDownloadFinish) {
                                        endDownload();
                                    } else if (status.isRequiredResourceCountPrecise()) {
                                        progressBar.setIndeterminate(false);
                                        progressBar.setProgress((int) Math.round(percentage));
                                    }
                                }

                                @Override
                                public void onError(OfflineRegionError error) {
                                    Timber.e("onError reason: %s", error.getReason());
                                    Timber.e("onError message: %s", error.getMessage());
                                }

                                @Override
                                public void mapboxTileCountLimitExceeded(long limit) {
                                    Timber.e("Mapbox tile count limit exceeded: %s", limit);
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Timber.e("Error: %s", error);
                        }
                    });
        }
    }

    /**
     * this method start and show the progress bar
     */
    private void startDownload() {
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
        radiusSeekBar.setVisibility(View.GONE);
    }

    /**
     * this method stop and hide the progress bar
     */
    private void endDownload() {
        progressBar.setIndeterminate(false);
        progressBar.setVisibility(View.GONE);
        isDownloadFinish = true;
        Intent intent = new Intent(this, OffLineMapMenu.class);
        startActivity(intent);
        finish();
    }

    /**
     * this method add the marker image to the map
     *
     * @param loadedMapStyle loaded map style
     */
    private void initDroppedMarker(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addImage("dropped-icon-image", BitmapFactory.decodeResource(
                getResources(), R.drawable.blue_marker));

        loadedMapStyle.addSource(new GeoJsonSource("dropped-marker-source-id"));
        loadedMapStyle.addLayer(new SymbolLayer(DROPPED_MARKER_LAYER_ID,
                "dropped-marker-source-id").withProperties(
                iconImage("dropped-icon-image"),
                visibility(NONE),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        ));
    }


    /**
     * This method is used to reverse geocode where the user has dropped the marker.
     *
     * @param point The location to use for the search
     */
    private void reverseGeocode(final Point point) {
        try {
            MapboxGeocoding client = MapboxGeocoding.builder()
                    .accessToken(getString(R.string.mapbox_access_token))
                    .query(Point.fromLngLat(point.longitude(), point.latitude()))
                    .geocodingTypes(GeocodingCriteria.TYPE_ADDRESS)
                    .build();

            client.enqueueCall(new Callback<GeocodingResponse>() {
                @Override
                public void onResponse(@NonNull Call<GeocodingResponse> call, @NonNull Response<GeocodingResponse> response) {

                }

                @Override
                public void onFailure(@NonNull Call<GeocodingResponse> call, @NonNull Throwable throwable) {

                }
            });
        } catch (ServicesException servicesException) {
            Timber.e("Error geocoding: %s", servicesException.toString());
            servicesException.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    /**
     * this method enable the location plugin
     *
     * @param loadedMapStyle loaded map style
     */
    @SuppressWarnings({"MissingPermission"})
    private void enableLocationPlugin(@NonNull Style loadedMapStyle) {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            LocationComponent locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(

                    this, loadedMapStyle).build());
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING);
            locationComponent.setRenderMode(RenderMode.NORMAL);

        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    /**
     * this methods allows to recover the gps position of the phone
     */
    @Override
    public void onPermissionResult(boolean granted) {
        if (granted && mapboxMap != null) {
            Style style = mapboxMap.getStyle();
            if (style != null) {
                enableLocationPlugin(style);
            }
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Change radius downloaded
     */
    @SuppressLint("SetTextI18n")
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        DownloadMapActivity.this.radiusText.setText(getResources().getString(R.string.downloadRadius) + progress + getResources().getString(R.string.km));
    }

    /**
     * this method calculates the coordinates that correspond to a zone around the coordinates in parameters
     *
     * @param latitude  the latitude of the picker
     * @param longitude the longitude of the picker
     * @param radius    the radius
     * @return an array that includes the different coordinates
     */
    public static double[] squareCoordinates(double latitude, double longitude, double radius) {
        double[] ret = new double[4];
        ret[0] = latitude + (radius / (110));
        ret[1] = longitude + (radius / (75));

        ret[2] = latitude - (radius / (110));
        ret[3] = longitude - (radius / (75));

        return ret;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }


    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDelete() {

    }

    @Override
    public void onError(String error) {

    }
}