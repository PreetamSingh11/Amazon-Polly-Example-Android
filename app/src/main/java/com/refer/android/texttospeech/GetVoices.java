package com.refer.android.texttospeech;

import android.os.AsyncTask;

import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.DescribeVoicesRequest;
import com.amazonaws.services.polly.model.DescribeVoicesResult;
import com.amazonaws.services.polly.model.Voice;

import java.util.List;

public class GetVoices extends AsyncTask<Void,Integer,List<Voice>> {

    private AmazonPollyPresigningClient mClient;

    GetVoices(AmazonPollyPresigningClient client) {
        this.mClient = client;
    }

    @Override
    protected List<Voice> doInBackground(Void... voids) {
        DescribeVoicesRequest describeVoicesRequest = new DescribeVoicesRequest();
        DescribeVoicesResult describeVoicesResult = mClient.describeVoices(describeVoicesRequest);
        return describeVoicesResult.getVoices();
    }
}
