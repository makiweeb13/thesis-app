package com.example.thesisapp;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.media3.common.util.UnstableApi;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE_LEGACY = 1; // For older onActivityResult
    private ImageView imageView;
    private Uri currentPhotoUri; // To store the URI of the captured image

    // --- Modern approach using ActivityResultLauncher (Recommended) ---
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
                if (result != null && result) { // 'result' is true if picture was taken and saved to URI
                    if (currentPhotoUri != null) {
                        imageView.setImageURI(currentPhotoUri);
                        // You can now use currentPhotoUri to access the full-resolution image
                        Toast.makeText(this, "Image captured and saved to: " + currentPhotoUri.toString(), Toast.LENGTH_LONG).show();
                    } else {
                        // This case might happen if the camera app doesn't stick to the contract
                        Toast.makeText(this, "Image URI is null after capture.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Image capture failed or was cancelled.", Toast.LENGTH_SHORT).show();
                }
            });

    // Launcher for getting a thumbnail (if you don't provide a URI to save to)
    private final ActivityResultLauncher<Void> takePicturePreviewLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    Toast.makeText(this, "Image preview received.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to get image preview.", Toast.LENGTH_SHORT).show();
                }
            });


    // --- Permission Handling ---
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean granted : permissions.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    // Permissions granted, proceed with camera launch
                    openCameraToCaptureImage();
                } else {
                    Toast.makeText(this, "Camera and/or Storage permissions are required.", Toast.LENGTH_SHORT).show();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Make sure this layout has an ImageView and a Button

        imageView = findViewById(R.id.imageViewPreview); // Add this ID to your ImageView in XML
        Button captureButton = findViewById(R.id.buttonCapture);   // Add this ID to your Button in XML

        captureButton.setOnClickListener(v -> {
            // Check permissions before launching camera
            // checkPermissionsAndLaunchCamera();
            openCameraToCaptureImage();
        });
    }

    private void checkPermissionsAndLaunchCamera() {
        String[] permissionsToRequest;
        // For Android 10 (API 29) and above, WRITE_EXTERNAL_STORAGE is not needed for app-specific files.
        // However, CAMERA permission is always needed.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissionsToRequest = new String[]{Manifest.permission.CAMERA};
        } else {
            // For older versions, you might need WRITE_EXTERNAL_STORAGE if saving to public directories
            // or if the camera app you are delegating to requires it.
            // However, using FileProvider with app-specific directory is generally safer.
            permissionsToRequest = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }

        boolean allPermissionsGranted = true;
        for (String permission : permissionsToRequest) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            openCameraToCaptureImage();
        } else {
            requestPermissionLauncher.launch(permissionsToRequest);
        }
    }


    private void openCameraToCaptureImage() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Option 1: Get a full-resolution image saved to a file you specify
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
                return;
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider", // Authority must match AndroidManifest
                        photoFile);
                takePictureLauncher.launch(currentPhotoUri); // Use the modern launcher
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }

        // Option 2: Get a small bitmap preview (thumbnail) directly in the result.
        // This is simpler if you don't need the full-resolution image or don't want to handle files.
        // Comment out Option 1 and uncomment this if you prefer this way:
        /*
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                takePicturePreviewLauncher.launch(null); // Pass null as we don't specify output URI
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
        */
    }


    @OptIn(markerClass = UnstableApi.class)
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES); // App-specific directory

        // Ensure the directory exists
        if (storageDir != null && !storageDir.exists()){
            if(!storageDir.mkdirs()){
                androidx.media3.common.util.Log.e("CameraApp", "failed to create directory");
                throw new IOException("Failed to create directory for image");
            }
        }

        /* prefix */
        /* suffix */
        /* directory */
        // Save a file: path for use with ACTION_VIEW intents (not needed for ACTION_IMAGE_CAPTURE directly)
        // currentPhotoPath = image.getAbsolutePath(); // Not strictly needed if using URI
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }


    // --- Legacy onActivityResult (if not using ActivityResultLauncher) ---
    // You can remove this if you are exclusively using ActivityResultLaunchers
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); // Important for Fragment results

        if (requestCode == REQUEST_IMAGE_CAPTURE_LEGACY && resultCode == RESULT_OK) {
            // Option 1: If you provided EXTRA_OUTPUT (currentPhotoUri will be set)
            if (currentPhotoUri != null) {
                imageView.setImageURI(currentPhotoUri);
                Toast.makeText(this, "Image (legacy) saved to: " + currentPhotoUri.toString(), Toast.LENGTH_LONG).show();
                // To delete the temp file if you only wanted a bitmap (less common with EXTRA_OUTPUT)
                // new File(currentPhotoUri.getPath()).delete();
            }
            // Option 2: If you did NOT provide EXTRA_OUTPUT (data will contain a thumbnail Bitmap)
            else if (data != null && data.getExtras() != null) {
                Bundle extras = data.getExtras();
                Bitmap imageBitmap = (Bitmap) extras.get("data"); // "data" is the key for the thumbnail
                if (imageBitmap != null) {
                    imageView.setImageBitmap(imageBitmap);
                    Toast.makeText(this, "Image thumbnail (legacy) received.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to get image (legacy).", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
