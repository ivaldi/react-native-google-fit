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
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
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

    public void aggregateDataByDate(long startTime, long endTime, final Callback successCallback) {

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        dateFormat.setTimeZone(TimeZone.getDefault());
        final WritableArray results = Arguments.createArray();
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));
        Log.i(TAG, "Result for sleep:");
        DataReadRequest readRequest = new DataReadRequest.Builder()
                // The data request can specify multiple data types to return, effectively
                // combining multiple data queries into one call.
                // In this example, it's very unlikely that the request is for several hundred
                // datapoints each consisting of a few steps and a timestamp.  The more likely
                // scenario is wanting to see how many steps were walked per day, for 7 days.
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                // bucketByTime allows for a time span, whereas bucketBySession would allow
                // bucketing by "sessions", which would need to be defined in code.
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        // [START read_dataset]
        // Invoke the History API to fetch the data with the query and await the result of
        // the read request.
        DataReadResult dataReadResult =
                Fitness.HistoryApi.readData(this.googleFitManager.getGoogleApiClient(), readRequest).await(1, TimeUnit.MINUTES);
        // [END read_dataset]


        WritableArray sleeps = Arguments.createArray();

        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    processDataSet(dataSet, sleeps);
                }
            }
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "Number of returned DataSets is: "
                    + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                processDataSet(dataSet, sleeps);
            }
        }

        WritableMap map = Arguments.createMap();
        map.putArray("sleeps", sleeps);
        results.pushMap(map);

        successCallback.invoke(results);
    }


    private static void processDataSet(DataSet dataSet, WritableArray map) {
        //Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        DateFormat dateFormat = getTimeInstance();
        float sleepHours = 0;
        WritableMap sleepMap = Arguments.createMap();

        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.i(TAG, "App Package Name:" + dp.getOriginalDataSource().getAppPackageName().toString());
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));

            Format formatter = new SimpleDateFormat("EEE");
            String day = formatter.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS)));

            for(Field field : dp.getDataType().getFields()) {
                if(dp.getOriginalDataSource().getAppPackageName().toString().contains("sleep") && field.getName().contains("duration")){
                    Value value = dp.getValue(field);
                    sleepHours  = (float) (Math.round((value.asInt() * 2.778 * 0.0000001*10.0))/10.0);
                    Log.i(TAG, "\tField: Sleep duration in h " + sleepHours);
                    sleepMap.putString("day", day);
                    sleepMap.putDouble("startDate", dp.getStartTime(TimeUnit.MILLISECONDS));
                    sleepMap.putDouble("endDate", dp.getEndTime(TimeUnit.MILLISECONDS));
                    sleepMap.putDouble("sleep", sleepHours);
                    map.pushMap(sleepMap);
                }
                Log.i(TAG, "\t\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));

            }
        }
    }

}
