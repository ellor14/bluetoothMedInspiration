package com.polidea.rxandroidble2.sample.example1_scanning;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.exceptions.BleScanException;
import com.polidea.rxandroidble2.sample.DeviceActivity;
import com.polidea.rxandroidble2.sample.R;
import com.polidea.rxandroidble2.sample.SampleApplication;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class ScanActivity extends AppCompatActivity {
// opretter variabler
    @BindView(R.id.scan_toggle_btn)
    Button scanToggleButton;
    @BindView(R.id.scan_results)
    RecyclerView recyclerView;
    private RxBleClient rxBleClient;
    private Disposable scanDisposable;
    private ScanResultsAdapter resultsAdapter;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    // opretter siden
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example1);
        ButterKnife.bind(this);

        rxBleClient = SampleApplication.getRxBleClient(this);
        configureResultList();
    }
    // når der klikkes på knappen
    @OnClick(R.id.scan_toggle_btn)
    public void onScanToggleClick() {
        // sikre tilladelse
        checkLocationPermission();

        // hvis der channes stop med at channe ved at fjerne objekt
        if (isScanning()) {
            scanDisposable.dispose();

        // hvis der ikke channes. bruges funktion fra rxbleclient til at scan med
        } else {
            scanDisposable = rxBleClient.scanBleDevices(
                    //defineres hvordan der skal scanens, input til scanbledivices funktionne
                    new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                            .build(),
                    new ScanFilter.Builder()
//                            .setDeviceAddress("B4:99:4C:34:DC:8B")
                            // add custom filters if needed
                            .build()
            )
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(this::dispose)
                    .subscribe(resultsAdapter::addScanResult, this::onScanFailure);
        }
// ændre tekst på knap
        updateButtonUIState();
    }

 // funktion der fanger fejl
    private void handleBleScanException(BleScanException bleScanException) {
        final String text;

        switch (bleScanException.getReason()) {
            case BleScanException.BLUETOOTH_NOT_AVAILABLE:
                text = "Bluetooth is not available";
                break;
            case BleScanException.BLUETOOTH_DISABLED:
                text = "Enable bluetooth and try again";
                break;
            case BleScanException.LOCATION_PERMISSION_MISSING:
                text = "On Android 6.0 location permission is required. Implement Runtime Permissions";
                break;
            case BleScanException.LOCATION_SERVICES_DISABLED:
                text = "Location services needs to be enabled on Android 6.0";
                break;
            case BleScanException.SCAN_FAILED_ALREADY_STARTED:
                text = "Scan with the same filters is already started";
                break;
            case BleScanException.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                text = "Failed to register application for bluetooth scan";
                break;
            case BleScanException.SCAN_FAILED_FEATURE_UNSUPPORTED:
                text = "Scan with specified parameters is not supported";
                break;
            case BleScanException.SCAN_FAILED_INTERNAL_ERROR:
                text = "Scan failed due to internal error";
                break;
            case BleScanException.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                text = "Scan cannot start due to limited hardware resources";
                break;
            case BleScanException.UNDOCUMENTED_SCAN_THROTTLE:
                text = String.format(
                        Locale.getDefault(),
                        "Android 7+ does not allow more scans. Try in %d seconds",
                        secondsTill(bleScanException.getRetryDateSuggestion())
                );
                break;
            case BleScanException.UNKNOWN_ERROR_CODE:
            case BleScanException.BLUETOOTH_CANNOT_START:
            default:
                text = "Unable to start scanning";
                break;
        }
        Log.w("EXCEPTION", text, bleScanException);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // høre til der der behandler fejl
    private long secondsTill(Date retryDateSuggestion) {
        return TimeUnit.MILLISECONDS.toSeconds(retryDateSuggestion.getTime() - System.currentTimeMillis());
    }

    // hvis man forlader skærmen stopper scanningen
    @Override
    public void onPause() {
        super.onPause();

        if (isScanning()) {
            /*
             * Stop scanning in onPause callback. You can use rxlifecycle for convenience. Examples are provided later.
             */
            scanDisposable.dispose();
        }
    }


    // hvorfor bliver den kladt fra oncreat ?????????
    private void configureResultList() {
        // noget i forhold til layout
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(recyclerLayoutManager);

        // henter resultater fra scanningen
        resultsAdapter = new ScanResultsAdapter();

        //sætter resultat i recyclerView
        recyclerView.setAdapter(resultsAdapter);

        // når der trykkes på et  objekt
        resultsAdapter.setOnAdapterItemClickListener(view -> {
            //tjekker hvilket objekt og sender det videre til nu funktion
            final int childAdapterPosition = recyclerView.getChildAdapterPosition(view);
            final ScanResult itemAtPosition = resultsAdapter.getItemAtPosition(childAdapterPosition);
            onAdapterItemClick(itemAtPosition);
        });
    }

    // tjekker om channing er i gang
    private boolean isScanning() {
        return scanDisposable != null;
    }


// når der er klikket op bestemt resultat i listen
    private void onAdapterItemClick(ScanResult scanResults) {
        // finder det valgte objekts add
        final String macAddress = scanResults.getBleDevice().getMacAddress();

        // opretter ny intent der skal gå til deviceActivity
        final Intent intent = new Intent(this, DeviceActivity.class);
        // ligger add ind i intent
        intent.putExtra(DeviceActivity.EXTRA_MAC_ADDRESS, macAddress);
        // udføre intent
        startActivity(intent);

    }

    private void onScanFailure(Throwable throwable) {

        if (throwable instanceof BleScanException) {
            handleBleScanException((BleScanException) throwable);
        }
    }

    private void dispose() {
        scanDisposable = null;
        resultsAdapter.clearScanResults();
        updateButtonUIState();
    }

    private void updateButtonUIState() {
        scanToggleButton.setText(isScanning() ? R.string.stop_scan : R.string.start_scan);
    }

    // tjekker for tilladelser
    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("")
                        .setMessage("")
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(ScanActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }
}
