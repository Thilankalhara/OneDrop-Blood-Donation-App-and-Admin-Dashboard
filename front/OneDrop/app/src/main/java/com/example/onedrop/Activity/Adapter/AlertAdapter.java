package com.example.onedrop.Activity.Adapter;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.onedrop.Activity.Fragment.HomeFragment;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    private static final String TAG = "AlertAdapter";

    private final List<HomeFragment.AlertItem> items;
    private final OnAlertDeletedListener       deleteListener;

    public interface OnAlertDeletedListener {
        void onAlertDeleted(int position);
        void onDeleteFailed(int position);
    }

    public AlertAdapter(List<HomeFragment.AlertItem> items,
                        OnAlertDeletedListener deleteListener) {
        this.items          = items;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        HomeFragment.AlertItem item = items.get(position);
        holder.title.setText(item.title);
        holder.message.setText(item.message);
        holder.time.setText(formatTime(item.time));
        holder.itemView.setAlpha(item.isRead ? 0.7f : 1.0f);
        holder.unreadDot.setVisibility(item.isRead ? View.GONE : View.VISIBLE);

        // ✅ Click whole card → show full message dialog
        holder.itemView.setOnClickListener(v ->
                showFullMessageDialog(v.getContext(), item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── Show full message in a bottom-sheet style dialog ───────────────────
    private void showFullMessageDialog(Context context, HomeFragment.AlertItem item) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        // ── Build layout programmatically ──────────────────────────────────
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 24), dp(context, 24),
                dp(context, 24), dp(context, 32));
        root.setBackgroundResource(android.R.color.transparent);

        // Card container
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 20), dp(context, 20),
                dp(context, 20), dp(context, 20));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(context, 20));
        card.setBackground(cardBg);

        // ── Drag handle ────────────────────────────────────────────────────
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Color.parseColor("#DDDDDD"));
        handleBg.setCornerRadius(dp(context, 4));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                dp(context, 40), dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(context, 16);
        handle.setLayoutParams(handleParams);

        // ── Bell icon + close row ──────────────────────────────────────────
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = dp(context, 16);
        headerRow.setLayoutParams(headerParams);

        // Bell emoji
        TextView bellIcon = new TextView(context);
        bellIcon.setText("🔔");
        bellIcon.setTextSize(22);
        GradientDrawable bellBg = new GradientDrawable();
        bellBg.setColor(Color.parseColor("#FFEBEE"));
        bellBg.setCornerRadius(dp(context, 10));
        bellIcon.setBackground(bellBg);
        bellIcon.setPadding(dp(context, 10), dp(context, 8),
                dp(context, 10), dp(context, 8));
        bellIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bellParams = new LinearLayout.LayoutParams(
                dp(context, 44), dp(context, 44));
        bellParams.rightMargin = dp(context, 12);
        bellIcon.setLayoutParams(bellParams);

        // Title
        TextView titleTv = new TextView(context);
        titleTv.setText(item.title);
        titleTv.setTextSize(16);
        titleTv.setTextColor(Color.parseColor("#1A1A1A"));
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleTv.setLayoutParams(titleParams);

        // Close button
        TextView closeBtn = new TextView(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(16);
        closeBtn.setTextColor(Color.parseColor("#AAAAAA"));
        closeBtn.setPadding(dp(context, 8), dp(context, 4),
                dp(context, 4), dp(context, 4));
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        headerRow.addView(bellIcon);
        headerRow.addView(titleTv);
        headerRow.addView(closeBtn);

        // ── Divider ────────────────────────────────────────────────────────
        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        dividerParams.bottomMargin = dp(context, 16);
        divider.setLayoutParams(dividerParams);

        // ── Scrollable full message ────────────────────────────────────────
        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 200));
        scrollParams.bottomMargin = dp(context, 16);
        scrollView.setLayoutParams(scrollParams);

        TextView messageTv = new TextView(context);
        messageTv.setText(item.message);
        messageTv.setTextSize(14);
        messageTv.setTextColor(Color.parseColor("#444444"));
        messageTv.setLineSpacing(dp(context, 4), 1f);
        scrollView.addView(messageTv);

        // ── Date row ───────────────────────────────────────────────────────
        TextView dateTv = new TextView(context);
        dateTv.setText("📅  " + formatTime(item.time));
        dateTv.setTextSize(12);
        dateTv.setTextColor(Color.parseColor("#AAAAAA"));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.bottomMargin = dp(context, 20);
        dateTv.setLayoutParams(dateParams);

        // ── Close button at bottom ─────────────────────────────────────────
        TextView closeBottomBtn = new TextView(context);
        closeBottomBtn.setText("Close");
        closeBottomBtn.setTextSize(14);
        closeBottomBtn.setTextColor(Color.WHITE);
        closeBottomBtn.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#DC143C"));
        closeBg.setCornerRadius(dp(context, 50));
        closeBottomBtn.setBackground(closeBg);
        LinearLayout.LayoutParams closeBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48));
        closeBottomBtn.setLayoutParams(closeBtnParams);
        closeBottomBtn.setOnClickListener(v -> dialog.dismiss());

        // ── Assemble card ──────────────────────────────────────────────────
        card.addView(handle);
        card.addView(headerRow);
        card.addView(divider);
        card.addView(scrollView);
        card.addView(dateTv);
        card.addView(closeBottomBtn);
        root.addView(card);

        dialog.setContentView(root);

        // ── Style the dialog window ────────────────────────────────────────
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.getAttributes().windowAnimations =
                    android.R.style.Animation_InputMethod; // slide up animation
        }

        dialog.show();
    }

    // ── Called by ItemTouchHelper on swipe ─────────────────────────────────
    public void deleteItem(int position, Context context) {
        HomeFragment.AlertItem item = items.get(position);
        int alertId = item.alertId;

        items.remove(position);
        notifyItemRemoved(position);

        if (deleteListener != null) deleteListener.onAlertDeleted(position);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.DELETE_ALERT_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                JSONObject payload = new JSONObject();
                payload.put("alertId", alertId);

                try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                    os.write(payload.toString().getBytes("UTF-8"));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Delete alert " + alertId + " → " + responseCode);

            } catch (Exception e) {
                Log.e(TAG, "Delete error: " + e.getMessage(), e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String formatTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "";
        return timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
    }

    private int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        TextView title, message, time;
        View     unreadDot;

        AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            title     = itemView.findViewById(R.id.alertTitle);
            message   = itemView.findViewById(R.id.alertMessage);
            time      = itemView.findViewById(R.id.alertTime);
            unreadDot = itemView.findViewById(R.id.unreadDot);
        }
    }
}