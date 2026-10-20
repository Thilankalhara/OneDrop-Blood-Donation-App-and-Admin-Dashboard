package com.example.onedrop.Activity.Fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";

    private PreviewView  cameraPreview;
    private View         scanLine;
    private LinearLayout scanButton;
    private TextView     scanButtonText;
    private LinearLayout resultOverlay;
    private TextView     resultIcon, resultTitle, resultMessage, resultDoneButton;

    private ProcessCameraProvider cameraProvider;
    private boolean scanning    = false;
    private boolean resultShown = false;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler    = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startCameraAfterPermission();
                else {
                    Toast.makeText(requireContext(), "Camera permission required. Enable in Settings → Apps → OneDrop → Permissions.", Toast.LENGTH_LONG).show();
                    scanButtonText.setText("Scan QR Code Now"); scanning = false;
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cameraPreview    = view.findViewById(R.id.cameraPreview);
        scanLine         = view.findViewById(R.id.scanLine);
        scanButton       = view.findViewById(R.id.scanButton);
        scanButtonText   = view.findViewById(R.id.scanButtonText);
        resultOverlay    = view.findViewById(R.id.resultOverlay);
        resultIcon       = view.findViewById(R.id.resultIcon);
        resultTitle      = view.findViewById(R.id.resultTitle);
        resultMessage    = view.findViewById(R.id.resultMessage);
        resultDoneButton = view.findViewById(R.id.resultDoneButton);

        scanButton.setOnClickListener(v -> { if (!scanning) startScanning(); else stopScanning(); });

        resultDoneButton.setOnClickListener(v -> {
            resultOverlay.setVisibility(View.GONE);
            resultShown = false; scanning = false;
            scanButtonText.setText("Scan QR Code Now");
        });

        startScanLineAnimation();
    }

    private void startScanLineAnimation() {
        TranslateAnimation anim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0f, Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, -0.45f, Animation.RELATIVE_TO_PARENT, 0.45f);
        anim.setDuration(2000); anim.setRepeatCount(Animation.INFINITE); anim.setRepeatMode(Animation.REVERSE);
        scanLine.startAnimation(anim);
    }

    private void startScanning() {
        boolean profileComplete = requireActivity()
                .getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                .getBoolean("profile_complete", false);
        if (!profileComplete) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Complete Your Profile First")
                    .setMessage("You need to add your mobile number, gender, and blood type before scanning QR codes.")
                    .setPositiveButton("Go to Profile", (d, w) -> {
                        if (getActivity() instanceof com.example.onedrop.Activity.MainActivity)
                            ((com.example.onedrop.Activity.MainActivity) getActivity()).navigateToProfile();
                    })
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        scanning = true; scanButtonText.setText("Scanning...");
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCameraAfterPermission();
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
    }

    private void startCameraAfterPermission() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            try { cameraProvider = future.get(); bindCamera(); }
            catch (Exception e) {
                Log.e(TAG, "Camera init error: " + e.getMessage(), e);
                mainHandler.post(() -> { Toast.makeText(requireContext(), "Camera error: "+e.getMessage(), Toast.LENGTH_SHORT).show(); scanning=false; scanButtonText.setText("Scan QR Code Now"); });
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCamera() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        BarcodeScanner barcodeScanner = BarcodeScanning.getClient(
                new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

        analysis.setAnalyzer(cameraExecutor, (ImageProxy imageProxy) -> {
            if (resultShown || !scanning) { imageProxy.close(); return; }
            @SuppressWarnings("UnsafeOptInUsageError")
            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) { imageProxy.close(); return; }
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            barcodeScanner.process(image)
                    .addOnSuccessListener((List<Barcode> barcodes) -> {
                        for (Barcode barcode : barcodes) {
                            String value = barcode.getRawValue();
                            if (value != null && !value.isEmpty() && !resultShown) {
                                resultShown = true; Log.d(TAG, "QR: " + value);
                                mainHandler.post(() -> verifyQrCode(value)); break;
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Barcode fail: "+e.getMessage()))
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        try { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis); }
        catch (Exception e) { Log.e(TAG, "Camera bind: "+e.getMessage(), e); }
    }

    private void stopScanning() { scanning=false; scanButtonText.setText("Scan QR Code Now"); if (cameraProvider!=null) cameraProvider.unbindAll(); }

    private void verifyQrCode(String qrCode) {
        scanButtonText.setText("Verifying...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(Constants.SCAN_QR_ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                AuthManager.attachToken(requireContext(), conn); // ✅ JWT
                conn.setDoOutput(true); conn.setConnectTimeout(8000); conn.setReadTimeout(8000);

                // ✅ Only qr_code in body — no user_id (server reads from JWT attribute)
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("qr_code", qrCode);

                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.write(requestBody.toString().getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(code<400?conn.getInputStream():conn.getErrorStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line=reader.readLine())!=null) sb.append(line);
                reader.close(); Log.d(TAG,"Scan response: "+sb);

                JsonObject json   = JsonParser.parseString(sb.toString()).getAsJsonObject();
                String     status = json.has("status")  ? json.get("status").getAsString()  : "error";
                String     msg    = json.has("message") ? json.get("message").getAsString()  : "Unknown error";

                mainHandler.post(() -> {
                    if (getView()==null) return;
                    switch (status) {
                        case "success":
                            int pts=json.has("points_earned")?json.get("points_earned").getAsInt():100;
                            int count=json.has("donation_count")?json.get("donation_count").getAsInt():0;
                            int total=json.has("total_points")?json.get("total_points").getAsInt():0;
                            showResult(true,"Donation Verified! 🎉","+"+pts+" pts  •  "+count+" donations  •  "+total+" total pts");
                            break;
                        case "already_verified": showToastAndReset("Already verified — this donation was previously confirmed."); break;
                        case "unused":           showToastAndReset("QR code not yet active. Try after donation is processed."); break;
                        case "deactivated":      showToastAndReset("This QR code has been deactivated."); break;
                        default:                 showResult(false,"Verification Failed",msg); break;
                    }
                });
            } catch (Exception e) {
                Log.e(TAG,"Scan API error: "+e.getMessage(),e);
                mainHandler.post(() -> showResult(false,"Connection Error","Could not reach server. Please try again."));
            }
        });
    }

    private void showResult(boolean success, String title, String message) {
        if (getView()==null) return; stopScanning();
        resultIcon.setText(success?"✓":"✕");
        resultIcon.setBackgroundResource(success?R.drawable.result_success_circle:R.drawable.result_error_circle);
        resultTitle.setText(title); resultMessage.setText(message);
        resultOverlay.setVisibility(View.VISIBLE);
        scanButtonText.setText("Scan QR Code Now");
    }

    private void showToastAndReset(String message) {
        Toast.makeText(requireContext(),message,Toast.LENGTH_LONG).show();
        resultShown=false; scanning=false; scanButtonText.setText("Scan QR Code Now");
    }

    @Override public void onDestroyView() { super.onDestroyView(); if (cameraProvider!=null) cameraProvider.unbindAll(); cameraExecutor.shutdown(); }
}