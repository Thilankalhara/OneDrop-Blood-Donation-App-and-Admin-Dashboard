package com.example.onedrop.Activity.Fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BloodDonationMapFragment extends Fragment {

    private static final String TAG = "BloodDonationMapFrag";

    // ✅ OSM tile usage policy: a unique, descriptive User-Agent with contact info.
    // TODO: replace the email below with your real contact address.
    private static final String OSM_USER_AGENT =
            "OneDropBloodDonationApp/1.0 (Android; contact: your-email@example.com)";

    // ✅ Tile provider setup.
    // OSM's public server blocks the app (403) and CARTO now needs an API key.
    // Best option: create a FREE key at https://cloud.maptiler.com/ (no credit card) and paste it here.
    // If MAPTILER_KEY is left empty, the map falls back to OpenTopoMap (no key, light use only).
    private static final String MAPTILER_KEY = "";

    private static ITileSource buildTileSource() {
        if (MAPTILER_KEY != null && !MAPTILER_KEY.trim().isEmpty()) {
            return new OnlineTileSourceBase("MapTilerStreets", 0, 20, 256, ".png",
                    new String[]{"https://api.maptiler.com/maps/streets-v2/256/"},
                    "© MapTiler © OpenStreetMap contributors") {
                @Override
                public String getTileURLString(long pMapTileIndex) {
                    return getBaseUrl()
                            + MapTileIndex.getZoom(pMapTileIndex) + "/"
                            + MapTileIndex.getX(pMapTileIndex) + "/"
                            + MapTileIndex.getY(pMapTileIndex)
                            + mImageFilenameEnding + "?key=" + MAPTILER_KEY.trim();
                }
            };
        }
        return new XYTileSource("OpenTopoMap", 0, 17, 256, ".png",
                new String[]{
                        "https://a.tile.opentopomap.org/",
                        "https://b.tile.opentopomap.org/",
                        "https://c.tile.opentopomap.org/"},
                "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)");
    }

    private MapView      osmMap;
    private LinearLayout hospitalBottomSheet;
    private LinearLayout mapLoadingOverlay;
    private TextView     hospitalName;
    private TextView     hospitalPhone;
    private TextView     hospitalStatus;
    private View         hospitalStatusDot;
    private LinearLayout callBtn;
    private LinearLayout openMapBtn;
    private TextView     backBtn;

    private String selectedPhone = "";
    private double selectedLat   = 0;
    private double selectedLng   = 0;

    private float   touchStartY   = 0f;
    private boolean trackingSwipe = false;
    private static final int SWIPE_MIN_PX = 80;

    private ExecutorService executor;
    private final Handler   mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureOsmdroid(requireContext().getApplicationContext());
    }

    /**
     * ✅ Makes the app comply with OpenStreetMap's tile usage policy:
     *  - custom User-Agent (the default com.example.* package name gets blocked)
     *  - limited parallel tile downloads
     *  - fresh private tile cache (old cache may hold cached "Access blocked" tiles)
     */
    private void configureOsmdroid(Context ctx) {
        IConfigurationProvider cfg = Configuration.getInstance();

        // load() first, because it resets the user agent; set ours afterwards
        cfg.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        cfg.setUserAgentValue(OSM_USER_AGENT);

        // App-private cache: no storage permission needed, and new path avoids old blocked tiles
        File base  = new File(ctx.getCacheDir(), "osmdroid_v2");
        File tiles = new File(base, "tiles");
        //noinspection ResultOfMethodCallIgnored
        tiles.mkdirs();
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(tiles);

        // Be gentle with the OSM servers
        cfg.setTileDownloadThreads((short) 2);
        cfg.setTileFileSystemThreads((short) 2);
        cfg.setTileDownloadMaxQueueSize((short) 20);
        cfg.setTileFileSystemMaxQueueSize((short) 20);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (hospitalBottomSheet != null
                                && hospitalBottomSheet.getVisibility() == View.VISIBLE
                                && isTouchInsideView(ev, hospitalBottomSheet)) {
                            touchStartY   = ev.getRawY();
                            trackingSwipe = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (trackingSwipe) {
                            if (ev.getRawY() - touchStartY > SWIPE_MIN_PX) hideBottomSheet();
                            trackingSwipe = false;
                        }
                        break;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        inflater.inflate(R.layout.fragment_blood_donation_map, root, true);
        return root;
    }

    private boolean isTouchInsideView(MotionEvent ev, View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float x = ev.getRawX(), y = ev.getRawY();
        return x >= loc[0] && x <= loc[0] + v.getWidth()
                && y >= loc[1] && y <= loc[1] + v.getHeight();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executor = Executors.newSingleThreadExecutor();

        osmMap               = view.findViewById(R.id.osmMap);
        hospitalBottomSheet  = view.findViewById(R.id.hospitalBottomSheet);
        mapLoadingOverlay    = view.findViewById(R.id.mapLoadingOverlay);
        hospitalName         = view.findViewById(R.id.hospitalName);
        hospitalPhone        = view.findViewById(R.id.hospitalPhone);
        hospitalStatus       = view.findViewById(R.id.hospitalStatus);
        hospitalStatusDot    = view.findViewById(R.id.hospitalStatusDot);
        callBtn              = view.findViewById(R.id.callBtn);
        openMapBtn           = view.findViewById(R.id.openMapBtn);
        backBtn              = view.findViewById(R.id.backBtn);

        osmMap.setTileSource(buildTileSource());
        Log.d(TAG, "osmdroid User-Agent = " + Configuration.getInstance().getUserAgentValue());
        osmMap.setMultiTouchControls(true);
        osmMap.setMinZoomLevel(5.0);      // avoid pointless tile requests when zoomed far out
        osmMap.setMaxZoomLevel(17.0);
        osmMap.getController().setZoom(12.0);
        osmMap.getController().setCenter(new GeoPoint(7.8731, 80.7718));

        backBtn.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
            else requireActivity().onBackPressed();
        });

        osmMap.setOnClickListener(v -> hideBottomSheet());

        callBtn.setOnClickListener(v -> {
            if (!selectedPhone.isEmpty())
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + selectedPhone)));
            else
                Toast.makeText(requireContext(), "No phone number available.", Toast.LENGTH_SHORT).show();
        });

        openMapBtn.setOnClickListener(v -> {
            if (selectedLat != 0 && selectedLng != 0) {
                String label = Uri.encode(hospitalName.getText().toString());
                Uri gmmUri   = Uri.parse("google.navigation:q=" + selectedLat + "," + selectedLng + "&title=" + label);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(requireActivity().getPackageManager()) != null)
                    startActivity(mapIntent);
                else
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + selectedLat + "," + selectedLng)));
            } else {
                Toast.makeText(requireContext(), "Location not available.", Toast.LENGTH_SHORT).show();
            }
        });

        fetchHospitals();
    }

    private void fetchHospitals() {
        if (executor == null || executor.isShutdown()) executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String result = httpGet(Constants.HOSPITALS_ENDPOINT);
                JSONArray hospitals = new JSONArray(result);
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < hospitals.length(); i++) list.add(hospitals.getJSONObject(i));
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    mapLoadingOverlay.setVisibility(View.GONE);
                    plotHospitals(list);
                });
            } catch (Exception e) {
                Log.e(TAG, "fetchHospitals error: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    mapLoadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Failed to load centers.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void plotHospitals(List<JSONObject> hospitals) {
        osmMap.getOverlays().clear();
        osmMap.getOverlays().add(new CopyrightOverlay(requireContext()));
        if (hospitals.isEmpty()) {
            Toast.makeText(requireContext(), "No donation centers found.", Toast.LENGTH_SHORT).show();
            return;
        }
        double firstLat = 0, firstLng = 0;
        for (JSONObject h : hospitals) {
            try {
                String name   = h.optString("name",   "Center");
                String phone  = h.optString("phone",  "");
                String status = h.optString("status", "ACTIVE");
                double lat    = h.optDouble("latitude",  0);
                double lng    = h.optDouble("longitude", 0);
                if (lat == 0 && lng == 0) continue;
                if (firstLat == 0) { firstLat = lat; firstLng = lng; }
                GeoPoint point  = new GeoPoint(lat, lng);
                Marker   marker = new Marker(osmMap);
                marker.setPosition(point);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setTitle(name);
                marker.setIcon(new BitmapDrawable(getResources(), createMarkerBitmap("ACTIVE".equals(status))));
                final String fName = name, fPhone = phone, fStatus = status;
                final double fLat  = lat,  fLng   = lng;
                marker.setOnMarkerClickListener((m, mv) -> {
                    showBottomSheet(fName, fPhone, fStatus, fLat, fLng);
                    osmMap.getController().animateTo(point);
                    return true;
                });
                osmMap.getOverlays().add(marker);
            } catch (Exception e) { Log.e(TAG, "marker error: " + e.getMessage()); }
        }
        if (firstLat != 0) {
            osmMap.getController().animateTo(new GeoPoint(firstLat, firstLng));
            osmMap.getController().setZoom(13.0);
        }
        osmMap.invalidate();
    }

    private Bitmap createMarkerBitmap(boolean isActive) {
        int size = 80;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#33000000"));
        canvas.drawCircle(size / 2f + 2, size / 2f + 4, size / 2f - 6, shadowPaint);
        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(isActive ? Color.parseColor("#DC143C") : Color.parseColor("#AAAAAA"));
        canvas.drawCircle(size / 2f, size / 2f - 2, size / 2f - 6, circlePaint);
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        canvas.drawCircle(size / 2f, size / 2f - 2, size / 2f - 6, borderPaint);
        Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossPaint.setColor(Color.WHITE);
        crossPaint.setStrokeWidth(6f);
        crossPaint.setStrokeCap(Paint.Cap.ROUND);
        float cx = size / 2f, cy = size / 2f - 2;
        canvas.drawLine(cx, cy - 14, cx, cy + 14, crossPaint);
        canvas.drawLine(cx - 14, cy, cx + 14, cy, crossPaint);
        return bitmap;
    }

    private void showBottomSheet(String name, String phone, String status, double lat, double lng) {
        selectedPhone = phone; selectedLat = lat; selectedLng = lng;
        hospitalName.setText(name);
        hospitalPhone.setText(phone.isEmpty() ? "No phone available" : phone);
        boolean isActive = "ACTIVE".equals(status);
        hospitalStatus.setText(isActive ? "Active" : "Inactive");
        hospitalStatus.setTextColor(Color.parseColor(isActive ? "#2E7D32" : "#888888"));
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(Color.parseColor(isActive ? "#4CAF50" : "#AAAAAA"));
        hospitalStatusDot.setBackground(dot);
        if (hospitalBottomSheet.getVisibility() == View.GONE) {
            hospitalBottomSheet.setVisibility(View.VISIBLE);
            TranslateAnimation slide = new TranslateAnimation(0, 0, hospitalBottomSheet.getHeight(), 0);
            slide.setDuration(300);
            hospitalBottomSheet.startAnimation(slide);
        }
    }

    private void hideBottomSheet() {
        if (hospitalBottomSheet.getVisibility() == View.VISIBLE) {
            TranslateAnimation slide = new TranslateAnimation(0, 0, 0, hospitalBottomSheet.getHeight());
            slide.setDuration(250);
            slide.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                @Override public void onAnimationStart(android.view.animation.Animation a) {}
                @Override public void onAnimationRepeat(android.view.animation.Animation a) {}
                @Override public void onAnimationEnd(android.view.animation.Animation a) {
                    hospitalBottomSheet.setVisibility(View.GONE);
                }
            });
            hospitalBottomSheet.startAnimation(slide);
        }
    }

    @Override public void onResume()      { super.onResume();  osmMap.onResume();  }
    @Override public void onPause()       { super.onPause();   osmMap.onPause();   }
    @Override public void onDestroyView() {
        super.onDestroyView();
        if (osmMap != null) osmMap.onDetach();
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
    }

    // ✅ JWT token attached via AuthManager
    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        AuthManager.attachToken(requireContext(), conn); // ✅ JWT
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.connect();
        int code = conn.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                code < 400 ? conn.getInputStream() : conn.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close(); conn.disconnect();
        return sb.toString();
    }
}
