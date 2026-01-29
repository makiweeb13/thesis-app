package com.example.thesisapp;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import androidx.appcompat.app.AlertDialog;
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

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int MIN_IMAGE_WIDTH = 640;
    private static final int MIN_IMAGE_HEIGHT = 480;
    private static final int REQUEST_IMAGE_CAPTURE_LEGACY = 1; // For older onActivityResult
    private ImageView imageView;
    private Uri currentPhotoUri; // To store the URI of the captured image
    private TextView textViewFishClassValue;
    private TextView textViewFishWeightValue;
    private TextView textViewFishClassLabel;
    private TextView textViewFishWeightLabel;

    // --- Modern approach using ActivityResultLauncher (Recommended) ---
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
                if (result != null && result) { // 'result' is true if picture was taken and saved to URI
                    if (currentPhotoUri != null) {
                        imageView.setImageURI(currentPhotoUri);
                        Toast.makeText(this, "Image captured!", Toast.LENGTH_SHORT).show();
                        checkImageQualityAndProceed(currentPhotoUri);
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

    // *** NEW: Launcher for selecting an image from the gallery ***
    private final ActivityResultLauncher<String> selectImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    // An image was selected from the gallery
                    imageView.setImageURI(uri);
                    Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show();
                    checkImageQualityAndProceed(uri);
                } else {
                    Toast.makeText(this, "No image selected.", Toast.LENGTH_SHORT).show();
                    hideFishDetails();
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
        Button uploadButton = findViewById(R.id.buttonUpload); // *** Get the new upload button

        textViewFishClassLabel = findViewById(R.id.textViewFishClassLabel);
        textViewFishClassValue = findViewById(R.id.textViewFishClassValue);
        textViewFishWeightLabel = findViewById(R.id.textViewFishWeightLabel);
        textViewFishWeightValue = findViewById(R.id.textViewFishWeightValue);

        captureButton.setOnClickListener(v -> {
            // Check permissions before launching camera
            // checkPermissionsAndLaunchCamera();
            openCameraToCaptureImage();
        });

        // *** Set OnClickListener for the new upload button ***
        uploadButton.setOnClickListener(v -> {
            // Launch the gallery to select an image
            selectImageLauncher.launch("image/*");
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
            currentPhotoUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider", // Authority must match AndroidManifest
                    photoFile);
            takePictureLauncher.launch(currentPhotoUri); // Use the modern launcher
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    // *** NEW: Method to check image quality before processing ***
    private void checkImageQualityAndProceed(Uri imageUri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // This will only decode the bounds, not the full bitmap

        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            BitmapFactory.decodeStream(inputStream, null, options);
        } catch (IOException e) {
            Log.e(TAG, "Error checking image dimensions: " + e.getMessage(), e);
            Toast.makeText(this, "Could not read image details. Proceeding anyway.", Toast.LENGTH_SHORT).show();
            processImageAndFetchFishDetails(imageUri); // Proceed as a fallback
            return;
        }

        int imageWidth = options.outWidth;
        int imageHeight = options.outHeight;

        Log.d(TAG, "Image dimensions: " + imageWidth + "x" + imageHeight);

        if (imageWidth < MIN_IMAGE_WIDTH || imageHeight < MIN_IMAGE_HEIGHT) {
            // Image quality is below threshold, show a dialog
            new AlertDialog.Builder(this)
                    .setTitle("Low Image Quality")
                    .setMessage("The captured image resolution (" + imageWidth + "x" + imageHeight +
                            ") might be too low for accurate results. Do you want to proceed anyway?")
                    .setPositiveButton("Proceed Anyway", (dialog, which) -> {
                        // User wants to proceed with the current image
                        processImageAndFetchFishDetails(imageUri);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        // User cancels the upload
                        Toast.makeText(this, "Upload cancelled.", Toast.LENGTH_SHORT).show();
                        hideFishDetails();
                        imageView.setImageURI(null); // Clear preview
                    })
                    .setCancelable(false) // Prevent dismissing by tapping outside
                    .show();
        } else {
            // Image quality is acceptable, proceed to upload
            processImageAndFetchFishDetails(imageUri);
        }
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

        File imageFile = null;
        try {
            imageFile = createFileFromUri(imageUri, "upload_image.jpg");
            if (!imageFile.exists()) {
                Log.e(TAG, "Failed to create file from URI or file does not exist.");
                Toast.makeText(this, "Error preparing image for upload.", Toast.LENGTH_LONG).show();
                hideFishDetails();
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException while creating file from URI: " + e.getMessage(), e);
            Toast.makeText(this, "Error preparing image file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            hideFishDetails();
            return;
        }

        String mimeType = getContentResolver().getType(imageUri);
        RequestBody requestFile = RequestBody.create(imageFile, MediaType.parse(mimeType != null ? mimeType : "image/*"));
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", imageFile.getName(), requestFile);

        String yourApiKey = "YOUR_ACTUAL_API_KEY";
        RequestBody apiKeyPart = RequestBody.create(MediaType.parse("text/plain"), yourApiKey);


        ApiService apiService = RetrofitClient.getApiService();
        Call<FishDetailsResponse> call = apiService.uploadImageAndGetDetails(body, apiKeyPart);

        call.enqueue(new retrofit2.Callback<FishDetailsResponse>() {
            @Override
            public void onResponse(@NonNull Call<FishDetailsResponse> call, @NonNull Response<FishDetailsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FishDetailsResponse fishDetails = response.body();
                    Log.d(TAG, "API call successful. Response: " + fishDetails.toString());
                    Toast.makeText(MainActivity.this, "Fish details received!", Toast.LENGTH_SHORT).show();

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
                    try {
                        if (response.errorBody() != null) {
                            Toast.makeText(MainActivity.this, "Error: " + response.code() + " " + response.errorBody().string(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Error: " + response.code() + " " + response.message(), Toast.LENGTH_LONG).show();
                        }
                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, "Error: " + response.code() + " and error parsing failed.", Toast.LENGTH_LONG).show();
                    }
                    hideFishDetails();
                }
            }

            @Override
            public void onFailure(Call<FishDetailsResponse> call, Throwable t) {
                Log.e(TAG, "Network call failed: " + t.getMessage(), t);
                Toast.makeText(MainActivity.this, "Network request failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                hideFishDetails();
            }
        });
    }

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

            File cacheDir = getApplicationContext().getCacheDir();
            tempFile = new File(cacheDir, fileName);

            outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024 * 4];
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

            textViewFishClassValue.setText("");
            textViewFishWeightValue.setText("");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE_LEGACY && resultCode == RESULT_OK) {
            if (currentPhotoUri != null) {
                imageView.setImageURI(currentPhotoUri);
                Toast.makeText(this, "Image (legacy) saved to: " + currentPhotoUri.toString(), Toast.LENGTH_LONG).show();
            }
            else if (data != null && data.getExtras() != null) {
                Bundle extras = data.getExtras();
                Bitmap imageBitmap = (Bitmap) extras.get("data");
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
