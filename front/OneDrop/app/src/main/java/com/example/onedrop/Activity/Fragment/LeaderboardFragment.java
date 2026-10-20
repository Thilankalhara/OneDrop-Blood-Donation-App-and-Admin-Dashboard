package com.example.onedrop.Activity.Fragment;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LeaderboardFragment extends BaseRefreshFragment {

    private static final String TAG = "LeaderboardFragment";

    private static final int[] AVATAR_COLORS = {
            0xFF7C4DFF, 0xFF00897B, 0xFFE91E63, 0xFF2196F3,
            0xFF43A047, 0xFFFF7043, 0xFF8D6E63, 0xFF546E7A,
    };

    private TextView  rank1Avatar, rank1Name, rank1Score, rank1Donations;
    private TextView  rank2Avatar, rank2Name, rank2Score, rank2Donations;
    private TextView  rank3Avatar, rank3Name, rank3Score, rank3Donations;
    private TextView  myAvatar, myName, myRankNumber, myScore;
    private TextView  myRankHint, myDonationCount, myTotalPointsLabel;
    private RecyclerView recyclerView;

    private int currentUserId = -1;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private UserDataManager userDataManager;

    //  ADD THIS: Flag to prevent reload
    private boolean leaderboardLoaded = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userDataManager = new UserDataManager(requireContext());

        rank1Avatar    = view.findViewById(R.id.rank1Avatar);
        rank1Name      = view.findViewById(R.id.rank1Name);
        rank1Score     = view.findViewById(R.id.rank1Score);
        rank1Donations = view.findViewById(R.id.rank1Donations);
        rank2Avatar    = view.findViewById(R.id.rank2Avatar);
        rank2Name      = view.findViewById(R.id.rank2Name);
        rank2Score     = view.findViewById(R.id.rank2Score);
        rank2Donations = view.findViewById(R.id.rank2Donations);
        rank3Avatar    = view.findViewById(R.id.rank3Avatar);
        rank3Name      = view.findViewById(R.id.rank3Name);
        rank3Score     = view.findViewById(R.id.rank3Score);
        rank3Donations = view.findViewById(R.id.rank3Donations);
        myAvatar          = view.findViewById(R.id.myAvatar);
        myName            = view.findViewById(R.id.myName);
        myRankNumber      = view.findViewById(R.id.myRankNumber);
        myScore           = view.findViewById(R.id.myScore);
        myRankHint        = view.findViewById(R.id.myRankHint);
        myDonationCount   = view.findViewById(R.id.myDonationCount);
        myTotalPointsLabel= view.findViewById(R.id.myTotalPointsLabel);
        recyclerView      = view.findViewById(R.id.leaderboardRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        currentUserId = AuthManager.getUserId(requireContext());

        //  Only fetch once per fragment lifecycle
        if (!leaderboardLoaded) {
            leaderboardLoaded = true;
            fetchLeaderboard();
        }
    }

    @Override protected void onRefresh() {
        leaderboardLoaded = false; //  Reset on manual refresh
        fetchLeaderboard();
    }

    private void fetchLeaderboard() {
        executor.execute(() -> {
            try {
                String     url    = Constants.LEADERBOARD_ENDPOINT + "?userId=" + currentUserId;
                String     result = httpGet(url);
                JSONObject json   = new JSONObject(result);

                JSONArray  top3 = json.optJSONArray("top3");
                JSONObject p1 = null, p2 = null, p3 = null;
                if (top3 != null) {
                    for (int i = 0; i < top3.length(); i++) {
                        JSONObject d = top3.getJSONObject(i);
                        int rank = d.optInt("rank");
                        if      (rank == 1) p1 = d;
                        else if (rank == 2) p2 = d;
                        else if (rank == 3) p3 = d;
                    }
                }
                final JSONObject final1 = p1, final2 = p2, final3 = p3;

                JSONObject my      = json.optJSONObject("my_rank");
                final String mInit = my != null ? my.optString("initials",      "?")   : "?";
                final String mName = my != null ? my.optString("name",          "You") : "You";
                final int    mRank = my != null ? my.optInt("rank",              0)    : 0;
                final int    mPts  = my != null ? my.optInt("total_points",      0)    : 0;
                final int    mCnt  = my != null ? my.optInt("donation_count",    0)    : 0;
                final String mHint = my != null ? my.optString("hint",           "")   : "";

                JSONArray       allJson = json.optJSONArray("all_donors");
                List<DonorItem> list    = new ArrayList<>();
                if (allJson != null) {
                    for (int i = 0; i < allJson.length(); i++) {
                        JSONObject d  = allJson.getJSONObject(i);
                        DonorItem  di = new DonorItem();
                        di.userId   = d.optInt("user_id");
                        di.rank     = d.optInt("rank");
                        di.name     = d.optString("name",     "");
                        di.initials = d.optString("initials", "?");
                        di.points   = d.optInt("total_points");
                        di.count    = d.optInt("donation_count");
                        list.add(di);
                    }
                }
                final List<DonorItem> finalList = list;

                mainHandler.post(() -> {
                    if (getView() == null) return;
                    bindPodium(final1, rank1Avatar, rank1Name, rank1Score, rank1Donations);
                    bindPodium(final2, rank2Avatar, rank2Name, rank2Score, rank2Donations);
                    bindPodium(final3, rank3Avatar, rank3Name, rank3Score, rank3Donations);
                    myAvatar.setText(mInit); myName.setText(mName);
                    myRankNumber.setText(mRank > 0 ? "#" + mRank : "#—");
                    myScore.setText(formatPts(mPts)); myRankHint.setText(mHint);
                    myDonationCount.setText("  " + mCnt + " donation" + (mCnt==1?"":"s") + " × 100 pts");
                    myTotalPointsLabel.setText("= " + formatPts(mPts));
                    recyclerView.setAdapter(new DonorAdapter(finalList, currentUserId));
                    stopRefreshing();
                });

            } catch (Exception e) {
                Log.e(TAG, "Leaderboard error: " + e.getMessage(), e);
                mainHandler.post(this::stopRefreshing);
            }
        });
    }

    private void bindPodium(JSONObject d, TextView avatar, TextView name, TextView score, TextView donations) {
        if (d == null) return;
        avatar.setText(d.optString("initials", "?"));
        name.setText(shortName(d.optString("name", "")));
        score.setText(formatPts(d.optInt("total_points")));
        donations.setText(d.optInt("donation_count") + " 🩸");
    }

    private String formatPts(int pts) { return pts >= 1000 ? String.format("%,d pts", pts) : pts + " pts"; }
    private String shortName(String n) {
        if (n==null||n.isEmpty()) return "";
        String[] p = n.trim().split("\\s+");
        return p.length>=2 ? p[0]+" "+p[1].charAt(0)+"." : n;
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        AuthManager.attachToken(requireContext(), conn);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        conn.disconnect();
        return sb.toString();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        if (userDataManager != null) userDataManager.close();
    }

    static class DonorItem { int userId, rank, points, count; String name, initials; }

    static class DonorAdapter extends RecyclerView.Adapter<DonorAdapter.VH> {
        private final List<DonorItem> items;
        private final int             currentUserId;

        DonorAdapter(List<DonorItem> items, int currentUserId) { this.items = items; this.currentUserId = currentUserId; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard_donor, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DonorItem item = items.get(position);
            h.rank.setText(String.valueOf(item.rank));
            h.avatar.setText(item.initials);
            h.name.setText(item.name);
            h.score.setText(item.points>=1000 ? String.format("%,d",item.points) : String.valueOf(item.points));
            h.location.setText(item.count + " donation" + (item.count==1?"":"s"));
            h.location.setVisibility(View.VISIBLE); h.change.setVisibility(View.GONE);
            GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL);
            circle.setColor(AVATAR_COLORS[position % AVATAR_COLORS.length]); h.avatar.setBackground(circle);
            h.itemView.setBackgroundColor(item.userId==currentUserId ? Color.parseColor("#FFF0F0") : Color.TRANSPARENT);
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView rank, avatar, name, location, score, change;
            VH(@NonNull View v) {
                super(v);
                rank=v.findViewById(R.id.donorRank); avatar=v.findViewById(R.id.donorAvatar);
                name=v.findViewById(R.id.donorName); location=v.findViewById(R.id.donorLocation);
                score=v.findViewById(R.id.donorScore); change=v.findViewById(R.id.donorChange);
            }
        }
    }
}