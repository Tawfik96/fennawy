package com.example.fennawy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import android.Manifest;
import java.net.Socket;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_IP = "192.168.1.106";  // Replace with your PC's IP
    private static final int SERVER_PORT = 5001;
    private Socket socket;
    private BufferedReader reader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Log.d("Socket", reader.toString());

            while (true) {
                String message = reader.readLine();
                Log.d("Socket", "Received: " + message);
                if (message != null) {
                    Log.d("Socket", "Received: " + message);
                    runOnUiThread(() -> handleServerMessage(message));
                }
            }
        } catch (Exception e) {
            Log.e("Socket", "Connection error: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private void handleServerMessage(String message) {
        Log.d("Socket", "Handling message: " + message);

        if (message.equalsIgnoreCase("capture")) {
            Log.d("Socket", "Received capture command");
//            Toast.makeText(this, "Taking Picture...", Toast.LENGTH_SHORT).show();
            checkCameraPermission();
            Log.d("Socket", "Camera opened");
        } else {
            Log.d("Socket", "Unknown command: " + message);
        }
    }


    public void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        } else {
            openCamera();
        }
    }

    // Handle the permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "Camera not supported", Toast.LENGTH_SHORT).show();
        }
    }
}
