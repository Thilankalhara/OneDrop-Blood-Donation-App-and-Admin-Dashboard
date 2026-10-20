package com.example.onedrop.Activity.Fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.onedrop.Activity.SignInActivity;
import com.example.onedrop.Activity.Database.UserDataManager;
import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends BaseRefreshFragment {

    private static final String TAG = "ProfileFragment";
    private static final long CACHE_VALIDITY_MINUTES = 30;

    private TextView        profileName, profileEmail;
    private TextView        fieldName, fieldEmail, fieldMobile, fieldGender, fieldBloodType;
    private TextView        statDonations, statBloodType, statMember;
    private TextView        avatarInitials;
    private CircleImageView profileAvatar;
    private LinearLayout    incompleteProfileBanner;
    private LinearLayout    saveProfileBtn, logoutBtn, deleteAccountBtn, uploadProgressOverlay;
    private TextView        editNameBtn, editMobileBtn, editGenderBtn, editBloodTypeBtn;
    private TextView        changePasswordBtn, changeAvatarBtn;
    private LinearLayout    aboutBtn;

    private String  currentName = "", currentMobile = "", currentGender = "", currentBloodType = "", currentCreatedAt = "";
    private boolean profileComplete = false;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private UserDataManager userDataManager;

    private boolean profileLoaded = false;
    private int currentUserId = -1;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) uploadProfileImage(uri); });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userDataManager = new UserDataManager(requireContext());
        currentUserId = AuthManager.getUserId(requireContext());

        profileName             = view.findViewById(R.id.profileName);
        profileEmail            = view.findViewById(R.id.profileEmail);
        fieldName               = view.findViewById(R.id.fieldName);
        fieldEmail              = view.findViewById(R.id.fieldEmail);
        fieldMobile             = view.findViewById(R.id.fieldMobile);
        fieldGender             = view.findViewById(R.id.fieldGender);
        fieldBloodType          = view.findViewById(R.id.fieldBloodType);
        statDonations           = view.findViewById(R.id.statDonations);
        statBloodType           = view.findViewById(R.id.statBloodType);
        statMember              = view.findViewById(R.id.statMember);
        avatarInitials          = view.findViewById(R.id.avatarInitials);
        profileAvatar           = view.findViewById(R.id.profileAvatar);
        incompleteProfileBanner = view.findViewById(R.id.incompleteProfileBanner);
        saveProfileBtn          = view.findViewById(R.id.saveProfileBtn);
        logoutBtn               = view.findViewById(R.id.logoutBtn);
        deleteAccountBtn        = view.findViewById(R.id.deleteAccountBtn);
        uploadProgressOverlay   = view.findViewById(R.id.uploadProgressOverlay);
        editNameBtn             = view.findViewById(R.id.editNameBtn);
        editMobileBtn           = view.findViewById(R.id.editMobileBtn);
        editGenderBtn           = view.findViewById(R.id.editGenderBtn);
        editBloodTypeBtn        = view.findViewById(R.id.editBloodTypeBtn);
        changePasswordBtn       = view.findViewById(R.id.changePasswordBtn);
        changeAvatarBtn         = view.findViewById(R.id.changeAvatarBtn);
        aboutBtn                = view.findViewById(R.id.aboutBtn);

        loadFromSession();

        changeAvatarBtn.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        profileAvatar.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        editNameBtn.setOnClickListener(v -> showEditDialog("Full Name", currentName, value -> {
            currentName = value;
            fieldName.setText(value);
            profileName.setText(value);
            avatarInitials.setText(getInitials(value));
        }));

        editMobileBtn.setOnClickListener(v -> showEditDialog("Mobile Number", currentMobile, value -> {
            currentMobile = value;
            fieldMobile.setText(value);
        }));

        editGenderBtn.setOnClickListener(v -> showGenderDialog());
        editBloodTypeBtn.setOnClickListener(v -> showBloodTypeDialog());
        changePasswordBtn.setOnClickListener(v -> showChangePasswordDialog());
        saveProfileBtn.setOnClickListener(v -> { animatePress(saveProfileBtn); saveProfile(); });
        logoutBtn.setOnClickListener(v -> showLogoutConfirm());
        deleteAccountBtn.setOnClickListener(v -> showDeleteAccountConfirm());
        aboutBtn.setOnClickListener(v -> showAboutDialog());

        //  IMPORTANT: Load cache first if not already loaded
        if (!profileLoaded && currentUserId != -1) {
            profileLoaded = true;
            loadProfileFromCache(); // Load cache immediately

            // Then fetch if cache is invalid (optional background fetch)
            if (!userDataManager.isCacheValid(currentUserId, CACHE_VALIDITY_MINUTES)) {
                fetchProfile(false);
            }
        } else if (currentUserId != -1) {
            loadProfileFromCache(); // Still show cache when returning to fragment
        }
    }

    @Override protected void onRefresh() {
        profileLoaded = false;
        fetchProfile(true);
    }

    private void loadFromSession() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        String name=prefs.getString("name",""), bloodType=prefs.getString("blood_type",""), email=prefs.getString("email",""), createdAt=prefs.getString("created_at","");
        if (!name.isEmpty()) { profileName.setText(name); avatarInitials.setText(getInitials(name)); fieldName.setText(name); currentName=name; }
        if (!email.isEmpty()) { profileEmail.setText(email); fieldEmail.setText(email); }
        if (!bloodType.isEmpty()) { statBloodType.setText(bloodType); fieldBloodType.setText(bloodType); currentBloodType=bloodType; }
        currentCreatedAt=createdAt; statMember.setText(calcMemberDuration(createdAt));
    }

    private void fetchProfile(boolean fromRefresh) {
        executor.execute(() -> {
            try {
                String result = httpGet(Constants.PROFILE_ENDPOINT);
                Log.d(TAG, "fetchProfile response length: " + result.length());

                if (result.trim().startsWith("<")) {
                    mainHandler.post(() -> {
                        stopRefreshing();
                        if (fromRefresh) Toast.makeText(requireContext(),"Server error.",Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                if (result.trim().isEmpty()) {
                    mainHandler.post(this::stopRefreshing);
                    return;
                }

                JsonReader reader = new JsonReader(new StringReader(result));
                reader.setLenient(true);
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                if (!"success".equals(json.get("status").getAsString())) {
                    mainHandler.post(this::stopRefreshing);
                    return;
                }

                currentName = s(json, "name");
                currentMobile = s(json, "mobile");
                currentGender = s(json, "gender");
                currentBloodType = s(json, "blood_type");
                currentCreatedAt = s(json, "created_at");
                profileComplete = json.has("profile_complete") && json.get("profile_complete").getAsBoolean();
                String email = s(json, "email");
                String image = s(json, "profile_image");
                int count = json.has("donation_count") ? json.get("donation_count").getAsInt() : 0;

                //  SAVE TO SQLITE - this updates the cache with new donation count
                userDataManager.saveUserData(currentUserId, currentName, email, currentBloodType,
                        currentGender, currentMobile, currentCreatedAt,
                        profileComplete, image);

                //  Also save home data with donation count
                userDataManager.saveHomeData(currentUserId, count, count * 3);

                requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE).edit()
                        .putString("name", currentName)
                        .putString("blood_type", currentBloodType)
                        .putString("email", email)
                        .putString("created_at", currentCreatedAt)
                        .putBoolean("profile_complete", profileComplete)
                        .putInt("donation_count", count)
                        .apply();

                final int fCount = count;
                final boolean fComplete = profileComplete;
                final String fEmail = email, fImage = image;

                mainHandler.post(() -> {
                    if (getView() == null) return;
                    stopRefreshing();
                    profileName.setText(currentName.isEmpty() ? "User" : currentName);
                    profileEmail.setText(fEmail);
                    avatarInitials.setText(getInitials(currentName));
                    fieldName.setText(currentName.isEmpty() ? "—" : currentName);
                    fieldEmail.setText(fEmail.isEmpty() ? "—" : fEmail);
                    fieldMobile.setText(currentMobile.isEmpty() ? "Not set" : currentMobile);
                    fieldGender.setText(currentGender.isEmpty() ? "Not set" : currentGender);
                    fieldBloodType.setText(currentBloodType.isEmpty() ? "Not set" : currentBloodType);
                    statDonations.setText(String.valueOf(fCount));
                    statBloodType.setText(currentBloodType.isEmpty() ? "?" : currentBloodType);
                    statMember.setText(calcMemberDuration(currentCreatedAt));
                    incompleteProfileBanner.setVisibility(fComplete ? View.GONE : View.VISIBLE);
                    if (!fImage.isEmpty()) loadProfileImage(fImage);
                    if (fromRefresh) Toast.makeText(requireContext(), "Profile refreshed", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "fetchProfile error: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    stopRefreshing();
                    if (fromRefresh) Toast.makeText(requireContext(), "Refresh failed.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadProfileFromCache() {
        try {
            JSONObject userData = userDataManager.getUserData(currentUserId);

            if (userData != null) {
                currentName = userData.optString("name", "");
                currentMobile = userData.optString("mobile", "");
                currentGender = userData.optString("gender", "");
                currentBloodType = userData.optString("blood_type", "");
                currentCreatedAt = userData.optString("created_at", "");
                profileComplete = userData.optBoolean("profile_complete", false);
                String email = userData.optString("email", "");
                String image = userData.optString("profile_image", "");

                profileName.setText(currentName.isEmpty() ? "User" : currentName);
                profileEmail.setText(email);
                avatarInitials.setText(getInitials(currentName));
                fieldName.setText(currentName.isEmpty() ? "—" : currentName);
                fieldEmail.setText(email.isEmpty() ? "—" : email);
                fieldMobile.setText(currentMobile.isEmpty() ? "Not set" : currentMobile);
                fieldGender.setText(currentGender.isEmpty() ? "Not set" : currentGender);
                fieldBloodType.setText(currentBloodType.isEmpty() ? "Not set" : currentBloodType);
                statBloodType.setText(currentBloodType.isEmpty() ? "?" : currentBloodType);
                statMember.setText(calcMemberDuration(currentCreatedAt));
                incompleteProfileBanner.setVisibility(profileComplete ? View.GONE : View.VISIBLE);
                if (!image.isEmpty()) loadProfileImage(image);

                // ✅ Load donation count from home data cache
                JSONObject homeData = userDataManager.getHomeData(currentUserId);
                if (homeData != null) {
                    int donationCount = homeData.optInt("donation_count", 0);
                    statDonations.setText(String.valueOf(donationCount));
                    Log.d(TAG, "✅ Donation count loaded from cache: " + donationCount);
                }

                Log.d(TAG, "✅ Profile loaded from cache instantly");
            }
        } catch (Exception e) {
            Log.e(TAG, "Profile cache load error: " + e.getMessage());
        }
    }

    private void loadProfileImage(String base64) {
        executor.execute(() -> {
            try {
                byte[] bytes=Base64.decode(base64.replaceAll("\\s+",""),Base64.DEFAULT);
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                mainHandler.post(() -> { if (getView()==null||bitmap==null) return; profileAvatar.setImageBitmap(bitmap); profileAvatar.setVisibility(View.VISIBLE); avatarInitials.setVisibility(View.GONE); });
            } catch (Exception e) { Log.e(TAG,"loadProfileImage: "+e.getMessage()); }
        });
    }

    private void uploadProfileImage(Uri uri) {
        showUploadProgress(true);
        executor.execute(() -> {
            try {
                InputStream is=requireActivity().getContentResolver().openInputStream(uri);
                Bitmap bitmap=resizeBitmap(BitmapFactory.decodeStream(is),512);
                ByteArrayOutputStream baos=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG,80,baos);
                String base64=Base64.encodeToString(baos.toByteArray(),Base64.NO_WRAP);
                JsonObject body=new JsonObject(); body.addProperty("action","upload_image"); body.addProperty("image",base64);
                String result=httpPost(Constants.PROFILE_ENDPOINT,body.toString());
                JsonReader jr=new JsonReader(new StringReader(result)); jr.setLenient(true);
                String status=s(JsonParser.parseReader(jr).getAsJsonObject(),"status");
                final Bitmap fb=bitmap;
                mainHandler.post(() -> { showUploadProgress(false);
                    if ("success".equals(status)) { profileAvatar.setImageBitmap(fb); profileAvatar.setVisibility(View.VISIBLE); avatarInitials.setVisibility(View.GONE); Toast.makeText(requireContext(),"Photo updated ✓",Toast.LENGTH_SHORT).show(); }
                    else Toast.makeText(requireContext(),"Upload failed.",Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) {
                Log.e(TAG,"uploadProfileImage: "+e.getMessage(),e);
                mainHandler.post(() -> { showUploadProgress(false); Toast.makeText(requireContext(),"Upload error: "+e.getMessage(),Toast.LENGTH_SHORT).show(); });
            }
        });
    }

    private Bitmap resizeBitmap(Bitmap src, int maxSize) {
        int w=src.getWidth(), h=src.getHeight();
        if (w<=maxSize&&h<=maxSize) return src;
        float ratio=Math.min((float)maxSize/w,(float)maxSize/h);
        return Bitmap.createScaledBitmap(src,Math.round(w*ratio),Math.round(h*ratio),true);
    }

    private void showUploadProgress(boolean show) { if (uploadProgressOverlay!=null) uploadProgressOverlay.setVisibility(show?View.VISIBLE:View.GONE); }

    private void saveProfile() {
        executor.execute(() -> {
            try {
                JsonObject body=new JsonObject(); body.addProperty("action","update");
                body.addProperty("name",currentName); body.addProperty("mobile",currentMobile);
                body.addProperty("gender",currentGender); body.addProperty("blood_type",currentBloodType);
                String result=httpPost(Constants.PROFILE_ENDPOINT,body.toString());
                JsonReader jr=new JsonReader(new StringReader(result)); jr.setLenient(true);
                JsonObject json=JsonParser.parseReader(jr).getAsJsonObject();
                String status=s(json,"status"); boolean nowComplete=json.has("profile_complete")&&json.get("profile_complete").getAsBoolean();

                userDataManager.saveUserData(currentUserId, currentName, "", currentBloodType, currentGender, currentMobile, currentCreatedAt, nowComplete, "");

                requireActivity().getSharedPreferences("UserSession",Context.MODE_PRIVATE).edit()
                        .putString("name",currentName)
                        .putString("blood_type",currentBloodType)
                        .putBoolean("profile_complete",nowComplete)
                        .apply();

                mainHandler.post(() -> { if (getView()==null) return;
                    if ("success".equals(status)) { profileComplete=nowComplete; incompleteProfileBanner.setVisibility(nowComplete?View.GONE:View.VISIBLE); statBloodType.setText(currentBloodType.isEmpty()?"?":currentBloodType);
                        Toast.makeText(requireContext(), nowComplete?"Profile saved! QR scanning unlocked 🎉":"Profile saved. Add all fields to unlock scanning.", Toast.LENGTH_LONG).show(); }
                    else Toast.makeText(requireContext(), json.has("message")?json.get("message").getAsString():"Save failed.", Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) { Log.e(TAG,"saveProfile: "+e.getMessage(),e); mainHandler.post(() -> Toast.makeText(requireContext(),"Connection error.",Toast.LENGTH_SHORT).show()); }
        });
    }

    private void showAboutDialog() {
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCancelable(true);
        LinearLayout root=new LinearLayout(requireContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(24),dp(24),dp(32)); root.setBackgroundResource(android.R.color.transparent);
        LinearLayout card=new LinearLayout(requireContext()); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(20),dp(24),dp(20),dp(24));
        GradientDrawable cardBg=new GradientDrawable(); cardBg.setColor(Color.WHITE); cardBg.setCornerRadius(dp(20)); card.setBackground(cardBg);
        TextView appIcon=new TextView(requireContext()); appIcon.setText("🩸"); appIcon.setTextSize(48); appIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); iconP.bottomMargin=dp(4); appIcon.setLayoutParams(iconP);
        TextView appName=new TextView(requireContext()); appName.setText("OneDrop"); appName.setTextSize(22); appName.setTextColor(Color.parseColor("#DC143C")); appName.setGravity(Gravity.CENTER); appName.setTypeface(null,android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameP=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); nameP.bottomMargin=dp(4); appName.setLayoutParams(nameP);
        TextView version=new TextView(requireContext()); version.setText("Version 1.0.0"); version.setTextSize(12); version.setTextColor(Color.parseColor("#AAAAAA")); version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams verP=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); verP.bottomMargin=dp(16); version.setLayoutParams(verP);
        TextView desc=new TextView(requireContext()); desc.setText("OneDrop connects blood donors with those in need, making the donation process seamless and rewarding. Every drop counts. 🩸"); desc.setTextSize(13); desc.setTextColor(Color.parseColor("#555555")); desc.setGravity(Gravity.CENTER); desc.setLineSpacing(dp(4),1f);
        LinearLayout.LayoutParams descP=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); descP.topMargin=dp(16); descP.bottomMargin=dp(16); desc.setLayoutParams(descP);
        TextView devLabel=new TextView(requireContext()); devLabel.setText("DEVELOPED BY"); devLabel.setTextSize(9); devLabel.setTextColor(Color.parseColor("#AAAAAA")); devLabel.setGravity(Gravity.CENTER); devLabel.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams devLabelP=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); devLabelP.topMargin=dp(16); devLabelP.bottomMargin=dp(8); devLabel.setLayoutParams(devLabelP);
        TextView devName=new TextView(requireContext()); devName.setText("Thilan Kalhara"); devName.setTextSize(17); devName.setTextColor(Color.parseColor("#1A1A1A")); devName.setGravity(Gravity.CENTER); devName.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView devEmail=new TextView(requireContext()); devEmail.setText("✉️  thilankalhara8@gmail.com"); devEmail.setTextSize(12); devEmail.setTextColor(Color.parseColor("#DC143C")); devEmail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emailP=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); emailP.topMargin=dp(6); emailP.bottomMargin=dp(16); devEmail.setLayoutParams(emailP);
        devEmail.setOnClickListener(v -> openUrl("mailto:thilankalhara8@gmail.com"));
        LinearLayout socialRow=new LinearLayout(requireContext()); socialRow.setOrientation(LinearLayout.HORIZONTAL); socialRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams socialP=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); socialP.bottomMargin=dp(20); socialRow.setLayoutParams(socialP);
        TextView linkedInBtn=makeSocialButton("💼 LinkedIn","#0A66C2"); linkedInBtn.setOnClickListener(v -> openUrl("https://www.linkedin.com/in/thilan-kalhara-06a9b723a/"));
        TextView coffeeBtn=makeSocialButton("☕ Support","#FFDD00"); coffeeBtn.setTextColor(Color.parseColor("#1A1A1A")); coffeeBtn.setOnClickListener(v -> openUrl("https://ko-fi.com/thilankalhara"));
        LinearLayout.LayoutParams btnP=new LinearLayout.LayoutParams(0,dp(40),1f); btnP.setMargins(0,0,dp(8),0); linkedInBtn.setLayoutParams(btnP);
        coffeeBtn.setLayoutParams(new LinearLayout.LayoutParams(0,dp(40),1f));
        socialRow.addView(linkedInBtn); socialRow.addView(coffeeBtn);
        TextView closeBtn=new TextView(requireContext()); closeBtn.setText("Close"); closeBtn.setTextSize(14); closeBtn.setTextColor(Color.WHITE); closeBtn.setGravity(Gravity.CENTER);
        GradientDrawable closeBg=new GradientDrawable(); closeBg.setColor(Color.parseColor("#DC143C")); closeBg.setCornerRadius(dp(50)); closeBtn.setBackground(closeBg);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        card.addView(appIcon); card.addView(appName); card.addView(version); card.addView(makeDivider()); card.addView(desc); card.addView(makeDivider()); card.addView(devLabel); card.addView(devName); card.addView(devEmail); card.addView(socialRow); card.addView(closeBtn);
        root.addView(card); dialog.setContentView(root);
        Window window=dialog.getWindow();
        if (window!=null) { window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); window.setGravity(Gravity.BOTTOM); window.getAttributes().windowAnimations=android.R.style.Animation_InputMethod; }
        dialog.show();
    }

    private TextView makeSocialButton(String text, String bgColor) {
        TextView btn=new TextView(requireContext()); btn.setText(text); btn.setTextSize(12); btn.setTextColor(Color.WHITE); btn.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.parseColor(bgColor)); bg.setCornerRadius(dp(50)); btn.setBackground(bg); return btn;
    }

    private View makeDivider() {
        View div=new View(requireContext());
        div.setBackgroundColor(Color.parseColor("#F0F0F0"));
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1)));
        return div;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(),"Cannot open link.",Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteAccountConfirm() {
        new AlertDialog.Builder(requireContext()).setTitle("⚠️ Delete Account").setMessage("This will permanently delete your account and all donation history. This cannot be undone.\n\nAre you sure?")
                .setPositiveButton("Continue",(d,w)->showDeletePasswordConfirm()).setNegativeButton("Cancel",null).show();
    }

    private void showDeletePasswordConfirm() {
        EditText passwordInput=new EditText(requireContext());
        passwordInput.setHint("Enter your password to confirm");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(dp(16),dp(12),dp(16),dp(12));
        new AlertDialog.Builder(requireContext()).setTitle("Confirm Password").setView(passwordInput)
                .setPositiveButton("Delete Forever",(d,w) -> {
                    String pw=passwordInput.getText().toString().trim();
                    if (pw.isEmpty()) {
                        Toast.makeText(requireContext(),"Password is required.",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    deleteAccount(pw);
                })
                .setNegativeButton("Cancel",null).show();
    }

    private void deleteAccount(String password) {
        executor.execute(() -> {
            try {
                JsonObject body=new JsonObject();
                body.addProperty("action","delete_account");
                body.addProperty("password",password);
                String result=httpPost(Constants.PROFILE_ENDPOINT,body.toString());
                JsonReader jr=new JsonReader(new StringReader(result));
                jr.setLenient(true);
                JsonObject json=JsonParser.parseReader(jr).getAsJsonObject();
                String status=s(json,"status");
                mainHandler.post(() -> {
                    if (getView()==null) return;
                    if ("success".equals(status)) {
                        Toast.makeText(requireContext(),"Account deleted.",Toast.LENGTH_SHORT).show();
                        AuthManager.clearSession(requireContext());
                        requireActivity().getSharedPreferences("UserSession",Context.MODE_PRIVATE).edit().clear().apply();
                        requireActivity().getSharedPreferences("OneDropPrefs",Context.MODE_PRIVATE).edit().putBoolean("remembered",false).apply();

                        if (currentUserId != -1) userDataManager.clearAllData(currentUserId);

                        Intent i=new Intent(requireActivity(),SignInActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    } else if ("wrong_password".equals(status)) {
                        Toast.makeText(requireContext(),"Incorrect password.",Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), json.has("message")?json.get("message").getAsString():"Failed.",Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG,"deleteAccount: "+e.getMessage(),e);
                mainHandler.post(() -> Toast.makeText(requireContext(),"Connection error.",Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showEditDialog(String label, String current, OnValueSaved callback) {
        EditText input=new EditText(requireContext());
        input.setText(current);
        input.setSelection(current.length());
        input.setHint(label);
        new AlertDialog.Builder(requireContext()).setTitle("Edit "+label).setView(input)
                .setPositiveButton("Save",(d,w) -> {
                    String v=input.getText().toString().trim();
                    if (!v.isEmpty()) callback.onSaved(v);
                })
                .setNegativeButton("Cancel",null).show();
    }

    private void showGenderDialog() {
        String[] options={"Male","Female","Other"};
        new AlertDialog.Builder(requireContext()).setTitle("Select Gender")
                .setItems(options,(d,i) -> {
                    currentGender=options[i];
                    fieldGender.setText(currentGender);
                })
                .setNegativeButton("Cancel",null).show();
    }

    private void showBloodTypeDialog() {
        String[] types={"A+","A-","B+","B-","O+","O-","AB+","AB-"};
        new AlertDialog.Builder(requireContext()).setTitle("Select Blood Type")
                .setItems(types,(d,i) -> {
                    currentBloodType=types[i];
                    fieldBloodType.setText(currentBloodType);
                    statBloodType.setText(currentBloodType);
                })
                .setNegativeButton("Cancel",null).show();
    }

    private void showChangePasswordDialog() {
        View dialogView=LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password,null);
        EditText oldPw=dialogView.findViewById(R.id.oldPasswordField),
                newPw=dialogView.findViewById(R.id.newPasswordField),
                confPw=dialogView.findViewById(R.id.confirmPasswordField);
        AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle("Change Password").setView(dialogView).setPositiveButton("Change",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String op=oldPw.getText().toString().trim(),
                    np=newPw.getText().toString().trim(),
                    cp=confPw.getText().toString().trim();
            if (op.isEmpty()) { oldPw.setError("Enter current password"); return; }
            if (np.length()<6) { newPw.setError("Min 6 characters"); return; }
            if (!np.equals(cp)) { confPw.setError("Passwords do not match"); return; }
            dialog.dismiss();
            submitPasswordChange(op,np);
        }));
        dialog.show();
    }

    private void submitPasswordChange(String oldPw, String newPw) {
        executor.execute(() -> {
            try {
                JsonObject body=new JsonObject();
                body.addProperty("action","change_password");
                body.addProperty("old_password",oldPw);
                body.addProperty("new_password",newPw);
                String result=httpPost(Constants.PROFILE_ENDPOINT,body.toString());
                JsonReader jr=new JsonReader(new StringReader(result));
                jr.setLenient(true);
                JsonObject json=JsonParser.parseReader(jr).getAsJsonObject();
                String status=s(json,"status");
                mainHandler.post(() -> {
                    if (getView()==null) return;
                    if ("success".equals(status)) {
                        Toast.makeText(requireContext(),"Password changed ✓",Toast.LENGTH_SHORT).show();
                    } else if ("wrong_password".equals(status)) {
                        Toast.makeText(requireContext(),"Current password is incorrect.",Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), json.has("message")?json.get("message").getAsString():"Error.",Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(requireContext(),"Connection error.",Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showLogoutConfirm() {
        new AlertDialog.Builder(requireContext()).setTitle("Log Out").setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out",(d,w) -> {
                    AuthManager.clearSession(requireContext());
                    requireActivity().getSharedPreferences("UserSession",Context.MODE_PRIVATE).edit().clear().apply();
                    requireActivity().getSharedPreferences("OneDropPrefs",Context.MODE_PRIVATE).edit().putBoolean("remembered",false).apply();

                    if (currentUserId != -1) userDataManager.clearAllData(currentUserId);

                    Intent i=new Intent(requireActivity(),SignInActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }).setNegativeButton("Cancel",null).show();
    }

    private void animatePress(View v) {
        android.view.animation.ScaleAnimation scale=new android.view.animation.ScaleAnimation(1f,0.97f,1f,0.97f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);
        scale.setDuration(100);
        scale.setRepeatCount(1);
        scale.setRepeatMode(Animation.REVERSE);
        v.startAnimation(scale);
    }

    private String calcMemberDuration(String createdAt) {
        if (createdAt==null||createdAt.isEmpty()) return "New";
        try {
            String[] parts=createdAt.split(" ")[0].split("-");
            Calendar reg=Calendar.getInstance();
            reg.set(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])-1,Integer.parseInt(parts[2]));
            Calendar now=Calendar.getInstance();
            int total=(now.get(Calendar.YEAR)-reg.get(Calendar.YEAR))*12+(now.get(Calendar.MONTH)-reg.get(Calendar.MONTH));
            if (total<1) return "New";
            if (total<12) return total+"mo";
            int y=total/12,m=total%12;
            return m>0?y+"yr "+m+"mo":y+"yr";
        } catch (Exception e) {
            return "New";
        }
    }

    private String getInitials(String name) {
        if (name==null||name.isEmpty()) return "?";
        String[] parts=name.trim().split("\\s+");
        if (parts.length>=2) return (""+parts[0].charAt(0)+parts[1].charAt(0)).toUpperCase();
        return name.substring(0,Math.min(2,name.length())).toUpperCase();
    }

    private int dp(int dp) {
        return Math.round(dp*requireContext().getResources().getDisplayMetrics().density);
    }

    private String s(JsonObject json, String key) {
        return json.has(key)&&!json.get(key).isJsonNull()?json.get(key).getAsString():"";
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn=(HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        AuthManager.attachToken(requireContext(), conn);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.connect();
        int code=conn.getResponseCode();
        Log.d(TAG,"GET "+urlStr+" → HTTP "+code);
        BufferedReader r=new BufferedReader(new InputStreamReader(code<400?conn.getInputStream():conn.getErrorStream()));
        StringBuilder sb=new StringBuilder();
        String line;
        while ((line=r.readLine())!=null) sb.append(line);
        r.close();
        conn.disconnect();
        return sb.toString();
    }

    private String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection conn=(HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type","application/json");
        AuthManager.attachToken(requireContext(), conn);
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (DataOutputStream dos=new DataOutputStream(conn.getOutputStream())) {
            dos.write(body.getBytes("UTF-8"));
        }
        int code=conn.getResponseCode();
        Log.d(TAG,"POST "+urlStr+" → HTTP "+code);
        BufferedReader r=new BufferedReader(new InputStreamReader(code<400?conn.getInputStream():conn.getErrorStream()));
        StringBuilder sb=new StringBuilder();
        String line;
        while ((line=r.readLine())!=null) sb.append(line);
        r.close();
        conn.disconnect();
        return sb.toString();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        if (userDataManager != null) userDataManager.close();
    }

    interface OnValueSaved {
        void onSaved(String value);
    }
}