package com.example.thesisapp;

import static android.content.ContentValues.TAG;

import android.Manifest;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.TakePicture;
import okhttp3.MediaType;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE_LEGACY = 1; // For older onActivityResult
    private ImageView imageView;
    private Uri currentPhotoUri; // To store the URI of the captured image
    private TextView textViewFishClassValue;
    private TextView textViewFishWeightValue;
    private TextView textViewFishClassLabel;
    private TextView textViewFishWeightLabel;

    // --- Modern approach using ActivityResultLauncher (Recommended) ---
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new TakePicture(), result -> {
                if (result != null && result) { // 'result' is true if picture was taken and saved to URI
                    if (currentPhotoUri != null) {
                        imageView.setImageURI(currentPhotoUri);
                        // You can now use currentPhotoUri to access the full-resolution image
                        Toast.makeText(this, "Image captured and saved to: " + currentPhotoUri.toString(), Toast.LENGTH_LONG).show();
                        processImageAndFetchFishDetails(currentPhotoUri);
                    } else {
                        // This case might happen if the camera app doesn't stick to the contract
                        Toast.makeText(this, "Image URI is null after capture.", Toast.LENGTH_SHORT).show();
                        hideFishDetails();
                    }
                } else {
                    Toast.makeText(this, "Image capture failed or was cancelled.", Toast.LENGTH_SHORT).show();
                    hideFishDetails();
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
                hideFishDetails();
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

        textViewFishClassLabel = findViewById(R.id.textViewFishClassLabel);
        textViewFishClassValue = findViewById(R.id.textViewFishClassValue);
        textViewFishWeightLabel = findViewById(R.id.textViewFishWeightLabel);
        textViewFishWeightValue = findViewById(R.id.textViewFishWeightValue);

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

    @OptIn(markerClass = UnstableApi.class)
    private void processImageAndFetchFishDetails(Uri imageUri) {
        Log.d(TAG, "Attempting to process image: " + imageUri.toString());
        Toast.makeText(this, "Processing image and fetching details...", Toast.LENGTH_SHORT).show();

        // Show some loading indicator if you have one
        // progressBar.setVisibility(View.VISIBLE);
//        groupFishDetails.setVisibility(View.GONE); // Hide previous details
//        textViewPlaceholder.setText("Processing..."); // Update placeholder
//        textViewPlaceholder.setVisibility(View.VISIBLE);


        File imageFile = null;
        try {
            // Create a temporary file from the content URI to upload
            // This is one way; alternatives exist depending on exact URI type and permissions
            imageFile = createFileFromUri(imageUri, "upload_image.jpg");
            if (!imageFile.exists()) {
                Log.e(TAG, "Failed to create file from URI or file does not exist.");
                Toast.makeText(this, "Error preparing image for upload.", Toast.LENGTH_LONG).show();
                hideFishDetails(); // Or show placeholder with error
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException while creating file from URI: " + e.getMessage(), e);
            Toast.makeText(this, "Error preparing image file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            hideFishDetails(); // Or show placeholder with error
            return;
        }

        // Create RequestBody instance from file
// Corrected line
        String mimeType = getContentResolver().getType(imageUri);
        RequestBody requestFile = RequestBody.create(imageFile, MediaType.parse(mimeType != null ? mimeType : "image/*"));
        // MultipartBody.Part is used to send also the actual file name
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", imageFile.getName(), requestFile); // "imageFile" is the name of the part in your API

        // Example for another part (e.g., API Key)
        String yourApiKey = "YOUR_ACTUAL_API_KEY"; // Replace with your actual API key or get it securely
        RequestBody apiKeyPart = RequestBody.create(MediaType.parse("text/plain"), yourApiKey);


        ApiService apiService = RetrofitClient.getApiService();
        Call<FishDetailsResponse> call = apiService.uploadImageAndGetDetails(body, apiKeyPart); // Pass the parts

        // Store the file to delete it after the call
        final File finalImageFile = imageFile;

        // Inside your processImageAndFetchFishDetails method or wherever the network call is made

        call.enqueue(new retrofit2.Callback<FishDetailsResponse>() { // Use retrofit2.Callback
            @Override
            public void onResponse(@NonNull Call<FishDetailsResponse> call, @NonNull Response<FishDetailsResponse> response) {
                // Hide loading indicator
                // progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    FishDetailsResponse fishDetails = response.body();
                    Log.d(TAG, "API call successful. Response: " + fishDetails.toString()); // Log the parsed object

                    // textViewFishClassValue.setText(fishDetails.getFishClass());
                    // textViewFishWeightValue.setText(String.valueOf(fishDetails.getWeight()));
                    // showFishDetails(); // Make your details TextViews visible
                    Toast.makeText(MainActivity.this, "Fish details received!", Toast.LENGTH_SHORT).show();
                    // For example:
                    if (fishDetails.getFishClass() != null) {
                        textViewFishClassValue.setText(fishDetails.getFishClass());
                        textViewFishClassLabel.setVisibility(View.VISIBLE);
                        textViewFishClassValue.setVisibility(View.VISIBLE);
                    } else {
                        textViewFishClassValue.setText("N/A");
                    }
                    if (fishDetails.getWeight() > 0) {
                        textViewFishWeightValue.setText(String.format(Locale.getDefault(), "%.2f g", fishDetails.getWeight()));
                        textViewFishWeightLabel.setVisibility(View.VISIBLE);
                        textViewFishWeightValue.setVisibility(View.VISIBLE);
                    } else {
                        textViewFishWeightValue.setText("N/A");
                    }

                } else {
                    // Handle API error (e.g., response.code(), response.errorBody())
                    // Log.e(TAG, "API Error: " + response.code() + " - " + response.message());
                    try {
                        if (response.errorBody() != null) {
                            // Log.e(TAG, "Error Body: " + response.errorBody().string());
                            Toast.makeText(MainActivity.this, "Error: " + response.code() + " " + response.errorBody().string(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Error: " + response.code() + " " + response.message(), Toast.LENGTH_LONG).show();
                        }
                    } catch (IOException e) {
                        // Log.e(TAG, "Error parsing error body", e);
                        Toast.makeText(MainActivity.this, "Error: " + response.code() + " and error parsing failed.", Toast.LENGTH_LONG).show();
                    }
                    hideFishDetails(); // Or show placeholder with error
                }
            }

            @Override
            public void onFailure(Call<FishDetailsResponse> call, Throwable t) {
                // Handle failure (e.g., network error, an exception during processing)
                // Log.e(TAG, "Network call failed: " + t.getMessage(), t);
                // Toast.makeText(MainActivity.this, "Network request failed: " + t.getMessage(), Toast.LENGTH_LONG).show();

                // Hide loading indicator
                // progressBar.setVisibility(View.GONE);
                hideFishDetails(); // Or show placeholder with error
                // textViewPlaceholder.setText("Failed to fetch details. Check connection.");
                // textViewPlaceholder.setVisibility(View.VISIBLE);
            }
        });
    }

    // Helper method to create a file from a content URI (can be complex depending on URI source)
    @OptIn(markerClass = UnstableApi.class)
    private File createFileFromUri(Uri contentUri, String fileName) throws IOException {
        File tempFile = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            inputStream = getContentResolver().openInputStream(contentUri);
            if (inputStream == null) {
                throw new IOException("Unable to open input stream from URI: " + contentUri);
            }

            // Create a temporary file in the app's cache directory
            File cacheDir = getApplicationContext().getCacheDir();
            tempFile = new File(cacheDir, fileName);

            outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024 * 4]; // 4KB buffer
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            Log.d(TAG, "File created successfully: " + tempFile.getAbsolutePath());
            return tempFile;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: " + e.getMessage());
            }
        }
    }

    private void updateFishDetailsUI(String fishClass, String fishWeight) {
        if (textViewFishClassValue != null && textViewFishWeightValue != null &&
                textViewFishClassLabel != null && textViewFishWeightLabel != null) {

            textViewFishClassValue.setText(fishClass);
            textViewFishWeightValue.setText(fishWeight);

            textViewFishClassLabel.setVisibility(View.VISIBLE);
            textViewFishClassValue.setVisibility(View.VISIBLE);
            textViewFishWeightLabel.setVisibility(View.VISIBLE);
            textViewFishWeightValue.setVisibility(View.VISIBLE);
        }
    }

    private void hideFishDetails() {
        if (textViewFishClassValue != null && textViewFishWeightValue != null &&
                textViewFishClassLabel != null && textViewFishWeightLabel != null) {

            textViewFishClassLabel.setVisibility(View.GONE);
            textViewFishClassValue.setVisibility(View.GONE);
            textViewFishWeightLabel.setVisibility(View.GONE);
            textViewFishWeightValue.setVisibility(View.GONE);

            // Optionally clear the text
            textViewFishClassValue.setText("");
            textViewFishWeightValue.setText("");
        }
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
