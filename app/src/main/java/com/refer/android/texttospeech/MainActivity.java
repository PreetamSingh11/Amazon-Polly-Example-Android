package com.refer.android.texttospeech;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechPresignRequest;
import com.amazonaws.services.polly.model.Voice;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private EditText text;
    private Spinner spinnerVoices;
    private ArrayList<String> voice_id_list = new ArrayList<>();
    private String selected_voice;
    private String selected_language;

    private ProgressBar progressBar;
    private ImageView audioImage;
    private TextView audio_info;
    private ImageView downloadImage;
    private TextView download_info;

    private RelativeLayout netWorkStatus;
    private LinearLayout bottomButtonBar;
    private ImageView polly_logo;

    private AmazonPollyPresigningClient client;

    private long queueID;
    private URL audioFileUrl;
    DownloadManager downloadManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isInternetConnected();

        text = findViewById(R.id.edit_text);
        spinnerVoices = findViewById(R.id.spinner_voice_list);

        Button listen_btn = findViewById(R.id.listen);
        Button clear_btn = findViewById(R.id.btn_clear);
        Button download_btn = findViewById(R.id.btn_download);
        Button try_again = findViewById(R.id.btn_try_again);

        progressBar = findViewById(R.id.progress_bar);
        audioImage = findViewById(R.id.audio_sign);
        audio_info = findViewById(R.id.text_audio_info);
        downloadImage = findViewById(R.id.download_sign);
        download_info = findViewById(R.id.text_download_info);
        netWorkStatus = findViewById(R.id.network_status);
        bottomButtonBar = findViewById(R.id.bottom_btn_bar);
        polly_logo = findViewById(R.id.image_polly);

        progressBar.setVisibility(View.GONE);
        audioImage.setVisibility(View.GONE);
        audio_info.setVisibility(View.GONE);
        downloadImage.setVisibility(View.GONE);
        download_info.setVisibility(View.GONE);
        netWorkStatus.setVisibility(View.GONE);

        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "ap-south-1:xxxxxxxxx-xxxxx-x-xxxx",
                Regions.AP_SOUTH_1
        );

        client = new AmazonPollyPresigningClient(credentialsProvider);

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(queueID);
                    Cursor cursor = downloadManager.query(query);
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                            Toast.makeText(
                                    getApplicationContext(),
                                    "Download Successful",
                                    Toast.LENGTH_LONG
                            ).show();
                            downloadImage.setVisibility(View.GONE);
                            download_info.setVisibility(View.GONE);
                        }
                    }
                }
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        if (isInternetConnected()){
            loadVoices();
            networkVisible();
        } else {
            noNetworkVisible();
        }

        try_again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isInternetConnected()){
                    loadVoices();
                    networkVisible();
                } else {
                    noNetworkVisible();
                }
            }
        });

        download_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioFileUrl != null) {
                    hideKeyboard();
                    downloadFile(audioFileUrl);
                }
            }
        });

        clear_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                text.getText().clear();
            }
        });

        listen_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                textToSpeech();
            }
        });
    }

    private void noNetworkVisible(){
        netWorkStatus.setVisibility(View.VISIBLE);
        spinnerVoices.setVisibility(View.GONE);
        text.setVisibility(View.GONE);
        bottomButtonBar.setVisibility(View.GONE);
        polly_logo.setVisibility(View.GONE);
    }
    private void networkVisible(){
        netWorkStatus.setVisibility(View.GONE);
        spinnerVoices.setVisibility(View.VISIBLE);
        text.setVisibility(View.VISIBLE);
        bottomButtonBar.setVisibility(View.VISIBLE);
        polly_logo.setVisibility(View.VISIBLE);
    }

    private void loadVoices() {
        GetVoices getVoices = new GetVoices(client);
        try {
            final List<Voice> voice_list = getVoices.execute().get();
            for (Voice voice : voice_list) {
                voice_id_list.add(voice.getId());
            }

            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, voice_id_list);
            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerVoices.setPrompt("Select Voice");
            spinnerVoices.setAdapter(spinnerArrayAdapter);
            spinnerVoices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selected_voice = voice_id_list.get(position);
                    selected_language = voice_list.get(position).getLanguageName();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    Toast.makeText(getApplicationContext(), "Nothing is selected ", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void textToSpeech() {
        progressBar.setVisibility(View.VISIBLE);
        URL url = null;
        try {
            url = new PlayAudioClass(client).execute(text.getText().toString(), selected_voice).get();
            audioFileUrl = url;
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            if (url != null)
                mediaPlayer.setDataSource(url.toString());
        } catch (IOException e) {
            Log.d("Main", "Unable to set data source for media player" + e.getMessage());
        }

        String audioInfo = " is speaking " + selected_language;
        audio_info.setText(audioInfo);

        mediaPlayer.prepareAsync();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                progressBar.setVisibility(View.GONE);
                audioImage.setVisibility(View.VISIBLE);
                audio_info.setVisibility(View.VISIBLE);
                mediaPlayer.start();
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                audioImage.setVisibility(View.GONE);
                audio_info.setVisibility(View.GONE);
                mediaPlayer.stop();
                Log.d("Main", "Text To Speech Translation complete");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadFile(audioFileUrl);
            } else {
                Toast.makeText(getApplicationContext(),
                        "No permissions",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void downloadFile(URL url) {
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        try {
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            } else {
                String download_text = "Downloading ...";
                download_info.setText(download_text);
                downloadImage.setVisibility(View.VISIBLE);
                download_info.setVisibility(View.VISIBLE);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url.toURI().toString()));
                request.setTitle(text.getText().toString().substring(0,10)+"...");
                request.setDescription(audio_info.getText());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                }
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, text.getText().toString().substring(0,10)+" by "+ selected_voice+".mp3");
                queueID = downloadManager.enqueue(request);
                Log.d("Main", "queueID " + queueID);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private boolean isInternetConnected(){
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void hideKeyboard(){
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(Objects.requireNonNull(getCurrentFocus()).getWindowToken(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class PlayAudioClass extends AsyncTask<String, Integer, URL> {

        private AmazonPollyPresigningClient mClient;

        PlayAudioClass(AmazonPollyPresigningClient client) {
            this.mClient = client;
        }

        @Override
        protected URL doInBackground(String... strings) {
            SynthesizeSpeechPresignRequest synthesizeSpeechPresignRequest = new SynthesizeSpeechPresignRequest()
                    .withText(strings[0])
                    .withVoiceId(strings[1])
                    .withOutputFormat(OutputFormat.Mp3);

            return mClient.getPresignedSynthesizeSpeechUrl(synthesizeSpeechPresignRequest);
        }
    }
}
