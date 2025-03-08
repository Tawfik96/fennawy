package com.example.fennawy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_IP = "192.168.1.106";  // Replace with your PC's IP
    private static final int SERVER_PORT = 5001;
    private Socket socket;
    private BufferedReader reader;

    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private TextView socketStatus;  // Declare TextView
    private String cameraId;
    private CameraManager cameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);// to keep the screen on when the app is running
        textureView = findViewById(R.id.textureView);
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        socketStatus = findViewById(R.id.socketStatus);  // Initialize TextView

        checkPermissions();
        new Thread(this::connectToServer).start();
    }


    private void connectToServer() {
        while (true) { // Keep retrying until connected
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                runOnUiThread(() -> updateSocketStatus("Connected", Color.GREEN));  // Update UI

                Log.d("Socket", "Connected to server!");

                while (true) {
                    try {
                        String message = reader.readLine();
                        if (message == null) {  // Server disconnected
                            Log.e("Socket", "Server disconnected.");
                            runOnUiThread(() -> updateSocketStatus("Disconnected", Color.RED));
                            break;  // Exit the loop
                        }

                        Log.d("Socket", "Received: " + message);
                        runOnUiThread(() -> handleServerMessage(message));

                    } catch (IOException e) {
                        Log.e("Socket", "Error reading from server: " + e.getMessage(), e);
                        runOnUiThread(() -> updateSocketStatus("Disconnected", Color.RED));
                        break;  // Exit loop if an error occurs
                    }
                }
            } catch (Exception e) {
                Log.e("Socket", "Connection error: " + e.getMessage(), e);
                runOnUiThread(() -> updateSocketStatus("Disconnected", Color.RED));  // Update UI
            }

            // Wait and retry connection if it fails
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}

            Log.d("Socket", "Retrying connection...");
        }
    }

    private void updateSocketStatus(String status, int color) {
        socketStatus.setText(status);
        socketStatus.setTextColor(color);
    }

    private void handleServerMessage(String message) {
        if (message.equalsIgnoreCase("capture")) {
            Log.d("Socket", "Received capture command");
            openCamera();
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            closeCamera(); // I added this to prevent cache leak
            cameraId = cameraManager.getCameraIdList()[0];  // Use rear camera
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            imageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(reader -> saveImage(reader.acquireLatestImage()), null);

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, null);
        } catch (Exception e) {
            Log.e("Camera2", "Error opening camera: " + e.getMessage());
        }
    }

    private void startPreview() {
        try {
            Surface textureSurface = new Surface(textureView.getSurfaceTexture());
            Surface imageSurface = imageReader.getSurface();

            cameraDevice.createCaptureSession(Arrays.asList(textureSurface, imageSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    takePicture();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("Camera2", "Configuration failed");
                }
            }, null);
        } catch (Exception e) {
            Log.e("Camera2", "Error starting preview: " + e.getMessage());
        }
    }

    private void takePicture() {
        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Log.d("Socket-Camera", "Picture Taken Automatically!");
                }
            }, null);
        } catch (Exception e) {
            Log.e("Socket-Camera", "Error taking picture: " + e.getMessage());
        }
    }

    private void sendImageToServer(byte[] imageBytes) {
        if (socket == null || socket.isClosed()) {
            Log.e("Socket", "Not connected to server.");
            return;
        }

        try {
            Log.d("Socket", "Sending image to server...");
            OutputStream outputStream = socket.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

            // Send image size first
            dataOutputStream.writeInt(imageBytes.length);
            dataOutputStream.flush();

            // Send image data
            outputStream.write(imageBytes);
            outputStream.flush();

            Log.d("Socket", "Image sent successfully");

        } catch (IOException e) {
            Log.e("Socket", "Error sending image: " + e.getMessage());
        }
    }


    private void saveImage(Image image) {
        if (image == null) {
            Log.e("Socket", "Image is null, skipping save operation.");
            return;
        }
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        // Save to gallery
        saveImageToGallery(bytes);
        new Thread(() -> sendImageToServer(bytes)).start();

        image.close();

    }

    private void saveImageToGallery(byte[] imageData) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "captured_image_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Fennawy");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                outputStream.write(imageData);
                outputStream.flush();
                Log.d("Camera2", "Image saved to gallery: " + uri.toString());
            } catch (IOException e) {
                Log.e("Camera2", "Failed to save image: " + e.getMessage());
            }
        } else {
            Log.e("Camera2", "Failed to create MediaStore entry");
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
