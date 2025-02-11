package jufo.vincent.de.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
@SuppressLint("StaticFieldLeak")
public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private static BluetoothDevice mDevice;
    private final static String MY_UUID = "00001101-0000-1000-8000-00805f9b34fb";
    private static BluetoothSocket mSocket = null;
    private static PrintWriter sender;

    static ArrayList<String> arrayList;
    static ArrayAdapter<String> arrayAdapter;
    String[]  items = {};

    EditText editText;
    ListView listView;
    Button addBtn;
    FloatingActionButton fab;

    static TextToSpeech tts;
    static boolean ttsReady = false;

    long lastClick;

    SharedPreferences list_pref;
    SharedPreferences.Editor list_editor;

    static SharedPreferences settings_pref;

    static NotificationManager Manager;
    static NotificationCompat.Builder Builder;

    static Context context;

    static String emergencyNumber;
    static String EmergencyMessage;


    @Override


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Einkaufsliste");

        //Gets old List
        list_pref = getSharedPreferences("Einkaufsliste", 0);
        list_editor = list_pref.edit();
        list_editor.apply();



        context = this;

        //Creates TextToSpeech
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if(i == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.GERMAN);
                    ttsReady = true;
                }
            }
        });

        //Creates NotificationBuilder
        Builder = new NotificationCompat.Builder(context);
        Manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Builder.setPriority(Notification.PRIORITY_MAX);
        Builder.setSmallIcon(R.drawable.icon);

        //Gets the Layout-Items
        listView = findViewById(R.id.list);
        editText = findViewById(R.id.topText);
        addBtn = findViewById(R.id.add_btn);
        fab = findViewById(R.id.fab);

        //Creates an Interface to the ListView
        arrayList = new ArrayList<>(Arrays.asList(items));
        arrayAdapter = new ArrayAdapter<>(this, R.layout.list_item, R.id.txtitem, arrayList);
        listView.setAdapter(arrayAdapter);

        //Sets OnClickListeners for "addBtn", "fab" and "listView"
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String newItem = editText.getText().toString();
                if (!newItem.trim().toLowerCase().equals("") && !newItem.isEmpty()){
                    if (!arrayList.contains(newItem) && !arrayList.contains(newItem.toLowerCase())) {
                        arrayList.add(0, newItem);
                        arrayAdapter.notifyDataSetChanged();
                        notificate();
                        editText.setText("");
                    }
                }
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (arrayList.size() != 0){
                    StringBuilder textToSay = new StringBuilder("Sie brauchen noch: ");
                    for(int i = 0; i < arrayList.size(); i++) {
                        textToSay.append(arrayAdapter.getItem(i)).append(", ");
                        if (i == arrayList.size() - 2) {
                            textToSay.append(" und ");
                        }
                    }
                    speak(textToSay.toString());
                } else {
                    speak("Sie haben alles gekauft, was sie brauchen!");
                }
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String selectedItem = adapterView.getItemAtPosition(i).toString();
                if(lastClick < System.currentTimeMillis() - 1000) {
                    speak(selectedItem);
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                arrayList.remove(i);
                arrayAdapter.notifyDataSetChanged();
                lastClick = System.currentTimeMillis();
                notificate();
                return false;
            }
        });

        //Adds ListItems from SharedPreferences to List
        int len = list_pref.getInt("length", 1);
        for (int i = 0; i < len; i++) {
            arrayList.add(0, list_pref.getString(String.valueOf(i), "Error"));
        }
        Collections.reverse(arrayList);

        //Creates Notification with Items from List
        notificate();

        //Creates Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Enables Bluetooth and connect to Raspberry Pi
        Toast.makeText(context, "Verbinde zu Reaspberry Pi..", Toast.LENGTH_LONG).show();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            mBluetoothAdapter.enable();
        }

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        findEV3();

        //Checks if the Devices is connected
        if (mDevice == null) {
            Toast.makeText(this, "EV3 nicht gefunden!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Verbunden.", Toast.LENGTH_SHORT).show();
        }

        try {
            createSocket();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Makes Tab-Selection invisible
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setTabTextColors(getResources().getColor(R.color.white), getResources().getColor(R.color.white));
        tabs.setSelectedTabIndicatorColor(getResources().getColor(R.color.colorPrimary));

        //Sends Messages to Raspberry Pi when Tab is pressed
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                try {
                    switch (tab.getPosition()) {
                        case 0:
                            onSend("Stop");
                            Toast.makeText(context, "Roboter wird gestoppt...", Toast.LENGTH_LONG).show();
                            break;
                        case 1:
                            onSend("Go");
                            Toast.makeText(context, "Roboter fährt weiter...", Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                try {
                    switch (tab.getPosition()) {
                        case 0:
                            onSend("Stop");
                            Toast.makeText(context, "Roboter wird gestoppt...", Toast.LENGTH_LONG).show();
                            break;
                        case 1:
                            onSend("Go");
                            Toast.makeText(context, "Roboter fährt weiter...", Toast.LENGTH_SHORT).show();
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
        });

        //Reads the List when FloatingActionButton is pressed
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            readList();
            }
        });

        // Starts the camera
        FloatingActionButton fab2 = findViewById(R.id.fab2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(context, CameraActivity.class);
                startActivity(i);
            }
        });

        //Starts the SpeechRecognizerService
        startService();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent i = new Intent(getApplication(), SettingsActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //Creates a Notification with List of Items to buy
    private void notificate() {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle("Einkaufsliste");
        for(int i = 0; i < arrayList.size(); i++) {
            inboxStyle.addLine(String.valueOf(i + 1) + ". " + arrayAdapter.getItem(i));
        }
        Builder.setStyle(inboxStyle);


        if(arrayList.size() != 0) {
            Manager.notify(1, Builder.build());
        } else {
            Manager.cancel(1);
        }

    }

    //Tells TextToSpeech to read the List
    static void readList() {
        if (arrayList.size() != 0){
            StringBuilder textToSay = new StringBuilder("Sie brauchen noch: ");
            for(int i = 0; i < arrayList.size(); i++) {
                textToSay.append(arrayAdapter.getItem(i)).append(", ");
                if (i == arrayList.size() - 2) {
                    textToSay.append(" und ");
                }
            }
            speak(textToSay.toString());
        } else {
            speak("Sie haben alles gekauft, was sie brauchen!");
        }
    }

    //Tells TextToSpeech to read a word
    private static void speak(String text) {
        if(ttsReady) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);

        } else {
            Toast.makeText(Builder.mContext, "TTS not ready", Toast.LENGTH_SHORT).show();
        }
    }

    //Adds an Item to List
    public static void addItem(String item) {
        if (!item.trim().toLowerCase().equals("") && !item.isEmpty()){
            if (!arrayList.contains(item) && !arrayList.contains(item.toLowerCase())) {
                arrayList.add(0, item);
                arrayAdapter.notifyDataSetChanged();
                Toast.makeText(Builder.mContext, item + " hinzugefügt.", Toast.LENGTH_SHORT).show();
            }
        }

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle("Einkaufsliste");
        for(int i = 0; i < arrayList.size(); i++) {
            inboxStyle.addLine(String.valueOf(i + 1) + ". " + arrayAdapter.getItem(i));
        }
        Builder.setStyle(inboxStyle);

        if(arrayList.size() != 0) {
            Manager.notify(1, Builder.build());
        } else {
            Manager.cancel(1);
        }
    }

    //Removes an Item from List
    public static void removeItem(String item) {
        if(arrayList.contains(item) || arrayList.contains(item.toLowerCase())) {
            int index = arrayList.indexOf(item);
            arrayList.remove(index);
            arrayAdapter.notifyDataSetChanged();
            Toast.makeText(Builder.mContext, item + " entfernt.", Toast.LENGTH_SHORT).show();
        }

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle("Einkaufsliste");
        for(int i = 0; i < arrayList.size(); i++) {
            inboxStyle.addLine(String.valueOf(i + 1) + ". " + arrayAdapter.getItem(i));
        }
        Builder.setStyle(inboxStyle);

        if(arrayList.size() != 0) {
            Manager.notify(1, Builder.build());
        } else {
            Manager.cancel(1);
        }
    }

    //Removes all Items from List
    public static void removeAll() {
        arrayList.clear();
        arrayAdapter.notifyDataSetChanged();
        Toast.makeText(Builder.mContext, "Liste geleert", Toast.LENGTH_SHORT).show();
        Manager.cancel(1);

    }

    //Saves all Items from List to SharedPreferences
    public void saveItems() {
        for(int i = 0; i < arrayList.size(); i++) {
            list_editor.putString(String.valueOf(i), arrayAdapter.getItem(i));
        }
        list_editor.putInt("length", arrayList.size());
        list_editor.apply();
        list_editor.commit();
        notificate();
    }

    //Finds the EV3 with the name "jufo" in the List of available Bluetooth-Devices
    private void findEV3() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equals("jufo"))
                mDevice = device;
        }
    }

    //Starts the SpeechRecognizerService
    private void startService() {
        Intent i = new Intent(this, BackgroundRecognizerService.class);
        startService(i);
    }

    //Sends a Message to the EV3
    public static void onSend(String message) throws IOException {
        try {
            message = message.toLowerCase();
            sender.println(message);
            sender.println(message);
            sender.flush();
            sender.flush();
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    //Creates a Bluetooth Socket to send Messages to the EV3
    public static void createSocket() throws IOException {
        try {
            UUID uuid = UUID.fromString(MY_UUID);
            if (mDevice != null) {
                mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mSocket != null) {
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            mSocket.connect();
            OutputStream os = mSocket.getOutputStream();
            sender = new PrintWriter(new OutputStreamWriter(os), true);
        }

    }

    //Sends an SMS with a predefined Text to a predefined Number
    public static void sendEmergencySMS() {
        //Gets the Settings and assigns it to local variables
        settings_pref = context.getSharedPreferences("Einstellungen", 0);
        emergencyNumber = settings_pref.getString("Number", "");
        EmergencyMessage = settings_pref.getString("Message", "");
        Tracker tracker = new Tracker(context);
        double lat = tracker.getLatidude();
        double lon = tracker.getLongitude();
        if (lat != 0 || lon != 0) {
            EmergencyMessage += "\n Position: Lat.: " + lat + " Lon.: " + lon;
        } else {
            Toast.makeText(context, "GPS aktiviert?", Toast.LENGTH_SHORT).show();
        }
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(emergencyNumber, null, EmergencyMessage, null, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mSocket != null) { mSocket.close(); }
            if (sender != null) { sender.close(); }
        } catch (IOException e) { e.printStackTrace(); }
        saveItems();
        Manager.cancel(1);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        saveItems();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        saveItems();
    }
}