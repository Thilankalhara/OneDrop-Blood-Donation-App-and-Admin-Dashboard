package com.example.onedrop.Activity.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.example.onedrop.Activity.Database.UserDataManager;
import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DonateFragment extends BaseRefreshFragment {

    private static final String TAG            = "DonateFragment";
    private static final String PREFS_HIDDEN   = "hidden_donations";
    private static final String KEY_HIDDEN_IDS = "hidden_ids";

    private TextView     statBloodDonations, statLivesHelped, statContributed;
    private CardView     bloodDonationCard, moneyDonationCard;
    private TextView     donateBloodBtn, donateMoneyBtn;
    private TextView     moneyHistoryLoading;
    private LinearLayout moneyHistoryEmpty, moneyHistoryContainer;

    private ExecutorService executor;
    private final Handler   mainHandler = new Handler(Looper.getMainLooper());

    private UserDataManager userDataManager;

    //  ADD THIS: Flag to prevent reload on every view
    private boolean dataLoadedThisSession = false;
    private int currentUserId = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_donation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executor = Executors.newSingleThreadExecutor();
        userDataManager = new UserDataManager(requireContext());
        currentUserId = AuthManager.getUserId(requireContext());

        statBloodDonations    = view.findViewById(R.id.statBloodDonations);
        statLivesHelped       = view.findViewById(R.id.statLivesHelped);
        statContributed       = view.findViewById(R.id.statContributed);
        bloodDonationCard     = view.findViewById(R.id.bloodDonationCard);
        moneyDonationCard     = view.findViewById(R.id.moneyDonationCard);
        donateBloodBtn        = view.findViewById(R.id.donateBloodBtn);
        donateMoneyBtn        = view.findViewById(R.id.donateMoneyBtn);
        moneyHistoryLoading   = view.findViewById(R.id.moneyHistoryLoading);
        moneyHistoryEmpty     = view.findViewById(R.id.moneyHistoryEmpty);
        moneyHistoryContainer = view.findViewById(R.id.moneyHistoryContainer);

        donateBloodBtn.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        .replace(R.id.fragmentContainer, new BloodDonationMapFragment())
                        .addToBackStack(null).commit());

        donateMoneyBtn.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        .replace(R.id.fragmentContainer, new MoneyDonationFragment())
                        .addToBackStack(null).commit());

        loadSessionStats();

        // ✅ IMPORTANT: Load cache first if not already loaded
        if (!dataLoadedThisSession && currentUserId != -1) {
            dataLoadedThisSession = true;
            loadDonationDataFromCache(currentUserId);

            // Then fetch if internet available (background)
            if (isConnected()) {
                mainHandler.postDelayed(this::fetchDonationData, 500);
            }
        } else if (currentUserId != -1 && isConnected()) {
            fetchDonationData();
        }
    }

    @Override protected void onRefresh() {
        dataLoadedThisSession = false; // ✅ Reset on manual refresh
        fetchDonationData();
    }

    private void loadSessionStats() {
        int donations = requireActivity()
                .getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                .getInt("donation_count", 0);
        if (donations > 0) {
            statBloodDonations.setText(String.valueOf(donations));
            statLivesHelped.setText(String.valueOf(donations * 3));
        }
    }

    private void fetchDonationData() {
        if (currentUserId == -1) { stopRefreshing(); return; }

        if (executor == null || executor.isShutdown()) executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                if (!isConnected()) {
                    mainHandler.post(() -> {
                        if (getView() == null) return;
                        stopRefreshing();
                        loadDonationDataFromCache(currentUserId);
                    });
                    return;
                }

                String     homeResult  = httpGet(Constants.HOME_DATA_ENDPOINT);
                JSONObject homeJson    = new JSONObject(homeResult);
                int        bloodCount  = homeJson.optInt("donation_count", 0);
                int        lives       = homeJson.optInt("lives_saved",    0);

                String     moneyResult = httpGet(Constants.MONETARY_DONATION_ENDPOINT);
                JSONObject moneyJson   = new JSONObject(moneyResult);
                JSONArray  history     = moneyJson.optJSONArray("history");
                double     totalAmount = moneyJson.optDouble("total_amount", 0.0);

                //  SAVE TO SQLITE
                userDataManager.saveHomeData(currentUserId, bloodCount, lives);
                if (history != null) {
                    userDataManager.saveDonations(currentUserId, history.toString());
                }

                requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                        .edit().putInt("donation_count", bloodCount).apply();

                final int fBlood = bloodCount, fLives = lives;
                final double fTotal = totalAmount;
                final JSONArray fHistory = history;

                mainHandler.post(() -> {
                    if (getView() == null) return;
                    stopRefreshing();
                    statBloodDonations.setText(String.valueOf(fBlood));
                    statLivesHelped.setText(String.valueOf(fLives));
                    statContributed.setText(formatAmount(fTotal));
                    renderMoneyHistory(fHistory);
                });

            } catch (Exception e) {
                Log.e(TAG, "fetchDonationData error: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    stopRefreshing();
                    loadDonationDataFromCache(currentUserId);
                });
            }
        });
    }

    private void loadDonationDataFromCache(int userId) {
        try {
            JSONObject homeData = userDataManager.getHomeData(userId);
            JSONArray donations = userDataManager.getDonations(userId);

            if (homeData != null) {
                int blood = homeData.optInt("donation_count", 0);
                int lives = homeData.optInt("lives_saved", 0);

                statBloodDonations.setText(String.valueOf(blood));
                statLivesHelped.setText(String.valueOf(lives));

                double total = 0;
                for (int i = 0; i < donations.length(); i++) {
                    total += donations.getJSONObject(i).optDouble("amount", 0);
                }
                statContributed.setText(formatAmount(total));
                renderMoneyHistory(donations);

                Log.d(TAG, "✅ Donation data loaded from cache instantly");
            }
        } catch (Exception e) {
            Log.e(TAG, "Cache load error: " + e.getMessage());
            moneyHistoryEmpty.setVisibility(View.VISIBLE);
            moneyHistoryContainer.setVisibility(View.GONE);
        }
    }

    private void renderMoneyHistory(JSONArray history) {
        moneyHistoryLoading.setVisibility(View.GONE);
        moneyHistoryContainer.removeAllViews();
        Set<String> hiddenIds = getHiddenIds();

        if (history == null || history.length() == 0) {
            moneyHistoryEmpty.setVisibility(View.VISIBLE);
            moneyHistoryContainer.setVisibility(View.GONE);
            return;
        }

        int visibleCount = 0;
        for (int i = 0; i < history.length(); i++) {
            try {
                JSONObject item  = history.getJSONObject(i);
                int        donId = item.optInt("monetary_donation_id", -1);
                if (hiddenIds.contains(String.valueOf(donId))) continue;
                double amount = item.optDouble("amount", 0.0);
                String date   = item.optString("donated_at", "");
                String status = item.optString("payment_status", "SUCCESS");
                if (date.length() > 10) date = date.substring(0, 10);
                moneyHistoryContainer.addView(buildHistoryRow(donId, amount, date, status));
                visibleCount++;
            } catch (Exception e) { Log.e(TAG, "row error: " + e.getMessage()); }
        }

        if (visibleCount == 0) {
            moneyHistoryEmpty.setVisibility(View.VISIBLE);
            moneyHistoryContainer.setVisibility(View.GONE);
        } else {
            moneyHistoryEmpty.setVisibility(View.GONE);
            moneyHistoryContainer.setVisibility(View.VISIBLE);
        }
    }

    private View buildHistoryRow(int donationId, double amount, String date, String status) {
        final LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wParams.setMargins(0, 0, 0, dp(10));
        wrapper.setLayoutParams(wParams);

        CardView card = new CardView(requireContext());
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setRadius(dp(14)); card.setCardElevation(0);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView icon = new TextView(requireContext());
        icon.setText("💰"); icon.setTextSize(20);
        icon.setGravity(android.view.Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.parseColor("#FFF3F3"));
        iconBg.setCornerRadius(dp(10));
        icon.setBackground(iconBg);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconP.rightMargin = dp(14); icon.setLayoutParams(iconP);

        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(requireContext());
        titleTv.setText("Monetary Donation"); titleTv.setTextSize(14);
        titleTv.setTextColor(Color.parseColor("#1A1A1A"));
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView dateTv = new TextView(requireContext());
        dateTv.setText("📅 " + date); dateTv.setTextSize(11);
        dateTv.setTextColor(Color.parseColor("#AAAAAA"));
        LinearLayout.LayoutParams dateP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateP.topMargin = dp(2); dateTv.setLayoutParams(dateP);

        TextView swipeHint = new TextView(requireContext());
        swipeHint.setText("← swipe to remove"); swipeHint.setTextSize(9);
        swipeHint.setTextColor(Color.parseColor("#CCCCCC"));
        LinearLayout.LayoutParams hintP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintP.topMargin = dp(2); swipeHint.setLayoutParams(hintP);

        textCol.addView(titleTv); textCol.addView(dateTv); textCol.addView(swipeHint);

        LinearLayout rightCol = new LinearLayout(requireContext());
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);

        TextView amountTv = new TextView(requireContext());
        amountTv.setText(formatAmount(amount)); amountTv.setTextSize(16);
        amountTv.setTextColor(Color.parseColor("#DC143C"));
        amountTv.setTypeface(null, android.graphics.Typeface.BOLD);
        amountTv.setGravity(android.view.Gravity.END);

        TextView statusTv = new TextView(requireContext());
        statusTv.setTextSize(10); statusTv.setGravity(android.view.Gravity.CENTER);
        statusTv.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusP.topMargin = dp(4); statusP.gravity = android.view.Gravity.END;
        statusTv.setLayoutParams(statusP);

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp(50));
        if ("SUCCESS".equalsIgnoreCase(status)) {
            statusBg.setColor(Color.parseColor("#E8F5E9")); statusTv.setTextColor(Color.parseColor("#2E7D32")); statusTv.setText("✓ Paid");
        } else if ("PENDING".equalsIgnoreCase(status)) {
            statusBg.setColor(Color.parseColor("#FFF8E1")); statusTv.setTextColor(Color.parseColor("#F57F17")); statusTv.setText("⏳ Pending");
        } else {
            statusBg.setColor(Color.parseColor("#FFEBEE")); statusTv.setTextColor(Color.parseColor("#C62828")); statusTv.setText("✗ Failed");
        }
        statusTv.setBackground(statusBg);

        rightCol.addView(amountTv); rightCol.addView(statusTv);
        row.addView(icon); row.addView(textCol); row.addView(rightCol);
        card.addView(row); wrapper.addView(card);

        attachSwipeToHide(wrapper, donationId);
        return wrapper;
    }

    private void attachSwipeToHide(final View rowView, final int donationId) {
        final float[]   startX  = {0f};
        final boolean[] swiping = {false};
        rowView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX(); swiping[0] = true; break;
                case MotionEvent.ACTION_MOVE:
                    if (!swiping[0]) break;
                    float dx = event.getRawX() - startX[0];
                    if (dx > 0) { rowView.setTranslationX(dx); rowView.setAlpha(Math.max(0f, 1f - dx / 400f)); }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!swiping[0]) break;
                    swiping[0] = false;
                    if (event.getRawX() - startX[0] > dp(100)) animateAndHide(rowView, donationId);
                    else rowView.animate().translationX(0f).alpha(1f).setDuration(200).start();
                    break;
            }
            return true;
        });
    }

    private void animateAndHide(final View rowView, final int donationId) {
        int screenWidth = requireContext().getResources().getDisplayMetrics().widthPixels;
        rowView.animate().translationX(screenWidth).alpha(0f).setDuration(280)
                .withEndAction(() -> {
                    android.animation.ValueAnimator collapse = android.animation.ValueAnimator.ofInt(rowView.getHeight(), 0);
                    collapse.setDuration(180);
                    collapse.addUpdateListener(a -> { rowView.getLayoutParams().height = (int) a.getAnimatedValue(); rowView.requestLayout(); });
                    collapse.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(android.animation.Animator animation) {
                            if (moneyHistoryContainer == null) return;
                            saveHiddenId(donationId);
                            moneyHistoryContainer.removeView(rowView);
                            if (moneyHistoryContainer.getChildCount() == 0) {
                                moneyHistoryEmpty.setVisibility(View.VISIBLE);
                                moneyHistoryContainer.setVisibility(View.GONE);
                            }
                        }
                    });
                    collapse.start();
                }).start();
    }

    private Set<String> getHiddenIds() {
        Set<String> stored = requireActivity()
                .getSharedPreferences(PREFS_HIDDEN, Context.MODE_PRIVATE)
                .getStringSet(KEY_HIDDEN_IDS, new HashSet<>());
        return new HashSet<>(stored);
    }

    private void saveHiddenId(int donationId) {
        Set<String> hidden = getHiddenIds();
        hidden.add(String.valueOf(donationId));
        requireActivity().getSharedPreferences(PREFS_HIDDEN, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_HIDDEN_IDS, hidden).apply();
    }

    private String formatAmount(double amount) {
        if (amount == 0) return "$0";
        if (amount == (long) amount) return "$" + String.format("%,d", (long) amount);
        return "$" + String.format("%,.2f", amount);
    }

    private int dp(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private boolean isConnected() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.net.Network net = cm.getActiveNetwork();
            if (net == null) return false;
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR));
        }
        return false;
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        AuthManager.attachToken(requireContext(), conn);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.connect();
        int code = conn.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                code < 400 ? conn.getInputStream() : conn.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        conn.disconnect();
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
        if (userDataManager != null) userDataManager.close();
    }
}