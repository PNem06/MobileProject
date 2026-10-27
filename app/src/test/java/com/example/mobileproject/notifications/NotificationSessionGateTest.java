package com.example.mobileproject.notifications;

import static org.junit.Assert.*;
import org.junit.Test;

public class NotificationSessionGateTest {
    @Test public void aSavedFirebaseUserDoesNotEnableNotificationsOnLogin() {
        NotificationSessionGate gate = new NotificationSessionGate();
        gate.authenticationChanged("old"); gate.screenChanged(false);
        assertEquals("", gate.activeUid());
    }
    @Test public void loginScreenStaysSilentEvenWhileSignInTaskSucceeds() {
        NotificationSessionGate gate = new NotificationSessionGate();
        gate.authenticationChanged("A"); assertTrue(gate.loginValidated("A"));
        assertEquals("", gate.activeUid());
        gate.screenChanged(true); assertEquals("A", gate.activeUid());
    }
    @Test public void enteringAnAuthenticatedScreenStillRequiresProfileValidation() {
        NotificationSessionGate gate = new NotificationSessionGate();
        gate.authenticationChanged("A"); gate.screenChanged(true);
        assertEquals("", gate.activeUid());
        gate.loginValidated("A"); assertEquals("A", gate.activeUid());
    }
    @Test public void signOutImmediatelyDisablesThePreviousUser() {
        NotificationSessionGate gate = loggedIn("A");
        gate.authenticationChanged(null); assertEquals("", gate.activeUid());
        assertFalse(gate.validated());
    }
    @Test public void changingAccountsRequiresANewSuccessfulLogin() {
        NotificationSessionGate gate = loggedIn("A");
        gate.authenticationChanged("B"); assertEquals("", gate.activeUid());
        assertFalse(gate.loginValidated("A"));
        gate.loginValidated("B"); assertEquals("B", gate.activeUid());
    }
    @Test public void delayedAuthenticationCallbackForTheSameUserKeepsValidation() {
        NotificationSessionGate gate = loggedIn("A");
        gate.authenticationChanged("A"); assertEquals("A", gate.activeUid());
    }
    @Test public void goingToBackgroundDisablesDeliveryUntilResumed() {
        NotificationSessionGate gate = loggedIn("A");
        gate.screenChanged(false); assertEquals("", gate.activeUid());
        gate.screenChanged(true); assertEquals("A", gate.activeUid());
    }
    @Test public void returningToLoginClearsValidationForTheSavedUid() {
        NotificationSessionGate gate = loggedIn("A");
        gate.clearValidation(); gate.screenChanged(false);
        gate.screenChanged(true); assertEquals("", gate.activeUid());
    }
    @Test public void emptyOrWrongUidCannotAuthorizeDelivery() {
        NotificationSessionGate gate = new NotificationSessionGate();
        assertFalse(gate.loginValidated(null)); assertFalse(gate.loginValidated(""));
        gate.authenticationChanged("B"); assertFalse(gate.loginValidated("A"));
    }
    private NotificationSessionGate loggedIn(String uid) {
        NotificationSessionGate gate = new NotificationSessionGate();
        gate.authenticationChanged(uid); gate.loginValidated(uid); gate.screenChanged(true);
        return gate;
    }
}
