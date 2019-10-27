/**
 * Copyright (c) 2017-present, Stanislav Doskalenko - doskalenko.s@gmail.com
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 **/

package com.reactnative.googlefit;

import android.os.AsyncTask;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.data.HealthDataTypes;
import com.google.android.gms.fitness.data.HealthFields;

import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static java.text.DateFormat.getTimeInstance;


public class HeartrateHistory {

    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;
    private DataSet Dataset;
    private DataType dataType;

    private static final String TAG = "HeartRateHistory";

    public HeartrateHistory(ReactContext reactContext, GoogleFitManager googleFitManager){
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
    }


    public void aggregateDataByDate(long startTime, long endTime, final Callback successCallback) {

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        dateFormat.setTimeZone(TimeZone.getDefault());
        final WritableArray results = Arguments.createArray();
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));
        Log.i(TAG, "Result for Heart Rate:");

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_RATE_BPM, DataType.AGGREGATE_HEART_RATE_SUMMARY)
                .bucketByTime(1, TimeUnit.DAYS)
                .enableServerQueries()
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        DataReadResult dataReadResult =
                Fitness.HistoryApi.readData(this.googleFitManager.getGoogleApiClient(), readRequest).await(1, TimeUnit.MINUTES);


        WritableArray heartRates = Arguments.createArray();

        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    processDataSet(dataSet, heartRates);
                }
            }
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "Number of returned DataSets is: "
                    + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                processDataSet(dataSet, heartRates);
            }
        }

        WritableMap map = Arguments.createMap();
        map.putArray("heartRates", heartRates);
        results.pushMap(map);

        successCallback.invoke(results);
    }


    private static void processDataSet(DataSet dataSet, WritableArray map) {
        //Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        DateFormat dateFormat = getTimeInstance();
        DateFormat dateAndTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");



        for (DataPoint dp : dataSet.getDataPoints()) {
            Format formatter = new SimpleDateFormat("EEE");
            String day = formatter.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS)));
            WritableMap heartRateMap = Arguments.createMap();
            for(Field field : dp.getDataType().getFields()) {
                Log.i(TAG, "App Package Name:" + dp.getDataSource().getAppPackageName().toString());
                Log.i(TAG, "Data point:");
                Log.i(TAG, "\tType: " + dp.getDataType().getName());
                Log.i(TAG, "\tDate: " +  dateAndTimeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " to " + dateAndTimeFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                Log.i(TAG, "\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));

                heartRateMap.putString("day", day);
                heartRateMap.putDouble("startDate", dp.getStartTime(TimeUnit.MILLISECONDS));
                heartRateMap.putDouble("endDate", dp.getEndTime(TimeUnit.MILLISECONDS));
                if (field.getName().toString().equals("average")) {
                    heartRateMap.putDouble("average", dp.getValue(field).asFloat());
                } else if (field.getName().toString().equals("max")) {
                    heartRateMap.putDouble("max", dp.getValue(field).asFloat());
                } else if (field.getName().toString().equals("min")) {
                    heartRateMap.putDouble("min", dp.getValue(field).asFloat());
                }

            }
            map.pushMap(heartRateMap);

        }
    }

}
