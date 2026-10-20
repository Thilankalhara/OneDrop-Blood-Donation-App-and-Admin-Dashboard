package com.example.onedrop.Activity.Utils;

public class Constants {

    // ⚠️ Replace with your local server IP or production backend URL
    public static final String BASE_URL = "http://YOUR_SERVER_IP:8080/oneDrop/";

    // Auth
    public static final String USER_SIGNUP_ENDPOINT              = BASE_URL + "UserSignUpServlet";
    public static final String USER_SIGNIN_ENDPOINT              = BASE_URL + "UserSignInServlet";

    // Email Verification
    public static final String VERIFY_EMAIL_ENDPOINT             = BASE_URL + "VerifyEmailCode";
    public static final String SEND_VERIFICATION_CODE_ENDPOINT   = BASE_URL + "SendVerificationCodeServlet";

    // Password Reset
    public static final String FORGET_PASSWORD_ENDPOINT          = BASE_URL + "ForgetPassword";
    public static final String VERIFY_OTP_ENDPOINT               = BASE_URL + "VerifyCode";
    public static final String RESET_PASSWORD_ENDPOINT           = BASE_URL + "ResetPassword";

    // Home Screen — single endpoint returns user info + donations + alerts
    public static final String HOME_DATA_ENDPOINT                = BASE_URL + "HomeDataServlet";

    public static final String RESEND_VERIFY_EMAIL_ENDPOINT      = BASE_URL + "SendVerificationCodeServlet";

    // Alerts
    public static final String DELETE_ALERT_ENDPOINT           = BASE_URL + "DeleteAlert";

    // Leaderboard
    public static final String LEADERBOARD_ENDPOINT            = BASE_URL + "LeaderboardServlet";

    // QR Scan
    public static final String SCAN_QR_ENDPOINT                = BASE_URL + "ScanQRServlet";

    // profile
    public static final String PROFILE_ENDPOINT                = BASE_URL + "ProfileServlet";

    //  Session validity check — used for auto-logout when admin deactivates user
    public static final String SESSION_CHECK_ENDPOINT            = BASE_URL + "SessionCheckServlet";

    // Donations
    public static final String DONATION_ENDPOINT                 = BASE_URL + "Donations";

    // Hospitals (for blood donation map)
    public static final String HOSPITALS_ENDPOINT                = BASE_URL + "Hospitals";

    //Donate money
    public static final String MONETARY_DONATION_ENDPOINT      = BASE_URL + "MoneyDonation";
}