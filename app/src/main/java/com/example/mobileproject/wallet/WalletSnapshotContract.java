package com.example.mobileproject.wallet;

import org.json.JSONObject;

/** Keep the authenticated viewer separate from the owner of a shared wallet. */
public final class WalletSnapshotContract {
    private WalletSnapshotContract() { }

    public static String error(JSONObject data, String viewerUid) {
        if (data == null || data.optInt("apiVersion") < 3
                || data.optInt("postReservationVersion") < 1
                || data.optInt("sharedAdminWalletVersion") < 1) {
            return "Máy chủ cần cập nhật backend 1.4.1 để dùng ví quản trị chung. Kiểm tra /health.";
        }
        if (viewerUid == null || viewerUid.isEmpty() || !viewerUid.equals(data.optString("userId"))) {
            return "Dữ liệu ví không thuộc phiên đăng nhập hiện tại. Mở lại ví.";
        }
        String owner = data.optString("walletOwnerId");
        boolean admin = data.optBoolean("isAdmin");
        JSONObject wallet = data.optJSONObject("wallet");
        if (owner.isEmpty() || wallet == null
                || !(admin ? "SYSTEM" : "PERSONAL").equals(data.optString("walletScope"))
                || (!admin && !owner.equals(viewerUid))
                || (!wallet.optString("userId").isEmpty() && !owner.equals(wallet.optString("userId")))) {
            return "Thông tin chủ ví chưa nhất quán. Làm mới trạng thái hoặc kiểm tra backend.";
        }
        return "";
    }
}
