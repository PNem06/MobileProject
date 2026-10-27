package com.example.mobileproject.wallet;

import static org.junit.Assert.*;
import android.app.Application;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class WalletSnapshotContractTest {
    private JSONObject snapshot(String viewer, String owner, boolean admin) throws Exception {
        return new JSONObject().put("apiVersion", 3).put("postReservationVersion", 1)
                .put("sharedAdminWalletVersion", 1).put("userId", viewer)
                .put("walletOwnerId", owner).put("isAdmin", admin)
                .put("walletScope", admin ? "SYSTEM" : "PERSONAL")
                .put("wallet", new JSONObject().put("userId", owner).put("available", 2000).put("held", 12000));
    }
    @Test public void secondaryAdminCanViewTheTreasuryWithoutPretendingToBeItsOwner() throws Exception {
        assertEquals("", WalletSnapshotContract.error(snapshot("admin2", "treasury", true), "admin2"));
    }
    @Test public void normalUserCannotReceiveAnotherUsersWallet() throws Exception {
        assertFalse(WalletSnapshotContract.error(snapshot("customer", "treasury", false), "customer").isEmpty());
    }
    @Test public void cachedSnapshotFromPreviousLoginIsRejected() throws Exception {
        assertFalse(WalletSnapshotContract.error(snapshot("admin1", "treasury", true), "admin2").isEmpty());
    }
    @Test public void oldBackendCannotSilentlyDisplayASeparateAdminBalance() throws Exception {
        JSONObject value = snapshot("admin2", "admin2", false);
        value.remove("sharedAdminWalletVersion");
        assertTrue(WalletSnapshotContract.error(value, "admin2").contains("1.4.1"));
    }
    @Test public void documentOwnerMustMatchTheAdvertisedWalletOwner() throws Exception {
        JSONObject value = snapshot("admin2", "treasury", true);
        value.getJSONObject("wallet").put("userId", "other");
        assertFalse(WalletSnapshotContract.error(value, "admin2").isEmpty());
    }
    @Test public void legacyWalletWithoutRedundantUserIdKeepsWorkingWithExplicitOwner() throws Exception {
        JSONObject value = snapshot("customer", "customer", false);
        value.getJSONObject("wallet").remove("userId");
        assertEquals("", WalletSnapshotContract.error(value, "customer"));
    }
}
