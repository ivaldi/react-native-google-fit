package com.reactnative.googlefit;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Device;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.SessionReadResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;

import static java.text.DateFormat.getDateInstance;
import static java.text.DateFormat.getTimeInstance;

public class SleepHistory {
    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;
    private DataSet Dataset;
    private DataType dataType;

    private static final String TAG = "SleepHistory";

    public SleepHistory(ReactContext reactContext, GoogleFitManager googleFitManager){
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
    }

    public void aggregateDataByDate(long startDate, long endDate, final Callback successCallback) {

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        dateFormat.setTimeZone(TimeZone.getDefault());

        SessionReadRequest request = new SessionReadRequest.Builder()
                .readSessionsFromAllApps()
                .includeSleepSessions()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeInterval((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        GoogleSignInOptionsExtension fitnessOptions =
                FitnessOptions.builder()
                        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
                        .build();
        final  GoogleSignInAccount gsa = GoogleSignIn.getAccountForExtension(this.mReactContext, fitnessOptions);

        Fitness.getSessionsClient(this.mReactContext, gsa)
                .readSession(request)
                .addOnSuccessListener(new OnSuccessListener<SessionReadResponse>() {
                    @Override
                    public void onSuccess(SessionReadResponse response) {
                        List<Session> sleepSessions = response.getSessions()
                            .stream()
                            .filter(s -> s.getActivity().equals(FitnessActivities.SLEEP))
                            .collect(Collectors.toList());

                        WritableArray sleepSample = Arguments.createArray();
                        Format formatter = new SimpleDateFormat("EEE");

                        for (Session session : sleepSessions) {
                            WritableMap sleepData = Arguments.createMap();

                            String day = formatter.format(new Date(session.getStartTime(TimeUnit.MILLISECONDS)));
                            sleepData.putString("day", day);

                            sleepData.putString("addedBy", session.getAppPackageName());
                            sleepData.putString("startDate", dateFormat.format(session.getStartTime(TimeUnit.MILLISECONDS)));
                            sleepData.putString("endDate", dateFormat.format(session.getEndTime(TimeUnit.MILLISECONDS)));

                            // If the sleep session has finer granularity sub-components, extract them:
                            List<DataSet> dataSets = response.getDataSet(session);
                            WritableArray granularity = Arguments.createArray();
                            for (DataSet dataSet : dataSets) {
                                processDataSet(dataSet, granularity);
                            }
                            sleepData.putArray("granularity", granularity);

                            sleepSample.pushMap(sleepData);
                        }
                        successCallback.invoke(sleepSample);
                    }
                });
                // .addOnFailureListener(new OnFailureListener() {
                //     @Override
                //     public void onFailure(@NonNull Exception e) {
                //         promise.reject(e);
                //     }
                // });
    }

    private void processDataSet(DataSet dataSet, WritableArray granularity) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        dateFormat.setTimeZone(TimeZone.getDefault());
        

        for (DataPoint dp : dataSet.getDataPoints()) {
            WritableMap sleepStage = Arguments.createMap();
            sleepStage.putInt("sleepStage", dp.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt());
            sleepStage.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            sleepStage.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));

            granularity.pushMap(sleepStage);
        }
    }


    // private static void processDataSet(DataSet dataSet, WritableArray map) {
    //     //Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
    //     DateFormat dateFormat = getTimeInstance();
    //     DateFormat dateAndTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    //     float sleepHours = 0;
    //     WritableMap sleepMap = Arguments.createMap();

    //     for (DataPoint dp : dataSet.getDataPoints()) {
    //         Format formatter = new SimpleDateFormat("EEE");
    //         String day = formatter.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS)));

    //         for(Field field : dp.getDataType().getFields()) {
    //             if(dp.getOriginalDataSource().getAppPackageName() != null && dp.getOriginalDataSource().getAppPackageName().toString().contains("sleep") && field.getName().contains("duration")){
                    
    //                 Value value = dp.getValue(field);
    //                 sleepHours  = (float) (Math.round((value.asInt() * 2.778 * 0.0000001*10.0))/10.0);
                    
    //                 sleepMap.putString("day", day);
    //                 sleepMap.putDouble("startDate", dp.getStartTime(TimeUnit.MILLISECONDS));
    //                 sleepMap.putDouble("endDate", dp.getEndTime(TimeUnit.MILLISECONDS));
    //                 sleepMap.putDouble("sleep", sleepHours);
    //                 map.pushMap(sleepMap);
    //             }
    //         }
    //     }
    // }

}
