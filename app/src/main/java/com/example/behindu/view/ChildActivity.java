package com.example.behindu.view;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.developer.kalert.KAlertDialog;
import com.example.behindu.R;
import com.example.behindu.model.Child;
import com.example.behindu.model.Follower;
import com.example.behindu.model.LastLocation;
import com.example.behindu.model.UserLocation;
import com.example.behindu.services.LocationService;
import com.example.behindu.util.SaveSharedPreference;
import com.example.behindu.viewmodel.ChildViewModel;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.GeoPoint;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static com.example.behindu.model.Constants.EMERGENCY_NUMBER_POLICE;
import static com.example.behindu.model.Constants.ERROR_DIALOG_REQUEST;
import static com.example.behindu.model.Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION;
import static com.example.behindu.model.Constants.PERMISSIONS_REQUEST_ENABLE_CALL;
import static com.example.behindu.model.Constants.PERMISSIONS_REQUEST_ENABLE_GPS;
import static com.example.behindu.model.Constants.PERMISSIONS_REQUEST_ENABLE_SMS;

public class ChildActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Child Activity" ;
    private ChildViewModel mViewModel = new ChildViewModel();
    private boolean mLocationPermissionGranted = false;
    private boolean mCallPermissionGranted = false;
    private boolean mSmsPermissionGranted = false;
    private FusedLocationProviderClient mFusedLocationClient;
    private UserLocation mUserLocation;
    private EditText mEnterCodeEt;
    private Button mEnterCodeBtn;
    private TextView mConnectedStatues;
    private Child mChild;
    private TextInputLayout mCodeError;
    private List<Child> mChildList;
    private Intent mServiceIntent;
    private AlarmReceiverTest mBatteryLevelReceiver;
    private MediaPlayer mAlarm;


    private LocationService mService = null;
    private boolean mBound = false;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder)service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mService = null;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
    }



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.child_page);


        Dexter.withActivity(this)
                .withPermissions(Arrays.asList(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ))
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {

                        bindService(new Intent(ChildActivity.this,
                                        LocationService.class),mServiceConnection,
                                Context.BIND_AUTO_CREATE);

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {

                    }
                }).check();


        Log.d(TAG, "onCreate: ChildActivity"+ "Arrive");

        getPermissions();

        mAlarm  = MediaPlayer.create(getBaseContext(),R.raw.alarm_sound);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mEnterCodeEt = findViewById(R.id.enter_code_Et);


        mConnectedStatues = findViewById(R.id.connect_status_tv);

        mCodeError = findViewById(R.id.enter_code_layout);

        Button sosBtn = findViewById(R.id.sos_btn);
        sosBtn.setOnClickListener(this);

        Button sendMessageToFollower = findViewById(R.id.send_message_follower_btn);
        sendMessageToFollower.setOnClickListener(this);

        Button callToFollower = findViewById(R.id.call_the_follower_btn);
        callToFollower.setOnClickListener(this);

        mEnterCodeBtn = findViewById(R.id.apply_code_btn);
        mEnterCodeBtn.setOnClickListener(this);


        Button signOutBtn = findViewById(R.id.signOutChildBtn);
        signOutBtn.setOnClickListener(this);

        mViewModel.getSound(new onCallbackFollowerSound() {
            @Override
            public void setSound(Boolean sound) {
                if(sound){
                    mAlarm.start();
                }
                mViewModel.setSound(false);
            }
        });


    }

    private void getAlarmReceiver() {
        mBatteryLevelReceiver = new  AlarmReceiverTest();
        registerReceiver(mBatteryLevelReceiver, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));

    }

    private void getUserDetails() {
        if (mUserLocation == null) {
            mUserLocation = new UserLocation();
        }


        mViewModel.getUserDetails(
                new childLocationCallback() {
                    @Override
                    public void setLocation(Child child) {
                        mChild = child;
                        getAlarmReceiver(); // Get the battery status
                        mUserLocation.setChild(child);
                        if (child.isConnected()) {
                            mEnterCodeEt.setVisibility(View.GONE);
                            mEnterCodeBtn.setVisibility(View.GONE);
                            mCodeError.setVisibility(View.GONE);
                            mConnectedStatues.setText(getString(R.string.connected_to_follower));
                            mConnectedStatues.setTextColor(getResources().getColor(R.color.connectedToFollower));
                        } else {
                            mConnectedStatues.setText(getString(R.string.not_connected));
                            mConnectedStatues.setTextColor(getResources().getColor(R.color.notConnectedToFollower));
                        }
                        getLastKnownLocation();
                    }
                });
    }


    private void getPermissions() {
        getLocationPermission();
    }

    private void saveUserLocation(UserLocation mUserLocation) {
        mViewModel.saveUserLocation(mUserLocation);
    }

    /* Get the last known location of child*/

    private void getLastKnownLocation() {
        mFusedLocationClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                if (task.isSuccessful() && task.getResult() != null) {
                    final Location location = task.getResult();
                    mViewModel.getLocationList(new locationList() {
                        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                        @Override
                        public void onCallbackLocationList(List<LastLocation> lastLocationList) {
                            mUserLocation.getChild().setRoutes(geoPoint);
                            if (lastLocationList == null) {
                                lastLocationList = new ArrayList<>();
                                lastLocationList.add(new LastLocation(geoPoint, Calendar.getInstance().getTime()));
                                mUserLocation.setList(lastLocationList);
                                saveUserLocation(mUserLocation);

                                requestLocationUpdates();
                               // mServiceIntent.putExtra("UserLocations", mUserLocation);
                              //  startLocationService(mUserLocation); // Starting the service after all the list has been load
                                newLocationNotify(); // Making a notification for follower that new location has been added to the location list
                            }
                            else {
                                lastLocationList.add(new LastLocation(geoPoint, Calendar.getInstance().getTime()));
                                mUserLocation.setList(lastLocationList);
                                saveUserLocation(mUserLocation);
                                requestLocationUpdates();
                              //  startLocationService(mUserLocation); // Starting the service after all the list has been load
                                newLocationNotify(); // Making a notification for follower that new location has been added to the location list
                            }
                                }


                    });
                }
            }
        });
    }

    private void requestLocationUpdates() {
        Intent intent = new Intent(ChildActivity.this,LocationService.class);
        intent.putExtra("UserLocation",mUserLocation);
        mService.requestLocationUpdates(intent);
    }

// Making a notification for follower that new location has been added to the location list

    private void newLocationNotify() {
        mViewModel.setNewLocationNotify(true,mChild.getUserId());
    }


    private void startLocationService(UserLocation mUserLocation) {
        if (!isLocationServiceRunning()) {
            mServiceIntent = new Intent(this, LocationService.class);
            mServiceIntent.putExtra("UserLocations", mUserLocation);

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ChildActivity.this.startForegroundService(mServiceIntent);

            } else {
                startService(mServiceIntent);
            }
        }
    }

    private boolean isLocationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    private boolean checkMapServices() {
        if (isServicesOK()) {
            return isMapsEnabled();
        }
        return false;
    }

    private void buildAlertMessageNoGps() {

        final KAlertDialog dialog =   new KAlertDialog(this, KAlertDialog.WARNING_TYPE);
                dialog.setTitleText(getString(R.string.GPS_off_title))
                .setContentText(getString(R.string.GPS_off_info))
                .setConfirmText(getString(R.string.settings_answer))
                .confirmButtonColor(R.color.colorPrimaryDark)
                .setConfirmClickListener(new KAlertDialog.KAlertClickListener() {
                    @Override
                    public void onClick(KAlertDialog kAlertDialog) {
                        Intent enableGpsIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(enableGpsIntent, PERMISSIONS_REQUEST_ENABLE_GPS);
                    }
                })
                .setCancelText(getString(R.string.no))
                .setCancelClickListener(new KAlertDialog.KAlertClickListener() {
                    @Override
                    public void onClick(KAlertDialog kAlertDialog) {
                        dialog.cancel();
                        SaveSharedPreference.clearUserName(ChildActivity.this);
                        moveToMainActivity();
                        finish();
                    }
                }).show();
    }

    public boolean isMapsEnabled() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(manager !=null) {
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mViewModel.isGPSOn(false); // tells the follower the gps is off
                buildAlertMessageNoGps();
                return false;
            }
            else{
                mViewModel.isGPSOn(true); // tells the follower the gps is on
            }
        }
        return true;
    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = false;
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void getCallingPermission() {

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            mCallPermissionGranted = false;
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CALL_PHONE},
                    PERMISSIONS_REQUEST_ENABLE_CALL);
        }


    }

    private void getSmsPermissions() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED)
        {
            mSmsPermissionGranted = false;
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.SEND_SMS},
                    PERMISSIONS_REQUEST_ENABLE_SMS);
        }

    }

    public boolean isServicesOK() {
        Log.d(TAG, "isServicesOK: checking google services version");

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ChildActivity.this);

        if (available == ConnectionResult.SUCCESS) {
            //everything is fine and the user can make map requests
            Log.d(TAG, "isServicesOK: Google Play Services is working");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            Log.d(TAG, "isServicesOK: an error occured");
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(ChildActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
        } else {
            Toast.makeText(this, "You can't make map requests", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                    mViewModel.isGPSOn(true); // tells the follower the gps is on
                    getUserDetails();
                } else {
                    mViewModel.isGPSOn(false);   // tells the follower the gps is off
                    buildAlertMessageNoGps();
                }
            }
            // If request is cancelled, the result arrays are empty.
            case PERMISSIONS_REQUEST_ENABLE_CALL:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mCallPermissionGranted = true;

                }
                // If request is cancelled, the result arrays are empty.
            case PERMISSIONS_REQUEST_ENABLE_SMS:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mSmsPermissionGranted = true;
                }
        }
    }

    private void getAllUsers() {
        final String code = mEnterCodeEt.getText().toString().trim();
        if (code.length() != 6) {
            mCodeError.setError(getString(R.string.code_error_child));
            return;
        }
        mViewModel.getAllUsers(new followerList() {
            @Override
            public void onCallbackUsersList(ArrayList<Follower> follower) {
                mChildList = new ArrayList<>();
                for (Follower f : follower) {
                    if (f.getFollowingId().equals(code)) {
                        initConnection(f);
                    }
                }

                if (mChildList.isEmpty()) {
                    setDialogFailed();
                }
            }
        });
    }

    // Initialize the connection between the child and follower
    private void initConnection(Follower f) {
        mChild.setFollowerId(f.getUserId());
        mChild.setConnected(true);
        mChildList.add(mChild);
        f.setChildList(mChildList);
        mViewModel.updateChild(mChild);
        mViewModel.saveChildList(f);
        mChildList.get(0).setConnected(true);
        mEnterCodeBtn.setVisibility(View.GONE);
        mEnterCodeEt.setVisibility(View.GONE);
        mCodeError.setVisibility(View.GONE);
        mConnectedStatues.setText(getString(R.string.connected_to_follower));
        mConnectedStatues.setTextColor(getResources().getColor(R.color.connectedToFollower));
        setDialogSucceed();
    }

    private void signOut() {

        if(mBound){
            unbindService(mServiceConnection);
            mBound = false;
        }

        unregisterReceiver(mBatteryLevelReceiver); // unregister the battery receiver
        mViewModel.setStatus(false);
        mViewModel.signOut();
        SaveSharedPreference.clearUserName(this);
        moveToMainActivity();
    }

    private void callEmergency(int phoneNumber) {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            mCallPermissionGranted = true;
        }

        if (mCallPermissionGranted) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(callIntent);
        }
        else{
            getCallingPermission();
        }

    }

    private void sendSms(String phoneNumber, String message) {
       /* if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            mSmsPermissionGranted = true;
        }*/


        if (mSmsPermissionGranted) {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, getString(R.string.message_sent), Toast.LENGTH_SHORT).show();
        }
        else{
            getSmsPermissions();
        }
    }

    private void moveToMainActivity () {
        Intent i = new Intent(ChildActivity.this, MainActivity.class);
        startActivity(i);
        this.finish();
    }

    public void setDialogSucceed(){
        new KAlertDialog(this, KAlertDialog.SUCCESS_TYPE)
                .setTitleText(getString(R.string.connection_succeed))
                .setContentText(getString(R.string.you_are_connected_to_follower))
                .setConfirmText(getString(R.string.ok_confirmation))
                .confirmButtonColor(R.color.colorPrimaryDark)
                .show();
    }

    public void setDialogFailed(){
        new KAlertDialog(this, KAlertDialog.ERROR_TYPE)
                .setTitleText(getString(R.string.connection_failed))
                .setContentText(getString(R.string.connection_failed_instructions))
                .setConfirmText(getString(R.string.ok_confirmation))
                .confirmButtonColor(R.color.colorPrimaryDark)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ENABLE_GPS: {
                if(!mLocationPermissionGranted){
                    getLocationPermission();
                }
                else
                    getUserDetails();
            }
            case PERMISSIONS_REQUEST_ENABLE_CALL:
                if(!mCallPermissionGranted) {
                    getCallingPermission();
                }


            case PERMISSIONS_REQUEST_ENABLE_SMS:
                if(!mSmsPermissionGranted){
                    getSmsPermissions();
                }

        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Thread thread = new Thread(){
            public void run(){
                mViewModel.setStatus(true);
            }
        };
        thread.start();


        if(!checkMapServices()){
            getLocationPermission();
        }
        else
            getUserDetails();

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(mBound){
            unbindService(mServiceConnection);
            mBound = false;
        }
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        mViewModel.setStatus(false);
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int mFollowerPhoneNumber = mUserLocation.getChild().getPhoneNumber();
        switch(v.getId()){
            case R.id.sos_btn:
                callEmergency(EMERGENCY_NUMBER_POLICE);
                break;
            case R.id.call_the_follower_btn:
                callEmergency(mFollowerPhoneNumber);
                break;
            case R.id.send_message_follower_btn:
                sendSms(String.valueOf(mFollowerPhoneNumber),getString(R.string.arrive_message_child));
                break;
            case R.id.apply_code_btn:
                getAllUsers();
                break;
            case R.id.signOutChildBtn:
                signOut();
                break;
        }


    }


    

    public class AlarmReceiverTest extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            int batteryLevel = intent.getIntExtra(
                    BatteryManager.EXTRA_LEVEL, 0);
            int maxLevel = intent
                    .getIntExtra(BatteryManager.EXTRA_SCALE, 0);
            int batteryHealth = intent.getIntExtra(
                    BatteryManager.EXTRA_HEALTH,
                    BatteryManager.BATTERY_HEALTH_UNKNOWN);
            float batteryPercentage = ((float) batteryLevel / (float) maxLevel) * 100;

            mChild.setBatteryPercent((int)batteryPercentage);
            mViewModel.setBattery(mChild);
        }




    }

    public interface childLocationCallback{
        void setLocation(Child child);
    }

    public interface locationList{
        void onCallbackLocationList(List<LastLocation> geoPointList);
    }

    public interface followerList{
        void onCallbackUsersList(ArrayList<Follower> follower);
    }

    public interface onCallbackFollowerSound{
        void setSound(Boolean sound);
    }

}

