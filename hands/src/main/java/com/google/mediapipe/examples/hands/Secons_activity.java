package com.google.mediapipe.examples.hands;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.VideoView;

public class Secons_activity extends AppCompatActivity {
    private static final String TAG = "SeconsActivity";
    private EditText editText;
    private Button button;
    private Button button2;
    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secons);

        editText = (EditText) findViewById(R.id.editTextTextPersonName);
        button = (Button) findViewById(R.id.button);
        button2 = (Button) findViewById(R.id.button2);
        videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setVisibility(View.GONE);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = editText.getText().toString();
                if (!(text.equals(""))) {
                    text = text.substring(0, 1).toLowerCase() + text.substring(1);
                    videoView.setVisibility(View.VISIBLE);
                    String[] words = text.split(" ");
                    for (String word : words) {

                        switch (word) {
                            case "ты":
                            case "тебя":
                            case "тобой":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.you));
                                videoView.start();
                                break;
                            case "привет":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.hello));
                                videoView.start();
                                break;
                            case "до":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.bye));
                                videoView.start();
                                break;
                            case "здравствуйте":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.hi));
                                videoView.start();
                                break;
                            case "любить":
                            case "любишь":
                            case "любите":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.love));
                                videoView.start();
                                break;
                            case "зовут":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.name));
                                videoView.start();
                                break;
                            case "где":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.where));
                                videoView.start();
                                break;
                            case "как":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.how));
                                videoView.start();
                                break;
                            case "познакомиться":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.meet));
                                videoView.start();
                                break;
                            case "можно":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.may));
                                videoView.start();
                                break;
                            case "дела":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.whatsup));
                                videoView.start();
                                break;
                            case "я":
                            case "меня":
                            case "мне":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.i));
                                videoView.start();
                                break;
                            case "школе":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.school));
                                videoView.start();
                                break;
                            case "учиться":
                            case "учитесь":
                            case "учишься":
                                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.study));
                                videoView.start();
                                break;
                        }
                    }
                }
            }
        });
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StartActivity(view);
            }
        });
    }

    public void StartActivity(View v) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}