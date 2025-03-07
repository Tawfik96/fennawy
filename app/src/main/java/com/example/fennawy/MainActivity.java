package com.example.fennawy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.content.Context;
import android.net.Uri;
import java.io.OutputStream;
public class MainActivity extends AppCompatActivity {

    private static final String SERVER_IP = "192.168.1.106";  // Replace with your PC's IP
    private static final int SERVER_PORT = 5001;
    private Socket socket;
    private BufferedReader reader;

    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;

    private String cameraId;
    private CameraManager cameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        checkPermissions();
        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (true) {
                String message = reader.readLine();
                if (message != null) {
                    Log.d("Socket", "Received: " + message);
                    runOnUiThread(() -> handleServerMessage(message));
                }
            }
        } catch (Exception e) {
            Log.e("Socket", "Connection error: " + e.getMessage(), e);
        }
    }

    private void handleServerMessage(String message) {
        if (message.equalsIgnoreCase("capture")) {
            Log.d("Socket", "Received capture command");
            openCamera();
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera and storage permissions required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
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
                    Log.d("Camera2", "Picture Taken Automatically!");
                }
            }, null);
        } catch (Exception e) {
            Log.e("Camera2", "Error taking picture: " + e.getMessage());
        }
    }



    private void saveImage(Image image) {
        File privateFile = new File(getExternalFilesDir(null), "captured_image.jpg");

        try (FileOutputStream outputStream = new FileOutputStream(privateFile)) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            outputStream.write(bytes);
            Log.d("Camera2", "Image saved: " + privateFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("Camera2", "Error saving image: " + e.getMessage());
        } finally {
            image.close();
        }

        // Save to gallery using MediaStore
        saveImageToGallery(privateFile);
    }

    private void saveImageToGallery(File privateFile) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "captured_image.jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Fennawy");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri);
                 FileInputStream in = new FileInputStream(privateFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                Log.d("Camera2", "Image successfully saved to gallery!");
            } catch (IOException e) {
                Log.e("Camera2", "Error saving to gallery: " + e.getMessage());
            }
        } else {
            Log.e("Camera2", "Failed to create media store entry");
        }
    }


}
