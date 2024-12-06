/**
 * MIT License
 * <p>
 * Copyright (c) 2019 Douglas Nassif Roma Junior
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.douglasjunior.reactNativeGetLocation.util;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.content.Context;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableNativeMap;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;

import java.util.Timer;
import java.util.TimerTask;

public class GetLocation {
    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationManager locationManager;
    private Context context;

    private Timer timer;
    private LocationListener listener;
    private Promise promise;

    public GetLocation(Context context, LocationManager locationManager,FusedLocationProviderClient fusedLocationClient) {
        this.fusedLocationClient = fusedLocationClient;
        this.locationManager = locationManager;
        this.context = context;
    }

    public void get(ReadableMap options, final Promise promise) {
        this.promise = promise;
        try {
            if (!isLocationEnabled()) {
                promise.reject("UNAVAILABLE", "Location not available");
                return;
            }

            boolean enableHighAccuracy = options.hasKey("enableHighAccuracy") && options.getBoolean("enableHighAccuracy");
            long timeout = options.hasKey("timeout") ? (long) options.getDouble("timeout") : 0;
            Log.d("ValidandoVersion", "validando afuera");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 14 o superior
                Log.d("ValidandoVersion-NEW", "NEW");
                useNewLocationMethod(enableHighAccuracy, timeout, promise,context);
            } else {
                Log.d("ValidandoVersion-OLD", "OLD");
                useOldLocationMethod(enableHighAccuracy, timeout, promise,context);
            }
        } catch (SecurityException ex) {
            ex.printStackTrace();
            stop();
            promise.reject("UNAUTHORIZED", "Location permission denied", ex);
        } catch (Exception ex) {
            ex.printStackTrace();
            stop();
            promise.reject("UNAVAILABLE", "Location not available-error", ex);
        }
    }


    public static boolean isMockLocation(Context context, Location location) {
        if (location == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return location.isFromMockProvider();
        } else {
            return !TextUtils.isEmpty(Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ALLOW_MOCK_LOCATION)) || location.getProvider().equals("mock");
        }
    }

    public synchronized void cancel() {
        if (promise == null) {
            return;
        }
        try {
            promise.reject("CANCELLED", "Location cancelled by another request");
            stop();
            clearReferences();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void stop() {
        if (timer != null) {
            timer.cancel();
        }
        if (listener != null) {
            locationManager.removeUpdates(listener);
        }
    }

    private void clearReferences() {
        promise = null;
        timer = null;
        listener = null;
    }

    private boolean isLocationEnabled() {
        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }


    private void useNewLocationMethod(boolean enableHighAccuracy, long timeout, final Promise promise, Context context) {
        Log.d("useNewLocationMethod", "useNewLocationMethod called");
        try {
            long locationTimeout = timeout > 0 ? timeout : 10000; // Default interval of 10 seconds
            int priority = enableHighAccuracy ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY;

            LocationRequest locationRequest = new LocationRequest.Builder(priority, locationTimeout)
                    .setGranularity(Granularity.GRANULARITY_FINE)
                    .setMinUpdateDistanceMeters(10) // Minimum update distance
                    .build();

            LocationCallback locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && !locationResult.getLocations().isEmpty()) {
                        Location location = locationResult.getLastLocation();
                        if (location != null) {
                            WritableNativeMap resultLocation = new WritableNativeMap();
                            boolean isMockLocation = isMockLocation(GetLocation.this.context, location);

                            resultLocation.putString("provider", location.getProvider());
                            resultLocation.putDouble("latitude", location.getLatitude());
                            resultLocation.putDouble("longitude", location.getLongitude());
                            resultLocation.putDouble("accuracy", location.getAccuracy());
                            resultLocation.putDouble("altitude", location.getAltitude());
                            resultLocation.putDouble("speed", location.getSpeed());
                            resultLocation.putDouble("bearing", location.getBearing());
                            resultLocation.putDouble("time", location.getTime());
                            resultLocation.putBoolean("isFakeLocation", isMockLocation);

                            promise.resolve(resultLocation);
                            stop();
                            clearReferences();
                        }
                    }
                }
            };

//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
//                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                // TODO: Handle permission request if not granted
//                return;
//            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d("useNewLocationMethod", "useNewLocationMethod called success");
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject("ERROR", "Error using new location method", ex);
        }
    }


    private void useOldLocationMethod(boolean enableHighAccuracy, long timeout, final Promise promise, Context context) {
        Log.d("LocationMethod", "useOldLocationMethod called");

        Criteria criteria = new Criteria();
        criteria.setAccuracy(enableHighAccuracy ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);
        Log.d("LocationMethod", "Criteria set with accuracy: " + (enableHighAccuracy ? "ACC_FINE" : "ACC_COARSE"));

        listener = new LocationListener() {
            private boolean locationFound = false;

            @Override
            public synchronized void onLocationChanged(Location location) {
                Log.d("LocationMethod", "onLocationChanged called");
                if (location != null && !locationFound) {
                    locationFound = true;
                    WritableNativeMap resultLocation = new WritableNativeMap();
                    boolean isMockLocation = isMockLocation(GetLocation.this.context, location);
                    Log.d("LocationMethod", "Location received: Latitude=" + location.getLatitude() + ", Longitude=" + location.getLongitude());

                    resultLocation.putString("provider", location.getProvider());
                    resultLocation.putDouble("latitude", location.getLatitude());
                    resultLocation.putDouble("longitude", location.getLongitude());
                    resultLocation.putDouble("accuracy", location.getAccuracy());
                    resultLocation.putDouble("altitude", location.getAltitude());
                    resultLocation.putDouble("speed", location.getSpeed());
                    resultLocation.putDouble("bearing", location.getBearing());
                    resultLocation.putDouble("time", location.getTime());
                    resultLocation.putBoolean("isFakeLocation", isMockLocation);

                    promise.resolve(resultLocation);
                    Log.d("LocationMethod", "Location data sent to promise");
                    stop();
                    clearReferences();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d("LocationMethod", "onStatusChanged: provider=" + provider + ", status=" + status);
            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.d("LocationMethod", "onProviderEnabled: provider=" + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.d("LocationMethod", "onProviderDisabled: provider=" + provider);
            }
        };

//        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
//                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            Log.d("LocationMethod", "Permissions not granted");
//            return;
//        }

        locationManager.requestLocationUpdates(0L, 0F, criteria, listener, Looper.myLooper());
//        Log.d("LocationMethod", "Location updates requested");

        if (timeout > 0) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        Log.d("LocationMethod", "Timeout reached");
                        promise.reject("TIMEOUT", "Location timed out");
                        stop();
                        clearReferences();
                    } catch (Exception ex) {
                        Log.e("LocationMethod", "Error in timeout task", ex);
                    }
                }
            }, timeout);
        }
    }


}
