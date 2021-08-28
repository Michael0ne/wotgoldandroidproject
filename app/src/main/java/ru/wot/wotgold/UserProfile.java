package ru.wot.wotgold;

import android.content.Context;
import android.content.SharedPreferences;

final class UserProfile {
    private static UserProfile instance = null;

    private static SharedPreferences sharedPreferences;

    private static String email, password, uid, remember = null;
    private static int clicksCount = 0;

    private static Context context;

    private UserProfile(Context c) {
        context = c;
        sharedPreferences = context.getSharedPreferences("WOTgold", Context.MODE_PRIVATE);
    }

    static UserProfile getInstance(Context c) {
        if (instance == null)
            instance = new UserProfile(c);

        return instance;
    }

    public static String getEmail() { return email; }
    static String getPassword() { return password; }
    static String getUid() { return uid; }
    static Boolean getRemember() { return remember == "true"; }
    static String getEmailName() { return email.substring(0, email.indexOf('@')); }
    static int getClicksCount() { return clicksCount; }

    public static void setEmail(String e) { email = e; }
    static void setPassword(String p) { password = p; }
    static void setUid(String u) { uid = u; }
    static void setRemember(boolean r) { remember = r ? "true" : "false"; }
    static void setClicksCount(int c) { clicksCount = c; }
    static void incClicksCount() { clicksCount++; }

    static boolean load() {
        String e = sharedPreferences.getString("email", null);
        String p = sharedPreferences.getString("password", null);
        boolean r = sharedPreferences.getBoolean("remember", false);

        if (e != null && p != null) {
            //  Что-то есть. Загрузим это.
            setEmail(e);
            setPassword(p);
            setRemember(r);

            return true;
        }else
            //  Ничего нет.
            return false;
    }

    static void save() {
        //  Сохранить всё, что есть в классе.
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("email", getEmail());
        editor.putString("password", getPassword());
        editor.putBoolean("remember", getRemember());

        editor.apply();
    }

    static void clear() {
        //  Очистить все данные.
        setEmail(null);
        setPassword(null);
        setUid(null);

        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.remove("email");
        editor.remove("password");
        editor.remove("remember");

        editor.apply();
    }
}
