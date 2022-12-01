package fr.ubs.opproadinfo.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Set;
import java.util.UUID;

import fr.ubs.opproadinfo.MainActivity;

/**
 * Establish connection between the app and the Raspberry Pi
 */
public class Network extends Thread {

    private BluetoothSocket mmSocket; //Connexion et communication portable / raspberry
    private BluetoothDevice mmDevice = null; //Désigne l'appareil serveur (le raspberry)
    private final Handler handler = new Handler();

    private final UUID uuid;
    private final MainActivity mainActivity;

    /**
     * Instantiates a new Network.
     *
     * @param activity the activity
     */
    public Network(MainActivity activity) {
        mainActivity = activity;
        uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee");

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().startsWith("OppRoadInfo-")) {
                    mmDevice = device;
                    try {
                        connect();
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Listen to a message reception
     */
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {
            int bytesAvailable = 0;

            try {
                InputStream mmInputStream = null;

                if(mmSocket != null && mmSocket.isConnected()) {
                    mmInputStream = mmSocket.getInputStream();
                    bytesAvailable = mmInputStream.available();
                }

                if (bytesAvailable > 0) {

                    byte[] packetBytes = new byte[bytesAvailable];
                    mmInputStream.read(packetBytes);
                    final String data = new String(packetBytes);

                    handler.post(new Runnable() {
                        @RequiresApi(api = Build.VERSION_CODES.N)
                        public void run() {

                            try {
                                JSONObject message = new JSONObject(data);
                                mainActivity.processEvent(message);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Connect the Raspberry Pi to the phone
     */
    private void connect() throws IOException {
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
    }

    /**
     * Send a message to the Raspberry PI
     *
     * @param message to send
     */
    public void sendMessage(String message) {
        try {
            if(mmDevice != null){

                if (!mmSocket.isConnected()) connect();

                OutputStream mmOutputStream = mmSocket.getOutputStream();
                if (mmSocket.isConnected()) {
                    mmOutputStream.write(message.getBytes());
                }
            }
        } catch (IOException i) {
            i.printStackTrace();
        }
    }
}