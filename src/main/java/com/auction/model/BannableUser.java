package com.auction.model;

public abstract class BannableUser extends User {

    public enum BanType { TEMPORARY, PERMANENT }

    private BanType banType;
    private long banExpiryUnix;

    public BannableUser(String id, String username, String password) {
        super(id, username, password);
    }

    public void banTemporary(long durationSeconds) {
        this.banType = BanType.TEMPORARY;
        this.banExpiryUnix = System.currentTimeMillis() / 1000L + durationSeconds;
    }

    public void banPermanent() {
        this.banType = BanType.PERMANENT;
        this.banExpiryUnix = 0;
    }

    public void unban() {
        this.banType = null;
        this.banExpiryUnix = 0;
    }

    public boolean isBanned() {
        if (banType == null) return false;
        if (banType == BanType.PERMANENT) return true;
        long now = System.currentTimeMillis() / 1000L;
        if (now >= banExpiryUnix) {
            unban();
            return false;
        }
        return true;
    }

    public BanType getBanType() { return banType; }

    public long getBanExpiryUnix() { return banExpiryUnix; }
}
