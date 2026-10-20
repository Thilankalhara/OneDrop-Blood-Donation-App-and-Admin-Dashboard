package com.example.onedrop.Activity.Fragment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import com.stripe.android.PaymentConfiguration;
import com.stripe.android.Stripe;
import com.stripe.android.model.CardParams;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.PaymentMethodCreateParams;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoneyDonationFragment extends Fragment {

    private static final String TAG = "MoneyDonationFragment";

    // ✅ Your publishable key from Stripe dashboard — configure before build
    private static final String STRIPE_PUBLISHABLE_KEY = "YOUR_STRIPE_PUBLISHABLE_KEY";

    private static final double[] PRESET_AMOUNTS = {1.0, 5.0, 10.0, 50.0};
    private static final String[] PRESET_DESCS   = {
            "Funds test kit for 1 donor",
            "Funds blood drive supplies for 1 donor",
            "Covers full donor care for 1 session",
            "Sponsors a full blood drive"
    };

    private double  selectedAmount = 5.0;
    private boolean isProcessing   = false;

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView       selectedAmountTv, selectedAmountDesc;
    private LinearLayout   preset1, preset2, preset3, preset4;
    private EditText       customAmountEt;
    private TextView       clearCustomBtn;
    private TextView       summaryAmountTv, summaryFeeTv, summaryTotalTv;
    private LinearLayout   donateBtn;
    private TextView       donateBtnText, moneyBackBtn;
    private LinearLayout[] presets;
    private EditText       cardNumberEt, cardExpiryEt, cardCvcEt;
    private TextView       cardBrandTv;

    private ExecutorService executor;
    private final Handler   mainHandler = new Handler(Looper.getMainLooper());
    private Stripe          stripe;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_money_donation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executor = Executors.newSingleThreadExecutor();

        // ✅ Init Stripe SDK with publishable key
        PaymentConfiguration.init(requireContext(), STRIPE_PUBLISHABLE_KEY);
        stripe = new Stripe(requireContext(), STRIPE_PUBLISHABLE_KEY);

        // ── Bind views ─────────────────────────────────────────────────────
        selectedAmountTv   = view.findViewById(R.id.selectedAmountTv);
        selectedAmountDesc = view.findViewById(R.id.selectedAmountDesc);
        preset1            = view.findViewById(R.id.preset1);
        preset2            = view.findViewById(R.id.preset2);
        preset3            = view.findViewById(R.id.preset3);
        preset4            = view.findViewById(R.id.preset4);
        customAmountEt     = view.findViewById(R.id.customAmountEt);
        clearCustomBtn     = view.findViewById(R.id.clearCustomBtn);
        summaryAmountTv    = view.findViewById(R.id.summaryAmountTv);
        summaryFeeTv       = view.findViewById(R.id.summaryFeeTv);
        summaryTotalTv     = view.findViewById(R.id.summaryTotalTv);
        donateBtn          = view.findViewById(R.id.donateBtn);
        donateBtnText      = view.findViewById(R.id.donateBtnText);
        moneyBackBtn       = view.findViewById(R.id.moneyBackBtn);
        cardNumberEt       = view.findViewById(R.id.cardNumberEt);
        cardExpiryEt       = view.findViewById(R.id.cardExpiryEt);
        cardCvcEt          = view.findViewById(R.id.cardCvcEt);
        cardBrandTv        = view.findViewById(R.id.cardBrandTv);
        presets            = new LinearLayout[]{preset1, preset2, preset3, preset4};

        // ── Back ───────────────────────────────────────────────────────────
        moneyBackBtn.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
            else requireActivity().onBackPressed();
        });

        // ── Preset clicks ──────────────────────────────────────────────────
        for (int i = 0; i < presets.length; i++) {
            final int idx = i;
            presets[i].setOnClickListener(v -> { customAmountEt.setText(""); selectPreset(idx); });
        }

        // ── Custom amount ──────────────────────────────────────────────────
        customAmountEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                String text = s.toString().trim();
                clearCustomBtn.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                if (!text.isEmpty()) {
                    try {
                        double val = Double.parseDouble(text);
                        selectedAmount = val;
                        deselectAllPresets();
                        updateHeader(val, "Custom donation amount");
                        updateSummary(val);
                    } catch (NumberFormatException ignored) {}
                } else {
                    selectPreset(1);
                }
            }
        });

        clearCustomBtn.setOnClickListener(v -> { customAmountEt.setText(""); selectPreset(1); });

        // ── Card number: auto-space every 4 digits + brand detection ───────
        cardNumberEt.addTextChangedListener(new TextWatcher() {
            private boolean formatting = false;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (formatting) return;
                formatting = true;
                String digits = s.toString().replaceAll("[^0-9]", "");
                updateCardBrand(digits);
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length() && i < 16; i++) {
                    if (i > 0 && i % 4 == 0) formatted.append(' ');
                    formatted.append(digits.charAt(i));
                }
                cardNumberEt.setText(formatted.toString());
                cardNumberEt.setSelection(formatted.length());
                formatting = false;
            }
        });

        // ── Expiry: auto-insert slash after MM ─────────────────────────────
        cardExpiryEt.addTextChangedListener(new TextWatcher() {
            private boolean formatting = false;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (formatting || before > count) return;
                formatting = true;
                String digits = s.toString().replaceAll("[^0-9]", "");
                String result = digits;
                if (digits.length() >= 2)
                    result = digits.substring(0, 2) + "/" +
                            (digits.length() > 2 ? digits.substring(2, Math.min(digits.length(), 4)) : "");
                cardExpiryEt.setText(result);
                cardExpiryEt.setSelection(result.length());
                formatting = false;
            }
        });

        donateBtn.setOnClickListener(v -> { if (!isProcessing) validateAndPay(); });
        selectPreset(1);
    }

    // ── Step 1: Validate card fields ───────────────────────────────────────
    private void validateAndPay() {
        if (selectedAmount <= 0) {
            Toast.makeText(requireContext(), "Please select an amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawNumber = cardNumberEt.getText().toString().replaceAll("\\s", "");
        String expiry    = cardExpiryEt.getText().toString().trim();
        String cvc       = cardCvcEt.getText().toString().trim();

        if (rawNumber.length() < 13 || rawNumber.length() > 19) {
            cardNumberEt.setError("Enter a valid card number"); cardNumberEt.requestFocus(); return;
        }
        if (!expiry.matches("\\d{2}/\\d{2}")) {
            cardExpiryEt.setError("Use MM/YY format"); cardExpiryEt.requestFocus(); return;
        }
        if (cvc.length() < 3 || cvc.length() > 4) {
            cardCvcEt.setError("Enter a valid CVC"); cardCvcEt.requestFocus(); return;
        }

        int expMonth, expYear;
        try {
            expMonth = Integer.parseInt(expiry.substring(0, 2));
            expYear  = Integer.parseInt("20" + expiry.substring(3));
        } catch (Exception e) {
            cardExpiryEt.setError("Invalid date"); cardExpiryEt.requestFocus(); return;
        }
        if (expMonth < 1 || expMonth > 12) {
            cardExpiryEt.setError("Invalid month"); cardExpiryEt.requestFocus(); return;
        }

        isProcessing = true;
        setLoadingState(true);

        // ── Step 2: Tokenise card ON DEVICE via Stripe SDK ─────────────────
        // ✅ Uses CardParams — works across all modern Stripe Android SDK versions.
        // Card numbers go directly to Stripe servers, never to our backend.
        CardParams cardParams = new CardParams(rawNumber, expMonth, expYear, cvc);
        PaymentMethodCreateParams createParams =
                PaymentMethodCreateParams.createCard(cardParams);

        stripe.createPaymentMethod(
                createParams,
                new com.stripe.android.ApiResultCallback<PaymentMethod>() {
                    @Override
                    public void onSuccess(@NonNull PaymentMethod paymentMethod) {
                        // ✅ Got pm_XXXX — safe to send to our server
                        Log.d(TAG, "PaymentMethod created: " + paymentMethod.id);
                        postToServer(paymentMethod.id);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        mainHandler.post(() -> {
                            setLoadingState(false);
                            isProcessing = false;
                            String msg = e.getMessage();
                            Toast.makeText(requireContext(),
                                    "Card error: " + (msg != null ? msg : "Check your card details."),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    // ── Step 3: POST pm_XXXX + amount to servlet ───────────────────────────
    private void postToServer(String paymentMethodId) {
        if (executor == null || executor.isShutdown())
            executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                // ✅ No userId in body — server reads from JWT attribute
                JSONObject body = new JSONObject();
                body.put("amount",          selectedAmount);
                body.put("paymentMethodId", paymentMethodId);

                String     result = httpPost(Constants.MONETARY_DONATION_ENDPOINT, body.toString());
                JSONObject json   = new JSONObject(result);
                String     status = json.optString("status", "error");

                mainHandler.post(() -> {
                    if (getView() == null) return;
                    setLoadingState(false);
                    isProcessing = false;
                    if ("success".equals(status)) {
                        showSuccessState();
                    } else {
                        String msg = json.optString("message", "Payment failed. Please try again.");
                        Toast.makeText(requireContext(), "❌ " + msg, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "postToServer: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    setLoadingState(false);
                    isProcessing = false;
                    Toast.makeText(requireContext(), "Connection error. Please try again.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ── Card brand detection ───────────────────────────────────────────────
    private void updateCardBrand(String digits) {
        if (cardBrandTv == null) return;
        if      (digits.startsWith("4"))                           { cardBrandTv.setText("VISA"); cardBrandTv.setTextColor(Color.parseColor("#1A1F71")); }
        else if (digits.startsWith("5") || digits.startsWith("2")){ cardBrandTv.setText("MC");   cardBrandTv.setTextColor(Color.parseColor("#EB001B")); }
        else if (digits.startsWith("3"))                           { cardBrandTv.setText("AMEX"); cardBrandTv.setTextColor(Color.parseColor("#007BC1")); }
        else if (digits.isEmpty())                                 { cardBrandTv.setText("💳");   cardBrandTv.setTextColor(Color.parseColor("#CCCCCC")); }
        else                                                       { cardBrandTv.setText("CARD"); cardBrandTv.setTextColor(Color.parseColor("#888888")); }
    }

    // ── Preset UI ──────────────────────────────────────────────────────────
    private void selectPreset(int index) {
        selectedAmount = PRESET_AMOUNTS[index];
        deselectAllPresets();
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(Color.parseColor("#DC143C")); sel.setCornerRadius(dp(12));
        presets[index].setBackground(sel);
        for (int i = 0; i < presets[index].getChildCount(); i++) {
            View child = presets[index].getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                tv.setTextColor(tv.getText().toString().startsWith("$")
                        ? Color.WHITE : Color.parseColor("#FFCCCC"));
            }
        }
        updateHeader(selectedAmount, PRESET_DESCS[index]);
        updateSummary(selectedAmount);
        ScaleAnimation sa = new ScaleAnimation(0.9f, 1f, 0.9f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        sa.setDuration(150);
        selectedAmountTv.startAnimation(sa);
    }

    private void deselectAllPresets() {
        for (LinearLayout p : presets) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE); bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), Color.parseColor("#EEEEEE")); p.setBackground(bg);
            for (int i = 0; i < p.getChildCount(); i++) {
                View child = p.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    tv.setTextColor(tv.getText().toString().startsWith("$")
                            ? Color.parseColor("#1A1A1A") : Color.parseColor("#AAAAAA"));
                }
            }
        }
    }

    private void updateHeader(double amount, String desc) {
        selectedAmountTv.setText(fmt(amount));
        selectedAmountDesc.setText(desc);
        donateBtnText.setText("Donate " + fmt(amount));
    }

    private void updateSummary(double amount) {
        summaryAmountTv.setText(String.format("$%.2f", amount));
        summaryFeeTv.setText("$0.00");
        summaryTotalTv.setText(String.format("$%.2f", amount));
    }

    private void setLoadingState(boolean loading) {
        donateBtn.setAlpha(loading ? 0.6f : 1f);
        donateBtn.setEnabled(!loading);
        donateBtnText.setText(loading ? "Processing..." : "Donate " + fmt(selectedAmount));
        customAmountEt.setEnabled(!loading);
        cardNumberEt.setEnabled(!loading);
        cardExpiryEt.setEnabled(!loading);
        cardCvcEt.setEnabled(!loading);
        for (LinearLayout p : presets) p.setEnabled(!loading);
    }

    private void showSuccessState() {
        cardNumberEt.setText(""); cardExpiryEt.setText(""); cardCvcEt.setText("");
        if (cardBrandTv != null) cardBrandTv.setText("💳");
        Toast.makeText(requireContext(),
                "Thank you! Donation of " + fmt(selectedAmount) + " successful 🩸❤️",
                Toast.LENGTH_LONG).show();
        mainHandler.postDelayed(() -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        }, 1500);
    }

    private String fmt(double amount) {
        if (amount == (long) amount) return "$" + (long) amount;
        return "$" + String.format("%.2f", amount);
    }

    private int dp(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    // ✅ JWT token attached + no userId in body
    private String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        AuthManager.attachToken(requireContext(), conn); // ✅ JWT
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
            dos.write(body.getBytes("UTF-8"));
        }
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
    }
}