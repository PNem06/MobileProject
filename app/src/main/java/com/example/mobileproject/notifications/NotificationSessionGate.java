package com.example.mobileproject.notifications;

/** A restored Firebase user alone is not a completed application login. */
public final class NotificationSessionGate {
    private String authenticatedUid = "";
    private String validatedUid = "";
    private boolean authenticatedScreen;

    public void authenticationChanged(String uid) {
        String next = uid == null ? "" : uid;
        if (!next.equals(authenticatedUid)) validatedUid = "";
        authenticatedUid = next;
    }

    public boolean loginValidated(String uid) {
        if (uid == null || uid.isEmpty() || !uid.equals(authenticatedUid)) return false;
        validatedUid = uid;
        return true;
    }

    public void screenChanged(boolean allowed) { authenticatedScreen = allowed; }
    public void clearValidation() { validatedUid = ""; }
    public boolean validated() {
        return !authenticatedUid.isEmpty() && authenticatedUid.equals(validatedUid);
    }
    public String activeUid() { return authenticatedScreen && validated() ? authenticatedUid : ""; }
}
