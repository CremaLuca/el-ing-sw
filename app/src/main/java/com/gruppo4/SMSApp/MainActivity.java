package com.gruppo4.SMSApp;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Bundle;

import com.gruppo4.sms.SMSController;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Richiediamo il permesso di leggere e inviare i messaggi

        //requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},1);
        requestPermissions(new String[]{Manifest.permission.SEND_SMS},1);

        //richiama il metodo per l'invio del messaggio
        SMSController contr = new SMSController();
        contr.sendMessage("3457090735","ciao, questo è un sms di prova");


    }
}
