package ru.wot.wotgold;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.database.FirebaseDatabase;
import com.plattysoft.leonids.ParticleSystem;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends Activity {

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private FirebaseAuth mAuth;

    // UI references.
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    protected UserProfile userProfile;
    private CheckBox mRememberChk;
    private FirebaseAnalytics mFirebaseAnalytics;


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent i = new Intent();
            i.putExtra("exit", true);

            setResult(RESULT_OK, i);
            finish();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mAuth = FirebaseAuth.getInstance();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        // Set up the login form.
        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);
        ImageView mBackgroundImage = (ImageView) findViewById(R.id.mBackgroundImage);
        mRememberChk = (CheckBox)  findViewById(R.id.mRememberChk);
        TextView mHelpButton = (TextView) findViewById(R.id.mHelpButton);

        mPasswordView = (EditText) findViewById(R.id.password);
            mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.password || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        mHelpButton.setOnClickListener(new HelpButtonClick());

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        Button mEmailSignUpButton = (Button) findViewById(R.id.mSignupButton);
        mEmailSignUpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSignup();
            }
        });

        final int maxParticles = 20;
        final int numParticles = 4;

        mBackgroundImage.setImageResource(R.drawable.wot_logo);

        Space space = (Space)findViewById(R.id.space);
        space.post(new Runnable() {
            @Override
            public void run() {
                LoginActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new ParticleSystem(LoginActivity.this, maxParticles, R.drawable.sparkle_1, 4000)
                                .setSpeedModuleAndAngleRange(0f, 0.3f, -35, -40)
                                .setRotationSpeed(10)
                                .setAcceleration(0.00005f, 0)
                                .emit(findViewById(R.id.space).getLeft() - 80, findViewById(R.id.space).getTop() + 16, numParticles);

                        new ParticleSystem(LoginActivity.this, maxParticles, R.drawable.sparkle_2, 4000)
                                .setSpeedModuleAndAngleRange(0f, 0.2f, -35, -40)
                                .setRotationSpeed(10)
                                .setAcceleration(0.00005f, 0)
                                .emit(findViewById(R.id.space).getLeft() - 80, findViewById(R.id.space).getTop(), numParticles);

                        new ParticleSystem(LoginActivity.this, maxParticles, R.drawable.sparkle_3, 4000)
                                .setSpeedModuleAndAngleRange(0f, 0.2f, -35, -40)
                                .setRotationSpeed(10)
                                .setAcceleration(0.00005f, 0)
                                .emit(findViewById(R.id.space).getLeft() - 64, findViewById(R.id.space).getTop(), numParticles);
                    }
                });
            }
        });

        userProfile = UserProfile.getInstance(LoginActivity.this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (userProfile.getEmail() != null && userProfile.getPassword() != null) {
            mEmailView.setText(userProfile.getEmail());
            mPasswordView.setText(userProfile.getPassword());
            mRememberChk.setChecked(userProfile.getRemember());
        }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        final String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        } else if (!isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Kick off a background task to
            // perform the user login attempt.
            final ProgressDialog dialog = new ProgressDialog(LoginActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage("Вход...");
            dialog.show();
            try {
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    //  Вход успешно завершён. Обновим данные локального профиля и закроем экран.
                                    userProfile.setEmail(mAuth.getCurrentUser().getEmail());
                                    userProfile.setPassword(password);
                                    userProfile.setUid(mAuth.getCurrentUser().getUid());
                                    userProfile.setRemember(mRememberChk.isChecked());
                                    userProfile.save();

                                    mFirebaseAnalytics.setUserId(userProfile.getUid());
                                    Bundle bundle = new Bundle();
                                    bundle.putString("email", userProfile.getEmail());
                                    bundle.putString("uid", userProfile.getUid());
                                    mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);

                                    dialog.dismiss();
                                    finish();
                                }else {
                                    //  Вход завершился неудачей. Узнаем, в чём ошибка и покажем это.
                                    task.getException().printStackTrace();
                                    if (task.getException().getMessage().contains("email")) {
                                        mEmailView.setError(task.getException().getLocalizedMessage());
                                        mEmailView.requestFocus();
                                    }else{
                                        mPasswordView.setError(task.getException().getLocalizedMessage());
                                        mPasswordView.requestFocus();
                                    }
                                }

                                dialog.dismiss();
                            }
                        });
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Attempts to sign up the user.
     * If there are any errors, they are presented
     * and no registration attempt is made.
     */
    private void attemptSignup() {
        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        final String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        } else if (!isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Kick off a background task to
            // perform the user login attempt.
            final ProgressDialog dialog = new ProgressDialog(LoginActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage("Регистрация...");
            dialog.show();
            try {
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    //  Регистрация и вход успешно завершены. Обновим данные локального профиля и закроем экран.
                                    userProfile.setEmail(mAuth.getCurrentUser().getEmail());
                                    userProfile.setPassword(password);
                                    userProfile.setUid(mAuth.getCurrentUser().getUid());
                                    userProfile.setRemember(mRememberChk.isChecked());
                                    userProfile.save();

                                    mFirebaseAnalytics.setUserId(userProfile.getUid());
                                    Bundle bundle = new Bundle();
                                    bundle.putString("email", userProfile.getEmail());
                                    bundle.putString("uid", userProfile.getUid());
                                    mFirebaseAnalytics.logEvent("register", bundle);

                                    dialog.dismiss();
                                    finish();
                                }else {
                                    //  Вход завершился неудачей. Узнаем, в чём ошибка и покажем это.
                                    task.getException().printStackTrace();
                                    if (task.getException().getMessage().contains("email")) {
                                        mEmailView.setError(task.getException().getLocalizedMessage());
                                        mEmailView.requestFocus();
                                    }else{
                                        mPasswordView.setError(task.getException().getLocalizedMessage());
                                        mPasswordView.requestFocus();
                                    }
                                }

                                dialog.dismiss();
                            }
                        });
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isEmailValid(String email) {
        return email.contains("@") && email.contains(".") && (email.indexOf(".") > 0 && email.indexOf(".") != email.length());
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
    }

    public class HelpButtonClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this)
                    .setCancelable(true)
                    .setTitle(getString(R.string.help_title))
                    .setMessage(getString(R.string.help_text));

            builder.create().show();
        }
    }
}

