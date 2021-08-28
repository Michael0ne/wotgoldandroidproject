package ru.wot.wotgold;

public class UserDb {
    public String email;
    public String uid;
    public int clicksCount;

    public UserDb() {}

    public UserDb(String email, String uid, int clicksCount) {
        this.email = email;
        this.uid = uid;
        this.clicksCount = clicksCount;
    }
}
