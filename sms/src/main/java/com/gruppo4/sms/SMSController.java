package com.gruppo4.sms;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.gruppo4.sms.broadcastReceivers.SMSSentBroadcastReceiver;
import com.gruppo4.sms.listeners.SMSReceivedListener;
import com.gruppo4.sms.listeners.SMSSentListener;

import java.util.ArrayList;

public class SMSController {

    public enum SentState {
        MESSAGE_SENT,
        ERROR_GENERIC_FAILURE,
        ERROR_RADIO_OFF,
        ERROR_NULL_PDU,
        ERROR_NO_SERVICE,
        ERROR_LIMIT_EXCEEDED
    }


    private static SMSController instance; //singleton
    private Context context;
    private int appCode;
    private int next_id; //id chosen for the next message to send

    //list of listeners called when ALL packets of a SMS have been received
    private ArrayList<SMSReceivedListener> receivedListeners;
    private ArrayList<SMSMessage> incompleteMessages; //these are partially constructed messages
    //each list has packets with the same messageID

    private SMSController(Context context, int applicationCode)
    {
        this.context = context;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_DENIED)
            throw new SecurityException("Missing Manifest.permission.SEND_SMS permission, use requestPermissions() to be granted this permission runtime");

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_DENIED)
            throw new SecurityException("Missing Manifest.permission.RECEIVE_SMS permission, use requestPermissions() to be granted this permission runtime");

        receivedListeners = new ArrayList<>();
        this.appCode = applicationCode;
        next_id = 0;
        incompleteMessages = new ArrayList<>();
    }

    public static void init(Context context, int applicationCode){

        instance = new SMSController(context, applicationCode);
    }

    public static SMSController getInstance()
    {
        if(instance == null)
            throw new IllegalStateException("The SMSController must be initialized!");
        else return instance;
    }

    /**
     * Send a SMSMessage, multiple packets could be sent
     *
     * @param message the message to be sent via SMS
     * @param listener called when the message is completely sent to the provider
     */
    public static void sendMessage(SMSMessage message, SMSSentListener listener)
    {
        PendingIntent sentPI = PendingIntent.getBroadcast(getInstance().context, 0, new Intent("SMS_SENT"), 0);
        BroadcastReceiver receiver = new SMSSentBroadcastReceiver(listener);
        //Set the new BroadcastReceiver to intercept intents with the right filter
        getInstance().context.registerReceiver(receiver, new IntentFilter("SMS_SENT"));
        //Retrieve the Android default smsManager
        SmsManager smsManager = SmsManager.getDefault();

        ArrayList<String> messages = new ArrayList<>();
        ArrayList<PendingIntent> onSentIntents = new ArrayList<>();

        for (SMSPacket packet : message.getPackets()) {
            messages.add(packet.getText()); //we include the header, i.e. packetNumber, applicationCode, ...
            onSentIntents.add(null); //we set all but the last listener to null
        }
        //we call the listener when all the packets have been sent, so last listener is not null
        onSentIntents.set(onSentIntents.size() - 1, sentPI);
        smsManager.sendMultipartTextMessage(message.getTelephoneNumber(), null, messages, onSentIntents, null);
    }

    /**
     * Subscribes the listener to be called once a message with the right code is received
     *
     * @param listener    a class that implements SMSReceivedListener
     */
    public static void addOnReceiveListener(SMSReceivedListener listener)
    {
        getInstance().receivedListeners.add(listener);
    }

    /**
     * Returns the identifier of this application, used to avoid interfering with other application's messages
     * @return the application code
     */
    public static int getApplicationCode() {return getInstance().appCode; }

    /**
     * Method used by OnSMSReceived to send a packet
     *
     * @param packet the sms content wrapped in a packet
     */
    public static void onReceive(SMSPacket packet, String telephoneNumber) {
        //Let's see if we already have the message stored
        boolean found = false;
        for (SMSMessage message : getInstance().incompleteMessages) {
            if (message.getTelephoneNumber().equals(telephoneNumber) && message.getMessageId() == packet.getMessageId()) {
                found = true;
                message.addPacket(packet);
                break;
            }
        }
        //If not found then create a list for all incoming packets with that id
        if (!found) {
            getInstance().incompleteMessages.add(new SMSMessage(telephoneNumber, packet));
        }
    }

    public static void callOnReceivedListeners(SMSMessage message) {
        for (SMSReceivedListener listener : getInstance().receivedListeners) {
            listener.onSMSReceived(message);
        }
    }

    private int getNewMessageId(){
        next_id = (next_id+1)%(SMSMessage.MAX_PACKETS+1);
        return next_id; //we send messages with id not greater than 999;
    }

    public static SMSPacket[] getPacketsFromMessage(SMSMessage message){
        String msgText = message.getMessage();
        int rem = msgText.length() % SMSPacket.MAX_PACKET_TEXT_LEN;
        int packetsCount = msgText.length() / SMSPacket.MAX_PACKET_TEXT_LEN + (rem != 0 ? 1:0);
        Log.d("SMSMessage", "I have to send " + packetsCount + " messages for a message " + msgText.length() + " characters long");
        SMSPacket[] packets = new SMSPacket[packetsCount];
        int msgId = getInstance().getNewMessageId();
        String subText;
        for (int i = 0; i < packetsCount; i++) {
            int finalCharacter = Math.min((i + 1) * SMSPacket.MAX_PACKET_TEXT_LEN, msgText.length());
            Log.d("SMSMessage", "Substring from " + i *  SMSPacket.MAX_PACKET_TEXT_LEN + " to " + finalCharacter);
            subText =msgText.substring(i * SMSPacket.MAX_PACKET_TEXT_LEN, finalCharacter);
            packets[i] = new SMSPacket(getApplicationCode(), msgId, i + 1, packetsCount, subText);
        }
        return packets;
    }
}
