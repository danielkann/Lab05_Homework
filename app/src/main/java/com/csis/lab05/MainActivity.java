package com.csis.lab05; //package we're in


//android imports

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;


//PURE DATA IMPORTS

import org.puredata.android.io.AudioParameters;
import org.puredata.android.io.PdAudio;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import java.io.File;
import java.io.IOException;


public class MainActivity extends AppCompatActivity implements SensorEventListener,LocationListener{

    private PdUiDispatcher dispatcher; //must declare this to use later, used to receive data from sendEvents

    TextView xval;
    TextView yval;
    TextView zval;
    TextView lati;
    TextView longi;

    double latitude;
    double longitude;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);//Mandatory
        setContentView(R.layout.activity_main);//Mandatory

        Switch onOffSwitch = (Switch) findViewById(R.id.onOff);//declared the switch here pointing to id onOffSwitch
        Switch onOffLatiSwitch = (Switch) findViewById(R.id.onOffLati);

        Button send = (Button) findViewById(R.id.SendInput);
        final TextView latiSend = (TextView) findViewById(R.id.latiInput);//used to send latiInput when clicking on the send Button
        final TextView longiSend = (TextView) findViewById(R.id.longiInput);//used to send longiInput

        xval = (TextView) findViewById (R.id.xValue);
        yval = (TextView) findViewById (R.id.yValue);
        zval = (TextView) findViewById (R.id.zValue);
        lati = (TextView) findViewById(R.id.latiValue);
        longi = (TextView) findViewById(R.id.longiValue);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this,mAccelerometer,SensorManager.SENSOR_DELAY_NORMAL);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return;
        }

        LocationManager mlocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mlocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0 , 1, this);

        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        //For declaring and initialising XML items, Always of form OBJECT_TYPE VARIABLE_NAME = (OBJECT_TYPE) findViewById(R.id.ID_SPECIFIED_IN_XML);
        try { // try the code below, catch errors if things go wrong
            initPD(); //method is below to start PD
            loadPDPatch("synth.pd"); // This is the name of the patch in the zip
        } catch (IOException e) {
            e.printStackTrace(); // print error if init or load patch fails.
            finish(); // end program
        }

        //Check to see if onOffSwitch value changes
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                float val = (isChecked) ?  1.0f : 0.0f; // value = (get value of isChecked, if true val = 1.0f, if false val = 0.0f)
                sendFloatPD("onOff", val); //send value to patch, receiveEvent names onOff

            }
        });

        //Check to see if onOffSwitch value changes
        onOffLatiSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                float val = (isChecked) ?  1.0f : 0.0f; // value = (get value of isChecked, if true val = 1.0f, if false val = 0.0f)
                sendFloatPD("onOffLati", val); //send value to patch, receiveEvent names onOff

            }
        });

        //<------BUTTON CLICK LISTENER--------------->
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                sendFloatPD("destinationLatitude", Float.parseFloat(latiSend.getText().toString()));
                sendFloatPD("destinationLongitude", Float.parseFloat(longiSend.getText().toString()));

            }
        });
    }




    @Override //If screen is resumed
    protected void onResume() {
        super.onResume();
        PdAudio.startAudio(this);
        //mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }


    @Override//If we switch to other screen
    protected void onPause() {
        super.onPause();
        PdAudio.stopAudio();
        // mSensorManager.unregisterListener(this);
    }

    //METHOD TO SEND FLOAT TO PUREDATA PATCH
    public void sendFloatPD(String receiver, Float value)//REQUIRES (RECEIVEEVENT NAME, FLOAT VALUE TO SEND)
    {
        PdBase.sendFloat(receiver, value); //send float to receiveEvent
    }

    //METHOD TO SEND BANG TO PUREDATA PATCH
    public void sendBangPD(String receiver) {

        PdBase.sendBang(receiver); //send bang to receiveEvent
    }

    //<---THIS METHOD LOADS SPECIFIED PATCH NAME----->
    private void loadPDPatch(String patchName) throws IOException {
        File dir = getFilesDir(); //Get current list of files in directory
        try {
            IoUtils.extractZipResource(getResources().openRawResource(R.raw.synth), dir, true); //extract the zip file in raw called synth
            File pdPatch = new File(dir, patchName); //Create file pointer to patch
            PdBase.openPatch(pdPatch.getAbsolutePath()); //open patch
        } catch (IOException e) {

        }
    }

    //<---THIS METHOD INITIALISES AUDIO SERVER----->
    private void initPD() throws IOException {
        int sampleRate = AudioParameters.suggestSampleRate(); //get sample rate from system
        PdAudio.initAudio(sampleRate, 0, 2, 8, true); //initialise audio engine

        dispatcher = new PdUiDispatcher(); //create UI dispatcher
        PdBase.setReceiver(dispatcher); //set dispatcher to receive items from puredata patches

    }


    //methods from abstract Clas SensorEventListener
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            sendFloatPD("xValue",x);
            xval.setText(String.valueOf(x));
            sendFloatPD("yValue",y);
            yval.setText(String.valueOf(y));
            sendFloatPD("zValue",z);
            zval.setText(String.valueOf(z));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //methods from abstract class LocationListener
    @Override
    public void onLocationChanged(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();

        Log.i("MyLocation", Double.toString(latitude) + " " + Double.toHexString(longitude));
        sendFloatPD("latitude",Float.parseFloat(Double.toString(latitude)));
        lati.setText(String.valueOf(latitude));
        sendFloatPD("longitude",Float.parseFloat(Double.toString(longitude)));
        longi.setText(String.valueOf(longitude));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
