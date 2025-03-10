package com.example.fennawy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
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

//    private static final String SERVER_IP = "192.168.1.106";  // My Home IP

    //My hotspot is 192.168.167.176
    private static final String SERVER_IP = "192.168.74.208";  // Moaz

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
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        textureView = findViewById(R.id.textureView);

        // Add a surface texture listener to know when the view is ready
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Surface is ready, open camera
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // Handle size change if needed
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // This is called every time the texture is updated with a new frame
            }
        });

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        socketStatus = findViewById(R.id.socketStatus);

        checkPermissions();

        // Don't open camera here anymore, it will be opened when surface is available
        // openCamera();

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
            // Take a picture without closing/reopening the camera
            if (captureSession != null && cameraDevice != null) {
                takePictureWithFocusSequence();
            } else {
                // If camera is not ready, we need to open it
                openCamera();
            }
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

    private Size getOptimalSize() throws CameraAccessException {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return null;
        }
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        Size largest = sizes[0];
        for (Size size : sizes) {
            if (size.getWidth() * size.getHeight() > largest.getWidth() * largest.getHeight()) {
                largest = size;
            }
        }
        return largest;
    }
    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            closeCamera(); // I added this to prevent cache leak
            cameraId = cameraManager.getCameraIdList()[0];  // Use rear camera
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Size optimalSize = getOptimalSize();
            if (optimalSize == null) {                                  // added to avoid nullpointer errors ylaa
                Log.e("Camera2", "Optimal size not found");
                return;
            }
            imageReader = ImageReader.newInstance(optimalSize.getWidth(), optimalSize.getHeight(), ImageFormat.JPEG, 2);
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
            if (textureView.getSurfaceTexture() == null) {
                Log.e("Camera2", "TextureView surface not available");
                return;
            }

            // Get optimal preview size (matching aspect ratio of the texture view)
            Size previewSize = getOptimalPreviewSize();

            // Configure the texture view to the preview size
            textureView.getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // Prepare surfaces for preview and capture
            Surface previewSurface = new Surface(textureView.getSurfaceTexture());
            Surface captureSurface = imageReader.getSurface();

            // Set up a preview request
            final CaptureRequest.Builder previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);

            // Apply quality settings for preview
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

            // Create capture session for both preview and capture
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, captureSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }

                            captureSession = session;
                            try {
                                // Start the preview
                                captureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null,
                                        null
                                );

                                Log.d("Camera2", "Live preview started successfully");
                            } catch (CameraAccessException e) {
                                Log.e("Camera2", "Failed to start preview: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e("Camera2", "Failed to configure camera session");
                        }
                    },
                    null
            );
        } catch (Exception e) {
            Log.e("Camera2", "Error starting preview: " + e.getMessage());
        }
    }

    private void takePictureWithFocusSequence() {
        if (cameraDevice == null || captureSession == null) {
            Log.e("Camera2", "Camera not ready for capture");
            return;
        }

        try {
            // Get camera characteristics for better settings
            CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraDevice.getId());

            // Create a pre-capture request to ensure focus and exposure
            final CaptureRequest.Builder precaptureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            precaptureBuilder.addTarget(imageReader.getSurface());

            // Set auto-focus trigger
            precaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);

            // Set auto-exposure precapture sequence
            precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            // Create a callback for monitoring focus and exposure
            CameraCaptureSession.CaptureCallback precaptureCallback = new CameraCaptureSession.CaptureCallback() {
                private boolean readyToCapture = false;

                private void processCaptureResult(CaptureResult result) {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                    boolean afReady = afState == null ||
                            afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;

                    boolean aeReady = aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED;

                    if (afReady && aeReady && !readyToCapture) {
                        readyToCapture = true;

                        // Now take the actual high-quality picture
                        try {
                            captureHighQualityImage(characteristics);
                        } catch (CameraAccessException e) {
                            Log.e("Camera2", "Failed to take picture: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
                    processCaptureResult(partialResult);
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    processCaptureResult(result);
                }
            };

            // Start the pre-capture focusing sequence
            captureSession.capture(precaptureBuilder.build(), precaptureCallback, null);
        } catch (CameraAccessException e) {
            Log.e("Camera2", "Error in precapture sequence: " + e.getMessage());
            // Fall back to direct capture
            try {
                CameraCharacteristics characteristics =
                        cameraManager.getCameraCharacteristics(cameraDevice.getId());
                captureHighQualityImage(characteristics);
            } catch (CameraAccessException ex) {
                Log.e("Camera2", "Failed to take picture: " + ex.getMessage());
            }
        }
    }

    private void captureHighQualityImage(CameraCharacteristics characteristics) throws CameraAccessException {
        // Create the capture request for a still image
        CaptureRequest.Builder captureBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureBuilder.addTarget(imageReader.getSurface());

        // 1. Enable auto-focus
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        // 2. Enable auto-exposure
        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

        // 3. Enable auto white balance
        captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

        // 4. Check if Optical Image Stabilization (OIS) is available
        int[] availableStabilizationModes = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        if (availableStabilizationModes != null && contains(availableStabilizationModes,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
            captureBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        }

        // 5. Check if HDR mode is available
        int[] availableSceneModes = characteristics.get(
                CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        if (availableSceneModes != null && contains(availableSceneModes,
                CaptureRequest.CONTROL_SCENE_MODE_HDR)) {
            captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                    CaptureRequest.CONTROL_SCENE_MODE_HDR);
            // Scene mode requires control mode to be set to USE_SCENE_MODE
            captureBuilder.set(CaptureRequest.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        }

        // 6. Set capture intent and quality settings
        captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
        captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

        // 7. Apply high quality post-processing
        captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY);
        captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE,
                CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CaptureRequest.TONEMAP_MODE,
                CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);

        // 8. Set proper orientation based on device rotation
        int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int jpegOrientation = (sensorOrientation + ORIENTATIONS.get(deviceRotation) + 360) % 360;
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);

        // 9. Take the picture and handle callback
        captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
                Log.d("Socket-Camera", "Picture taken with high quality settings!");

                // Continue the preview after capture
                try {
                    // Create a preview request builder
                    CaptureRequest.Builder previewBuilder =
                            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    previewBuilder.addTarget(new Surface(textureView.getSurfaceTexture()));

                    // Set auto-focus and auto-exposure for preview
                    previewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    previewBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);

                    // Resume the preview
                    captureSession.setRepeatingRequest(previewBuilder.build(), null, null);
                } catch (CameraAccessException e) {
                    Log.e("Camera2", "Failed to resume preview: " + e.getMessage());
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureFailure failure) {
                Log.e("Socket-Camera", "Capture failed: " + failure.getReason());
            }
        }, null);
    }

    // Helper method to check if an array contains a value
    private boolean contains(int[] array, int value) {
        if (array == null) return false;
        for (int i : array) {
            if (i == value) return true;
        }
        return false;
    }

    // ORIENTATIONS map to correct rotation values
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    // Method to get optimal preview size based on TextureView dimensions
    private Size getOptimalPreviewSize() throws CameraAccessException {
        CameraCharacteristics characteristics =
                cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
            Log.e("Camera2", "Could not get configuration map");
            return null;
        }

        // Get all available preview sizes
        Size[] availableSizes = map.getOutputSizes(SurfaceTexture.class);

        // Get the texture view dimensions
        int textureViewWidth = textureView.getWidth();
        int textureViewHeight = textureView.getHeight();

        // If texture view is not yet sized, use display metrics
        if (textureViewWidth == 0 || textureViewHeight == 0) {
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            textureViewWidth = displayMetrics.widthPixels;
            textureViewHeight = displayMetrics.heightPixels;
        }

        // Find the best matching size (closest aspect ratio)
        float targetRatio = (float) textureViewWidth / textureViewHeight;
        Size optimalSize = null;
        float minDiff = Float.MAX_VALUE;

        for (Size size : availableSizes) {
            float ratio = (float) size.getWidth() / size.getHeight();
            float diff = Math.abs(ratio - targetRatio);

            if (diff < minDiff) {
                optimalSize = size;
                minDiff = diff;
            }
        }

        return optimalSize;
    }
    private void takePicture() {
        try {

            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);  // Ensure max JPEG quality

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
