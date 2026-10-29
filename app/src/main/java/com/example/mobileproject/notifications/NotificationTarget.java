package com.example.mobileproject.notifications;

/** Routing by explicit IDs; a request ID is never treated as a post ID. */
public final class NotificationTarget {
    public enum Kind { POST, REQUEST, CHAT, WALLET, INFO }
    public final Kind kind;
    public final String id, collection;
    private NotificationTarget(Kind kind, String id, String collection) {
        this.kind = kind; this.id = id; this.collection = collection;
    }
    public static NotificationTarget resolve(Notice notice) {
        String type = notice.type();
        if (type.equals("chat_message") || type.equals("chat"))
            return new NotificationTarget(Kind.CHAT, notice.text("senderId", "partnerId", "otherUserId"), notice.text("conversationId"));
        String exchange = notice.text("exchangeId", "requestId");
        if ((notice.flag("walletManaged") || type.equals("wallet")) && !notice.text("withdrawalId").isEmpty())
            return new NotificationTarget(Kind.WALLET, notice.text("withdrawalId"), "withdrawals");
        if (notice.flag("walletManaged") || type.equals("wallet"))
            return new NotificationTarget(Kind.WALLET, exchange, "");
        if (!exchange.isEmpty() && (type.contains("exchange") || type.contains("request"))) {
            String source = notice.text("sourceCollection", "collection");
            if (!source.equals("exchanges") && !source.equals("exchangeRequests"))
                source = notice.text("exchangeId").isEmpty() ? "exchangeRequests" : "exchanges";
            return new NotificationTarget(Kind.REQUEST, exchange, source);
        }
        String post = notice.text("postId", "targetPostId", "offeredPostId");
        if (!post.isEmpty()) return new NotificationTarget(Kind.POST, post, "");
        if (!exchange.isEmpty()) return new NotificationTarget(Kind.REQUEST, exchange, "exchanges");
        return new NotificationTarget(Kind.INFO, "", "");
    }
}
