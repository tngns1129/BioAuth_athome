package com.semo.bioauth;

import static android.content.Context.FINGERPRINT_SERVICE;
import static android.content.Context.KEYGUARD_SERVICE;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

import javax.crypto.Cipher;

@RequiresApi(api = Build.VERSION_CODES.M)
public class BioAuthManager {

    private static BioAuthManager instance;

    private BioAuthManager() { };

    public static BioAuthManager getInstance() {
        if (instance == null)
        {
            instance = new BioAuthManager();
        }

        return instance;
    }

    private static final int REQUEST_FINGERPRINT_ENROLLMENT_AUTH = 10;

    private KeyManager keyManager;

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private FingerprintManager fingerprintManager;
    private KeyguardManager keyguardManager;

    private FingerprintManager.CryptoObject cryptoObject; // over api 23
    private BiometricPrompt.CryptoObject bioCryptoObject; // over api 28

    private boolean canAuthenticate(Activity activity, Context context){

        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            Log.d("MY_APP", Integer.toString(Build.VERSION.SDK_INT));
            BiometricManager biometricManager = BiometricManager.from(context);
            switch (biometricManager.canAuthenticate(BIOMETRIC_STRONG))
            {
                case BiometricManager.BIOMETRIC_SUCCESS:
                    Log.d("MY_APP_TAG", "App can authenticate using biometrics.");
                    return true;
                case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                    Log.e("MY_APP_TAG", "No biometric features available on this device.");
                    return false;
                case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                    Log.e("MY_APP_TAG", "Biometric features are currently unavailable.");
                    return false;
                case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                    // Prompts the user to create credentials that your app accepts.
                    Log.d("MY_APP_TAG", "enrolled biometric doesn't existed. please enroll");

                    intent = new Intent(Settings.ACTION_FINGERPRINT_ENROLL);
                    intent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            BIOMETRIC_STRONG);
                    activity.startActivityForResult(intent, REQUEST_FINGERPRINT_ENROLLMENT_AUTH);
                    return false;
                default:
                    Log.d("MY_APP", "whyyyyyyyy");
            }
        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            fingerprintManager = (FingerprintManager) context.getSystemService(FINGERPRINT_SERVICE);
            keyguardManager = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
            Log.d("MY_APP", "here2");
            if (!fingerprintManager.isHardwareDetected()) // 지문을 사용할 수 없는 디바이스인 경우
            {
                Log.d("MY_APP", "it is not device that can use fingerprint");
                return false;
            }
            // 지문 인증 사용을 거부한 경우
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED)
            {
                Log.d("MY_APP", "permission denied");
                return false;
            }
            else if (!keyguardManager.isKeyguardSecure()) // 잠금 화면이 설정되지 않은 경우
            {
                Log.d("MY_APP", "please set lock screen");
                return false;
            }
            else if (!fingerprintManager.hasEnrolledFingerprints()) // 등록된 지문이 없는 경우
            {
                Log.d("MY_APP", "please enroll fingerprint");
                intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                activity.startActivityForResult(intent, REQUEST_FINGERPRINT_ENROLLMENT_AUTH);
                return false;
            }

            return true;
        }
        Log.d("MY_APP", "here3");
        return false;
    }


    public void authenticate(Activity activity, Context context) {

        keyManager = KeyManager.getInstance();
        // api 28 ( ANDROID 9.0 ) 이상은 biometricPrompt 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.d("MY_APP", "start biometricPrompt here");

            if (canAuthenticate(activity, context))
            {
                executor = ContextCompat.getMainExecutor(context);
                biometricPrompt = new BiometricPrompt((FragmentActivity) activity, executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.d("MY_APP", errString.toString());
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d("MY_APP", "auth success");
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.d("MY_APP", "auth failed");
                    }
                });

                // DEVICE_CREDENTIAL 및 BIOMETRIC_STRING | DEVICE_CREDENTIAL 은 안드로이드 10 이하에서 지원되지 않는다.
                // 안드로이드 10 이하에서 PIN 이나 패턴, 비밀번호가 있는지 확인하려면 KeyguardManager.isDeviceSecure() 함수를 사용할 것
                promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("지문 인증")
                        .setSubtitle("기기에 등록된 지문을 이용하여 지문을 인증해주세요.")
                        .setDescription("생체 인증 설명")
                        // BIOMETRIC_STRONG 은 안드로이드 11 에서 정의한 클래스 3 생체 인식을 사용하는 인증 - 암호회된 키 필요
                        // BIOMETRIC_WEAK 은 안드로이드 11 에서 정의한 클래스 2 생체 인식을 사용하는 인증 - 암호화된 키까지 필요하지는 않음
                        // DEVICE_CREDENTIAL 은 화면 잠금 사용자 인증 정보를 사용하는 인증 - 사용자의 PIN, 패턴 또는 비밀번호
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .setConfirmationRequired(false) // 명시적인 사용자 작업 ( 생체 인식 전 한번더 체크 ) 없이 인증할건지 default : true
                        .setNegativeButtonText("취소")
                        .build();

                keyManager.generateKey();

                if (keyManager.cipherInit())
                {
                    bioCryptoObject = new BiometricPrompt.CryptoObject(keyManager.getCipher());
                    biometricPrompt.authenticate(promptInfo, bioCryptoObject);
                }

                biometricPrompt.authenticate(promptInfo);
            }

        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)// api 23 ( ANDROID 6.0 ) 부터 api 28 ( ANDROID 9.0 ) 까지는 fingerprint 사용
        {
            Log.d("MY_APP", "fingerprint start");

            fingerprintManager = (FingerprintManager) context.getSystemService(FINGERPRINT_SERVICE);
            keyguardManager = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
//            if (!fingerprintManager.isHardwareDetected()) // 지문을 사용할 수 없는 디바이스인 경우
//            {
//                Log.d("fingerprint", "it is not device that can use fingerprint");
//            }
//            // 지문 인증 사용을 거부한 경우
//            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED)
//            {
//                Log.d("fingerprint", "permission denied");
//            }
//            else if (!keyguardManager.isKeyguardSecure()) // 잠금 화면이 설정되지 않은 경우
//            {
//                Log.d("fingerprint", "please set lock screen");
//            }
//            else if (!fingerprintManager.hasEnrolledFingerprints()) // 등록된 지문이 없는 경우
//            {
//                Log.d("fingerprint", "please enroll fingerprint");
//            }
//            else
            if (canAuthenticate(activity, context))
            {
                Log.d("MY_APP", "requirement fingerprint needed all pass");

                keyManager.generateKey();

                if (keyManager.cipherInit())
                {

                    Cipher cipher = keyManager.getCipher();

                    cryptoObject = new FingerprintManager.CryptoObject(cipher);

                    fingerprintManager.authenticate(cryptoObject, new CancellationSignal(), 0, new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            Log.d("MY_APP", "fingerprint " + errorCode);
                        }

                        @Override
                        public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Log.d("MY_APP", "fingerprint auth success");

                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Log.d("MY_APP", "fingerprint auth failed");

                        }

                        @Override
                        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                            super.onAuthenticationHelp(helpCode, helpString);
                            Log.d("MY_APP", "fingerprint" + helpString.toString());
                        }

                    }, null);
                }

            }
        }
    }


    public boolean check() {
        return KeyManager.check();
    }
}