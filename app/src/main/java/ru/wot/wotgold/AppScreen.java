package ru.wot.wotgold;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.internal.formats.zzl;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AppScreen extends Activity {

    ImageButton mExitButton, mHelpButton;
    TextView mUserGreet, mProgressText;
    ProgressBar progressBar;

    private FirebaseAnalytics mFirebaseAnalytics;
    private FirebaseAuth mAuth;
    private FirebaseDatabase mDatabase;
    private FirebaseRemoteConfig mConfig;

    private UserProfile userProfile;

    private AlertDialog reqGoldDialog;

    public SparseIntArray goldCount = new SparseIntArray();

    public CountDownTimer timer;

    public long timeLeft = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_screen);

        MobileAds.initialize(this, getString(R.string.banner_app_id));

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        userProfile = UserProfile.getInstance(this);

        mAuth = FirebaseAuth.getInstance();

        mDatabase = FirebaseDatabase.getInstance();

        mConfig = FirebaseRemoteConfig.getInstance();

        goldCount.put(50, 100);
        goldCount.put(100, 250);

        Log.v("E_CREATE", "onCreate");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();

        //  Приложение сейчас спрячется, нужно сохранить состояние.
        Log.v("E_PAUSE", "onPause");

        //  Диалог убираем НЕ сохраняя изменения. При возобновлении работы он сам покажется заного.
        if (reqGoldDialog != null &&
                reqGoldDialog.isShowing())
            reqGoldDialog.dismiss();

        // Если есть таймер - его нужно остановить и запустить заново при разворачивании.
        if (timeLeft != 0) {
            timer.cancel();
            timer = null;

            mDatabase.getReference("users").child(userProfile.getUid()).child("waittime").setValue(timeLeft);

            Log.v("E_PAUSE", "Await time saved (" + timeLeft + " millis. left)");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null)
            return;

        Log.v("E_RESULT", "Request code " + requestCode + ", result code " + resultCode + ", exit(" + Boolean.toString(data.getBooleanExtra("exit", false)) + ")");

        boolean bExit = data.getBooleanExtra("exit", false);

        if (bExit)
            finish();
    }

    private View getTabIndicator(Context context, int title) {
        View view = LayoutInflater.from(context).inflate(R.layout.tabhead_layout, null);
        TextView tv = (TextView) view.findViewById(R.id.tabTitle);
        tv.setText(title);
        return view;
    }

    @Override
    protected void onStart() {
        super.onStart();

        setContentView(R.layout.app_screen);

        TabHost tabHost = (TabHost) findViewById(android.R.id.tabhost);
        tabHost.setup();

        TabHost.TabSpec tabSpec;

        tabSpec = tabHost.newTabSpec("tab1");
        tabSpec.setIndicator(getTabIndicator(tabHost.getContext(), R.string.events_active));
        tabSpec.setContent(R.id.tab1);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("tab2");
        tabSpec.setIndicator(getTabIndicator(tabHost.getContext(), R.string.events_archive));
        tabSpec.setContent(R.id.tab2);
        tabHost.addTab(tabSpec);

        mUserGreet = (TextView) findViewById(R.id.mUserGreet);
        mExitButton = (ImageButton) findViewById(R.id.mExitButton);
        mHelpButton = (ImageButton) findViewById(R.id.mHelpButton);
        mProgressText = (TextView) findViewById(R.id.mProgressText);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        mExitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putString("email", userProfile.getEmail());
                bundle.putString("uid", userProfile.getUid());
                mFirebaseAnalytics.logEvent("logout", bundle);

                //  Выйти из аккаунта.
                FirebaseAuth.getInstance().signOut();
                UserProfile.clear();

                //  Показать экран входа.
                Intent i = new Intent(AppScreen.this, LoginActivity.class);
                startActivityForResult(i, 111);
            }
        });

        mHelpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                bundle.putString("email", userProfile.getEmail());
                bundle.putString("uid", userProfile.getUid());
                mFirebaseAnalytics.logEvent("helpreq", bundle);

                //  Показать окно справки.
                AlertDialog dialog = new AlertDialog.Builder(AppScreen.this)
                        .setTitle(getString(R.string.help_title))
                        .setMessage(getString(R.string.help_text))
                        .setCancelable(true)
                        .create();

                dialog.show();
            }
        });

        if (userProfile.load()) {
            //  Есть данные. Выполним вход.
            final ProgressDialog dialog = new ProgressDialog(this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage("Вход...");
            dialog.show();

            mAuth.signInWithEmailAndPassword(userProfile.getEmail(), userProfile.getPassword())
                    .addOnCompleteListener(this, new OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<com.google.firebase.auth.AuthResult> task) {
                            if (task.isSuccessful()) {
                                userProfile.setUid(mAuth.getCurrentUser().getUid());
                                userProfile.setEmail(mAuth.getCurrentUser().getEmail());

                                mFirebaseAnalytics.setUserId(userProfile.getUid());
                                Bundle bundle = new Bundle();
                                bundle.putString("email", userProfile.getEmail());
                                bundle.putString("uid", userProfile.getUid());
                                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);

                                //  Проверим статус бана пользователя.
                                Log.v("E_SIGNIN", "Signed in as " + mAuth.getCurrentUser().getEmail());
                                mDatabase.getReference("status").child(userProfile.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        if (dataSnapshot.getChildrenCount() == 0) {
                                            //  Событий у пользователя нет вообще.
                                            Log.v("E_BANCHECK", "No events found.");
                                            loadClicks();

                                            dialog.dismiss();
                                            return;
                                        }

                                        Iterable<DataSnapshot> children = dataSnapshot.getChildren();
                                        long dateBanStart = 0;
                                        String keyBan = null;
                                        //  Проход по всем событиям и определение наличия бана,
                                        //  если таких несколько - определение даты самого "последнего".
                                        for (DataSnapshot child : children) {
                                            if (child.child("type").getValue(String.class).equals("block")) {
                                                //  Это бан. Запишем его дату начала.
                                                if (child.child("timestart").getValue(Long.class) > dateBanStart) {
                                                    dateBanStart = child.child("timestart").getValue(Long.class);
                                                    keyBan = child.getKey();
                                                }
                                            }
                                        }

                                        if (keyBan == null) {
                                            //  Банов нет.
                                            Log.v("E_BANCHECK", "No bans found.");
                                            loadClicks();

                                            dialog.dismiss();
                                            return;
                                        }

                                        //  В keyBan - ключ самого последнего бана.
                                        //  В dateBanStart - дата самого последнего бана.
                                        //  Теперь узнаём дату окончания этого бана. Если дата
                                        //  меньше сегодняшней - бан уже снят. Иначе - ещё в действии.
                                        //  FIXME: время надо брать с сервера, а не с телефона.
                                        Calendar calendar = Calendar.getInstance();
                                        long dateBanEnd = dataSnapshot.child(keyBan).child("timeend").getValue(Long.class)* 1000;

                                        if (calendar.getTimeInMillis() > dateBanEnd) {
                                            Log.v("E_BANCHECK", "Last ban already expired. (curr: " + calendar.getTimeInMillis() + ", end: " + dateBanEnd + ")");
                                            loadClicks();

                                            dialog.dismiss();
                                            return;
                                        }

                                        //  Бан в действии. Покажем соотв. уведомление.
                                        try {
                                            HashMap<String, Object> params = new HashMap<>();
                                            JSONObject json = new JSONObject();

                                            json.put("timestart", dataSnapshot.child(keyBan).child("timestart").getValue(Long.class));
                                            json.put("timeend", dataSnapshot.child(keyBan).child("timeend").getValue(Long.class));
                                            json.put("message", dataSnapshot.child(keyBan).child("msg").getValue(String.class));

                                            params.put("extra", json);

                                            Log.v("E_BANCHECK", "Will show ban message, begin date " + Calendar.getInstance().getTimeInMillis() + ", end date " + json.getLong("timeend") * 1000);

                                            dialog.dismiss();
                                            loadStatus();

                                            showBlockedScreen(params);
                                        }catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {

                                    }
                                });
                            }else{
                                //  Что-то неправильно. Покажем экран входа.
                                dialog.dismiss();

                                Bundle bundle = new Bundle();
                                bundle.putString("email", userProfile.getEmail());
                                bundle.putString("uid", userProfile.getUid());
                                mFirebaseAnalytics.logEvent("login_error", bundle);

                                Intent i = new Intent(AppScreen.this, LoginActivity.class);
                                startActivityForResult(i, 111);
                            }
                        }
                    });
        }else{
            //  Данных нет. Покажем экран входа.
            Intent i = new Intent(AppScreen.this, LoginActivity.class);
            startActivityForResult(i, 111);
        }

        if (userProfile.getEmail() != null)
            mUserGreet.setText("Привет, " + userProfile.getEmailName() + "!");

        Bundle bundle = new Bundle();
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, bundle);

        Log.v("E_START", "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.v("E_RESUME", "onResume");

        //  Читаем из базы. Если там есть таймер - то нужно его запустить с того момента, где остановились.
        if (userProfile.getUid() != null)
            mDatabase.getReference("users").child(userProfile.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.hasChild("waittime")) {
                        //  Прячем рекламу и показываем таймер.
                        Log.v("E_RESUME", "Will resume showing ad for " + dataSnapshot.child("waittime").getValue(Integer.class) + " more milliseconds.");

                        resumeAdTimer(dataSnapshot.child("waittime").getValue(Integer.class));
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
    }

    public void resumeAdTimer(int remainMillis) {
        Log.v("E_RESUMEADTIMER", "Resuming timer with " + remainMillis + " milliseconds left.");

        //  Запустить таймер для этого рекламного блока.
        final AdView adView = (AdView) findViewById(R.id.adView);
        final TextView tvCountdown = (TextView) findViewById(R.id.tvTimerFirst);

        adView.setVisibility(View.INVISIBLE);

        tvCountdown.setVisibility(View.VISIBLE);
        tvCountdown.setText("Новый просмотр через\n\t30 секунд");

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        timer = new CountDownTimer(remainMillis, 1000) {
            public void onTick(long millisUntilFinished) {
                timeLeft = millisUntilFinished;
                String timerString = getResources().getQuantityString(R.plurals.timeout_plurals, (int)(millisUntilFinished / 1000), (int)(millisUntilFinished / 1000));
                tvCountdown.setText("Новый просмотр через\n\t" + timerString);
            }

            public void onFinish() {
                timeLeft = 0;
                adView.setVisibility(View.VISIBLE);
                tvCountdown.setVisibility(View.INVISIBLE);
                mDatabase.getReference("users").child(userProfile.getUid()).child("waittime").removeValue();
            }
        };

        timer.start();
    }

    public void eventRequestGold(String uid) {
        //  Покажем запрос никнейма и далее запишем его в бд!
        if (userProfile.getUid() != uid) {
            Log.v("E_REQUESTGOLD", "Event handling failed due to incompatible uid's");

            return;
        }

        final DatabaseReference mAccountRef = mDatabase.getReference("users");

        mAccountRef.child(userProfile.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //  Покажем соотв. экран со статусом ожидания, если такой нужен.
                //  Иначе - покажем запрос никнейма.
                if (dataSnapshot.child("wotnick").getValue(String.class) != null) {
                    //  Ник уже указан. Узнаем статус обработки и покажем экран ожидания.
                    loadStatus();
                }else{
                    //  Ник не указан.
                    AlertDialog.Builder builder = new AlertDialog.Builder(AppScreen.this)
                            .setView(R.layout.req_screen)
                            .setPositiveButton("ОК", null)
                            .setCancelable(false);

                    reqGoldDialog = builder.create();
                    reqGoldDialog.show();

                    reqGoldDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            //  Здесь отправим запрос на сервер (запишем в бд никнейм).
                            //  Так же, надо как-то уведомить администратора о том, что
                            //  появился новый запрос.
                            final String mWotNickname = ((EditText) reqGoldDialog.findViewById(R.id.mWotNickname)).getText().toString().trim();

                            if (mWotNickname.isEmpty()) {
                                ((EditText) reqGoldDialog.findViewById(R.id.mWotNickname)).setError("Необходимо ввести Ваш игровой ник!");

                                return;
                            }

                            final DatabaseReference mAccountRef = mDatabase.getReference("users");
                            final DatabaseReference mRootRef = mDatabase.getReference();

                            mAccountRef.child(userProfile.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.child("wotnick").getValue(String.class) != null) {
                                        //  Уже указывал никнейм. Сообщим об этом.
                                        AlertDialog.Builder builder_inner = new AlertDialog.Builder(AppScreen.this)
                                                .setTitle("Ошибка")
                                                .setMessage("Вы уже указали этот никнейм ранее.")
                                                .setCancelable(true);
                                        builder_inner.create().show();
                                    }else{
                                        //  Никнейм ещё не был указан. Запишем его в профиль пользователя.
                                        mAccountRef.child(userProfile.getUid()).child("wotnick").setValue(mWotNickname);

                                        //  Проверим наличие ключа "status". Если нет - создаём.
                                        mRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(DataSnapshot dataSnapshot) {
                                                if (mRootRef.child("status") == null)
                                                    mRootRef.child("status").setValue(userProfile.getUid());
                                                /*
                                                *   Статусы
                                                *   0 - заявки не поступало (такого быть не должно)
                                                *   1 - заявка получена, обработка
                                                *   2 - заявка была получена и одобрена
                                                *   3 - заявка была получена и отклонена
                                                *   4 - резерв?
                                                */
                                                DatabaseReference mStatusRef = mRootRef.child("status").getRef();
                                                String key = mRootRef.child("status").child(userProfile.getUid()).push().getKey();

                                                Map<String, Object> properties = new HashMap<>();
                                                //  Статус заявки.
                                                properties.put("/" + key + "/status", 1);
                                                //  Временной отпечаток подачи заявки.
                                                properties.put("/" + key + "/timestart", System.currentTimeMillis() / 1000);
                                                //  Кол-во выводимого золота.
                                                properties.put("/" + key + "/clicks", goldCount.get(userProfile.getClicksCount())); //userProfile.getClicksCount());
                                                //  Игровой никнейм.
                                                properties.put("/" + key + "/wotnick", mWotNickname);
                                                //  Тип события.
                                                properties.put("/" + key + "/type", "withdraw");

                                                mStatusRef.child(userProfile.getUid()).updateChildren(properties, new DatabaseReference.CompletionListener() {
                                                    @Override
                                                    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                                        //  Запись в базу завершена. Можно продолжить.
                                                        //  Записали никнейм в бд, покажем уведомление об этом и закроем диалог.
                                                        reqGoldDialog.dismiss();

                                                        String[] positive_button_text = new String[] { "ОК", "Хорошо", "Понятно", "Ладно", "Годится", "Идёт", "Классно", "Круто" };
                                                        Random random = new Random();

                                                        AlertDialog.Builder builder_inner = new AlertDialog.Builder(AppScreen.this)
                                                                .setTitle("Успех")
                                                                .setMessage("Ваш запрос успешно отправлен!\nИзменение статуса заявки Вы можете отслеживать на экране ниже.")
                                                                .setPositiveButton(positive_button_text[random.nextInt(positive_button_text.length)], null)
                                                                .setCancelable(false);

                                                        builder_inner.create().show();

                                                        //  Обнулить количество золота, чтобы была возможность нажимать рекламу дальше.
                                                        mAccountRef.child(userProfile.getUid()).child("clicksCount").setValue(0);
                                                        mAccountRef.child(userProfile.getUid()).child("wotnick").removeValue();
                                                        mProgressText.setText(String.format(getString(R.string.clicksf), 0, progressBar.getMax()));
                                                        progressBar.setProgress(0);
                                                        userProfile.setClicksCount(0);

                                                        //  Запишем в статистику, что был отправлен запрос этим пользователем.
                                                        Bundle bundle = new Bundle();
                                                        bundle.putString("email", userProfile.getEmail());
                                                        bundle.putString("uid", userProfile.getUid());
                                                        bundle.putString("wotnick", mWotNickname);
                                                        mFirebaseAnalytics.logEvent("gold_req", bundle);

                                                        loadStatus();
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onCancelled(DatabaseError databaseError) {

                                            }
                                        });
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void loadClicks() {
        //  Загрузим из дб прогресс "кликов" этого пользователя.
        final DatabaseReference mAccountRef = mDatabase.getReference("users");
        mAccountRef.child(userProfile.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean bShowAd = false;

                if (dataSnapshot.getValue() != null) {
                    //  Пользователь существует. Получим количество кликов.
                    int clicksCount = dataSnapshot.child("clicksCount").getValue(Integer.class);
                    userProfile.setClicksCount(clicksCount);

                    progressBar.setProgress(clicksCount);
                    mProgressText.setText(String.format(getString(R.string.clicksf), clicksCount, progressBar.getMax()));

                    //  Загрузим события.
                    loadStatus();

                    if (clicksCount == progressBar.getMax()) {
                        //  Достигнуто нужное количество кликов. Отправить запрос на перечисление золота.
                        eventRequestGold(userProfile.getUid());
                    } else
                        bShowAd = true;
                } else {
                    //  Пользователя не существует. Создадим запись в бд.
                    UserDb u = new UserDb(userProfile.getEmail(), userProfile.getUid(), 0);
                    mAccountRef.child(userProfile.getUid()).setValue(u);

                    //  Кликов ещё не было - рекламу в любом случае показать.
                    bShowAd = true;
                }

                //  Показ рекламы.
                if (!bShowAd)
                    return;

                Log.v("E_LOADCLICKS", "Will show ad");

                if (dataSnapshot.hasChild("waittime")) {
                    //  Прячем рекламу и показываем таймер.
                    Log.v("E_RESUME", "Will resume showing ad for " + dataSnapshot.child("waittime").getValue(Integer.class) + " more milliseconds.");

                    resumeAdTimer(dataSnapshot.child("waittime").getValue(Integer.class));
                }

                AdView mAdView = (AdView) findViewById(R.id.adView);

                AdRequest adRequest = new AdRequest.Builder()
                        .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                        .addTestDevice("9D25B6375ABCC81CD13343B5FD877676")
                        .build();

                mAdView.loadAd(adRequest);
                mAdView.setAdListener(new AdListener());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void loadStatus() {
        //  Загружаем статус ожидания получения золота.
        //  UPD 08.11.16 - появились две вкладки - текущие события и архивные. При получении списка, отбрасываем архивные в отдельный список.
        final DatabaseReference mStatusRef = mDatabase.getReference("status");

        mStatusRef.child(userProfile.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(final DataSnapshot dataSnapshot) {
                //  Получим статус для указанного nickname.
                Iterable<DataSnapshot> children = dataSnapshot.getChildren();

                ListView mEventsList = (ListView) findViewById(R.id.mEventsList);
                ListView mEventsArchiveList = (ListView) findViewById(R.id.mEventsListArchive);
                List<Map<String, Object>> data_l = new ArrayList<>();
                List<Map<String, Object>> data_a = new ArrayList<>();

                SimpleAdapter adapter = new SimpleAdapter(AppScreen.this,
                        data_l,
                        android.R.layout.simple_list_item_1,
                        new String[] { "text", "type", "extra" },
                        new int[] { android.R.id.text1 }
                );

                SimpleAdapter adapter_a = new SimpleAdapter(AppScreen.this,
                        data_a,
                        android.R.layout.simple_list_item_1,
                        new String[] { "text", "type", "extra" },
                        new int[] { android.R.id.text1 }
                );

                mEventsList.setAdapter(adapter);
                mEventsArchiveList.setAdapter(adapter_a);

                mEventsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        //  При нажатии на элемент списка - показать детали процесса перевода золота.
                        HashMap<String, Object> item_data = (HashMap<String, Object>) parent.getItemAtPosition(position);

                        //  Видов событий несколько и для каждого предусмотрен свой экран.
                        switch (item_data.get("type").toString()) {
                            case "withdraw":
                                try {
                                    showWithdrawScreenInfo(item_data);
                                }catch (JSONException e) {
                                    e.printStackTrace();
                                    FirebaseCrash.report(e);
                                }
                                break;
                            case "message":
                                try {
                                    showMessageScreen(item_data);

                                    //  Отметить сообщение как прочитанное.
                                    if (dataSnapshot.child(((JSONObject)item_data.get("extra")).getString("key")).child("status").getValue(Integer.class) != 1)
                                        mStatusRef.child(userProfile.getUid()).child(((JSONObject)item_data.get("extra")).getString("key")).child("status").setValue(1);
                                }catch (JSONException e) {
                                    e.printStackTrace();
                                    FirebaseCrash.report(e);
                                }
                                break;
                            case "block":
                                try {
                                    showBlockedScreen(item_data);
                                }catch (JSONException e) {
                                    e.printStackTrace();
                                    FirebaseCrash.report(e);
                                }
                                break;
                        }
                    }
                });

                mEventsArchiveList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        //  При нажатии на элемент списка - показать детали процесса перевода золота.
                        HashMap<String, Object> item_data = (HashMap<String, Object>) parent.getItemAtPosition(position);

                        //  Видов событий несколько и для каждого предусмотрен свой экран.
                        switch (item_data.get("type").toString()) {
                            case "withdraw":
                                try {
                                    showWithdrawScreenInfo(item_data);
                                }catch (JSONException e) {
                                    e.printStackTrace();
                                    FirebaseCrash.report(e);
                                }
                                break;
                            case "message":
                                try {
                                    showMessageScreen(item_data);

                                    //  Отметить сообщение как прочитанное.
                                    if (dataSnapshot.child(((JSONObject)item_data.get("extra")).getString("key")).child("status").getValue(Integer.class) != 1)
                                        mStatusRef.child(userProfile.getUid()).child(((JSONObject)item_data.get("extra")).getString("key")).child("status").setValue(1);
                                }catch (JSONException e) {
                                    e.printStackTrace();
                                    FirebaseCrash.report(e);
                                }
                                break;
                            case "block":
                                try {
                                    showBlockedScreen(item_data);
                                }catch (JSONException e) {
                                    e.printStackTrace();
                                    FirebaseCrash.report(e);
                                }
                                break;
                        }
                    }
                });

                for (DataSnapshot data :
                        children) {

                    //  Добавляем событие в список событий.
                    Map<String, Object> info = new HashMap<>();
                    info.put("type", data.child("type").getValue(String.class));

                    switch (info.get("type").toString()) {
                        case "withdraw":
                            String[] sStatuses = new String[]{
                                    getString(R.string.event_withdraw_unknown),
                                    getString(R.string.event_withdraw_processing),
                                    getString(R.string.event_withdraw_completed),
                                    getString(R.string.event_withdraw_rejected)
                            };

                            JSONObject json_extraA = new JSONObject();
                            try {
                                json_extraA.put("wotnick", data.child("wotnick").getValue(String.class));
                                json_extraA.put("status", Integer.toString(data.child("status").getValue(Integer.class)));
                                json_extraA.put("clicks", Integer.toString(data.child("clicks").getValue(Integer.class)));
                                json_extraA.put("timestart", Long.toString(data.child("timestart").getValue(Long.class)));
                            }catch (JSONException e) {
                                //  Произошло что-то ужасное. Сообщим об этом.
                                e.printStackTrace();
                                FirebaseCrash.report(e);
                            }
                            info.put("extra", json_extraA);

                            info.put("text", "Вывод " + data.child("clicks").getValue(Integer.class) + " золота (" + sStatuses[data.child("status").getValue(Integer.class) >= sStatuses.length ? 0 : data.child("status").getValue(Integer.class)] + ")");
                            break;
                        case "message":
                            String[] read_status = new String[] {
                                    getString(R.string.event_message_unread),
                                    getString(R.string.event_message_read)
                            };
                            JSONObject json_extraB = new JSONObject();
                            try {
                                json_extraB.put("message", data.child("message").getValue(String.class));
                                json_extraB.put("status", Integer.toString(data.child("status").getValue(Integer.class)));
                                json_extraB.put("timestart", Long.toString(data.child("timestart").getValue(Long.class)));
                                json_extraB.put("key", data.getKey());
                            }catch (JSONException e) {
                                //  Произошло что-то ужасное. Сообщим об этом.
                                e.printStackTrace();
                                FirebaseCrash.report(e);
                            }
                            info.put("extra", json_extraB);

                            try {
                                info.put("text", "Новое сообщение от администратора (" + read_status[json_extraB.getInt("status")] + ")");
                            }catch (JSONException e) {
                                e.printStackTrace();
                                FirebaseCrash.report(e);
                            }
                            break;
                        case "block":

                            JSONObject json_extraC = new JSONObject();
                            try {
                                json_extraC.put("message", data.child("msg").getValue(String.class));
                                json_extraC.put("timeend", data.child("timeend").getValue(Long.class));
                                json_extraC.put("timestart", Long.toString(data.child("timestart").getValue(Long.class)));
                            }catch (JSONException e) {
                                //  Произошло что-то ужасное. Сообщим об этом.
                                e.printStackTrace();
                                FirebaseCrash.report(e);
                            }
                            info.put("extra", json_extraC);

                            info.put("text", "Ваш аккаунт временно заблокирован");
                            break;
                    }

                    //  TODO: возможность создания собственных фильтров, что нужно отправлять в "архив" - хранить это на сервере вместе с аккаунтом.
                    //  Если тип - вывод золота и статус 2 (исполнено) - то это в архив.
                    try {
                        Log.v("E_LOADSTATUS", "Should archive? (type=" + info.get("type").toString() + ";status=" + ((JSONObject)info.get("extra")).getString("status") + ")");

                        if (info.get("type").toString().equals("withdraw") &&
                                ((JSONObject) info.get("extra")).getString("status").equals("2"))
                            data_a.add(info);
                        else
                            data_l.add(info);
                    }catch (JSONException e) {
                        e.printStackTrace();
                        FirebaseCrash.report(e);

                        //  Тем не менее, нужно добавить что-то в список.
                        data_l.add(info);
                    }

                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void showWithdrawScreenInfo(HashMap<String, Object> params) throws JSONException {
        //  Покажем экран с деталями вывода золота.
        View v_detail_screen = View.inflate(AppScreen.this, R.layout.detail_screen, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(AppScreen.this)
                .setCancelable(true)
                .setView(v_detail_screen);

        TextView mDateAddedT = (TextView) v_detail_screen.findViewById(R.id.mDateAddedT);
        TextView mGoldCountT = (TextView) v_detail_screen.findViewById(R.id.mGoldCountT);
        TextView mCurrStatusT = (TextView) v_detail_screen.findViewById(R.id.mCurrStatusT);
        TextView mWotNicknameT = (TextView) v_detail_screen.findViewById(R.id.mWotNicknameT);

        builder.create().show();

        //  Нужные нам данные содержатся в объекте с ключом "extra", их надо привести к типу JSONObject.
        JSONObject item_data = (JSONObject) params.get("extra");

        //  Покажем статус.
        String mMessageText;
        int mMessageColor;
        switch (Integer.parseInt(item_data.get("status").toString())) {
            case 0:
                mMessageText = "Отсутствует";
                mMessageColor = Color.WHITE;

                break;
            case 1:
                mMessageText = "В обработке...";
                mMessageColor = Color.YELLOW;

                break;
            case 2:
                mMessageText = "Выполнено";
                mMessageColor = Color.GREEN;

                break;
            case 3:
                mMessageText = "Отклонено";
                mMessageColor = Color.RED;

                break;
            default:
                mMessageText = "Неопределено";
                mMessageColor = Color.WHITE;

                break;
        }

        mCurrStatusT.setText(mMessageText);
        mCurrStatusT.setTextColor(mMessageColor);

        mGoldCountT.setText(item_data.get("clicks").toString());

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Long.parseLong(item_data.get("timestart").toString()) * 1000);

        mDateAddedT.setText(format.format(calendar.getTime()));

        mWotNicknameT.setText(item_data.get("wotnick").toString());
    }

    public void showMessageScreen(HashMap<String, Object> params) throws JSONException {
        //  Покажем экран с сообщением от администратора.
        View v_detail_screen = View.inflate(AppScreen.this, R.layout.msg_screen, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(AppScreen.this)
                .setCancelable(true)
                .setView(v_detail_screen);

        //  Создаём нужные объекты находящиеся на экране.
        TextView mAdmMessageT = (TextView) v_detail_screen.findViewById(R.id.mAdmMessageT);

        builder.create().show();

        //  Нужные нам данные содержатся в объекте с ключом "extra", их надо привести к типу JSONObject.
        JSONObject item_data = (JSONObject) params.get("extra");

        mAdmMessageT.setText(item_data.getString("message"));
    }

    public void showBlockedScreen(HashMap<String, Object> params) throws JSONException {
        //  Покажем экран с информацией о блокировке.
        View v_detail_screen = View.inflate(AppScreen.this, R.layout.block_screen, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(AppScreen.this)
                .setCancelable(true)
                .setView(v_detail_screen);

        TextView mAdmBlockReason = (TextView) v_detail_screen.findViewById(R.id.mAdmBlockReason);
        TextView mAdmBlockTimeST = (TextView) v_detail_screen.findViewById(R.id.mAdmBlockTimeST);
        TextView mAdmBlockTimeET = (TextView) v_detail_screen.findViewById(R.id.mAdmBlockTimeET);

        builder.create().show();

        //  Нужные нам данные содержатся в объекте с ключом "extra", их надо привести к типу JSONObject.
        JSONObject item_data = (JSONObject) params.get("extra");

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Long.parseLong(item_data.get("timestart").toString()) * 1000);

        mAdmBlockReason.setText(item_data.getString("message"));
        mAdmBlockTimeST.setText(format.format(calendar.getTime()));

        calendar.setTimeInMillis(Long.parseLong(item_data.get("timeend").toString()) * 1000);
        mAdmBlockTimeET.setText(format.format(calendar.getTime()));
    }

    class AdListener extends com.google.android.gms.ads.AdListener {
        final String[] errors = {
                "ERROR_CODE_INTERNAL_ERROR",
                "ERROR_CODE_INVALID_REQUEST",
                "ERROR_CODE_NETWORK_ERROR",
                "ERROR_CODE_NO_FILL"
        };

        @Override
        public void onAdLoaded() {
            //  Реклама получена с сервера.
            Log.v("E_AD_LOADED", "onAdLoaded");
        }

        @Override
        public void onAdFailedToLoad(int errorCode) {
            //  Реклама не смогла загрузиться.
            Log.v("E_AD_FAILED", "onAdFailedToLoad:" + errorCode);

            //  Уведомить пользователя об этом.
            Toast.makeText(AppScreen.this, "Не удалось загрузить рекламу :(\nЭто временно, вернитесь через несколько часов\n(" + errors[errorCode] + ")", Toast.LENGTH_LONG).show();

            Bundle bundle = new Bundle();
            bundle.putString("error_code", errors[errorCode]);
            mFirebaseAnalytics.logEvent("ad_failed_load", bundle);
            FirebaseCrash.report(new AdException(errors[errorCode]));
        }

        @Override
        public void onAdOpened() {
            //  Реклама была нажата пользователем.
            Log.v("E_AD_OPENED", "onAdOpened");

            try {
                Bundle bundle = new Bundle();
                mFirebaseAnalytics.setUserId(userProfile.getUid());
                mFirebaseAnalytics.logEvent("ad_open", bundle);
            }catch (NullPointerException e) {
                //  Пользователь не авторизован. Такого быть не должно.
            }
        }

        @Override
        public void onAdClosed() {
            //  Пользователь хочет вернуться обратно к приложению посмотрев рекламу.
            Log.v("E_AD_CLOSED", "onAdClosed");

            //  Запустить таймер для этого рекламного блока.
            final AdView adView = (AdView) findViewById(R.id.adView);
            final TextView tvCountdown = (TextView) findViewById(R.id.tvTimerFirst);

            adView.setVisibility(View.INVISIBLE);

            tvCountdown.setVisibility(View.VISIBLE);
            tvCountdown.setText("Новый просмотр через\n\t30 секунд");

            if (timer != null) {
                //  Уже есть таймер? Что-то не так.
                timer.cancel();
                timer = null;
            }

            timer = new CountDownTimer(30000, 1000) {
                public void onTick(long millisUntilFinished) {
                    timeLeft = millisUntilFinished;
                    String timerString = getResources().getQuantityString(R.plurals.timeout_plurals, (int)(millisUntilFinished / 1000), (int)(millisUntilFinished / 1000));
                    tvCountdown.setText("Новый просмотр через\n\t" + timerString);
                }

                public void onFinish() {
                    timeLeft = 0;
                    adView.setVisibility(View.VISIBLE);
                    tvCountdown.setVisibility(View.INVISIBLE);
                    mDatabase.getReference("users").child(userProfile.getUid()).child("waittime").removeValue();
                }
            };

            timer.start();

            try {
                Bundle bundle = new Bundle();
                mFirebaseAnalytics.setUserId(userProfile.getUid());
                mFirebaseAnalytics.logEvent("ad_seen", bundle);

                //  Записать в бд новое количество просмотров рекламы пользователем.
                final DatabaseReference mAccountRef = mDatabase.getReference("users");

                userProfile.incClicksCount();
                int clicksCount = userProfile.getClicksCount();

                mAccountRef.child(userProfile.getUid()).child("clicksCount").setValue(clicksCount);

                progressBar.setProgress(clicksCount);
                mProgressText.setText(String.format(getString(R.string.clicksf), clicksCount, progressBar.getMax()));
            }catch (NullPointerException e) {
                //  Пользователь не авторизован. Такого быть не должно.
            }
        }

        @Override
        public void onAdLeftApplication() {
            //  Реклама вызвала переключение с приложения на что-то иное.
            Log.v("E_AD_LEFTAPP", "onAdLeftApplication");
        }
    }

    public class AdException extends Exception {

        AdException(String message) {
            super(message);
        }
    }
}