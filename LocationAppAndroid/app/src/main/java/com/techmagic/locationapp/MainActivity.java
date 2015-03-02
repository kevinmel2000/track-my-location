package com.techmagic.locationapp;

import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.database.ContentObserver;
import android.location.Location;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.ErrorDialogFragment;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.techmagic.locationapp.data.Data;
import com.techmagic.locationapp.data.DataHelper;
import com.techmagic.locationapp.data.model.LocationData;
import com.techmagic.locationapp.map.MapResultsActivity;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;


public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getCanonicalName();
    private static final int REQUEST_RESOLVE_ERROR = 9999;
    private GoogleApiClient googleApiClient ;
    private LocationApplication app;
    private Handler handler = new Handler();

    private ContentObserver contentObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateLocationData();
        }
    };

    @InjectView(R.id.tv_last_update) TextView tvLastUpdate;
    @InjectView(R.id.radio_group) RadioGroup radioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        app = (LocationApplication) getApplication();

        setupRadioGroup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerContentObservers();
        updateLocationData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unRegisterContentObservers();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            if (resultCode == RESULT_OK) {
                connectGoogleApiClient();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_map) {
            Intent i = new Intent(this, MapResultsActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        startTrackLocationService();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed");
        if (result.hasResolution()) {
            try {
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                googleApiClient.connect();
            }
        } else {
            showErrorDialog(result.getErrorCode());
        }
    }

    @OnClick(R.id.btn_start_tracking)
    public void startTracking() {
        connectGoogleApiClient();
    }

    @OnClick(R.id.btn_clear_data)
    public void clearData() {
        DataHelper.getInstance(getApplicationContext()).deleteAllLocations();
    }

    @OnClick(R.id.btn_stop_tracking)
    public void stopTracking() {
        stopService(new Intent(this, TrackLocationService.class));
    }

    private void startTrackLocationService() {
        startService(new Intent(this, TrackLocationService.class));
    }

    private void registerContentObservers() {
        getContentResolver().registerContentObserver(Data.LocationData.URI, false, contentObserver);
    }

    private void unRegisterContentObservers() {
        getContentResolver().unregisterContentObserver(contentObserver);
    }

    private void updateLocationData() {
        DataHelper dataHelper = DataHelper.getInstance(this);
        LocationData location = dataHelper.getLastLocation();
        if (location != null) {
            String time = Utils.formatTime(location.getTimestamp());
            tvLastUpdate.setText(time);
        }
    }

    private void setupRadioGroup() {

        switch (app.getLocationRequestData()) {
            case FREQUENCY_HIGH:
                radioGroup.check(R.id.radio_high);
                break;
            case FREQUENCY_MEDIUM:
                radioGroup.check(R.id.radio_medium);
                break;
            case FREQUENCY_LOW:
                radioGroup.check(R.id.radio_low_);
                break;
        }

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.radio_high:
                        app.setLocationRequestData(LocationRequestData.FREQUENCY_HIGH);
                        break;
                    case R.id.radio_medium:
                        app.setLocationRequestData(LocationRequestData.FREQUENCY_MEDIUM);
                        break;
                    case R.id.radio_low_:
                        app.setLocationRequestData(LocationRequestData.FREQUENCY_LOW);
                        break;
                }
                stopTracking();
                startTracking();
            }
        });
    }

    private int createGoogleApiClient() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        switch (status) {
            case ConnectionResult.SUCCESS:
                googleApiClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
                break;
            case ConnectionResult.SERVICE_MISSING:
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
            case ConnectionResult.SERVICE_DISABLED:
                Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, REQUEST_RESOLVE_ERROR);
                dialog.show();
                break;
        }
        return status;
    }

    private void connectGoogleApiClient() {
        if (googleApiClient == null) {
            if (createGoogleApiClient() != ConnectionResult.SUCCESS) {
                return;
            }
        }

        if (!(googleApiClient.isConnected() || googleApiClient.isConnecting())) {
            googleApiClient.connect();
        } else {
            Log.d(TAG, "Client is connected");
            startTrackLocationService();
        }
    }

    private void showErrorDialog(int errorCode) {
        //TODO add errors handling
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt("dialog_error", errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getFragmentManager(), "errordialog");
    }

}