package com.example.mobileproject.model;

import java.util.Date;

    public class Message {

        private String messageId;
        private String conversationId;
        private String senderId;
        private String content;
        private Date sentAt;
        private Date readAt;

        // Constructor rỗng
        public Message() {
        }

        // Constructor đầy đủ
        public Message(
                String messageId,
                String conversationId,
                String senderId,
                String content,
                Date sentAt,
                Date readAt
        ) {
            this.messageId = messageId;
            this.conversationId = conversationId;
            this.senderId = senderId;
            this.content = content;
            this.sentAt = sentAt;
            this.readAt = readAt;
        }

        // Gửi tin nhắn
        public void send() {
            if (conversationId == null || conversationId.trim().isEmpty()
                    || senderId == null || senderId.trim().isEmpty()
                    || content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Thông tin tin nhắn không được để trống."
                );
            }

            if (sentAt == null) {
                sentAt = new Date();
            }
        }

        // Đánh dấu đã đọc
        public void markRead() {
            if (readAt == null) {
                readAt = new Date();
            }
        }

        // Kiểm tra tin nhắn đã đọc chưa
        public boolean isRead() {
            return readAt != null;
        }

        // Getter / Setter
        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public String getSenderId() {
            return senderId;
        }

        public void setSenderId(String senderId) {
            this.senderId = senderId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Date getSentAt() {
            return sentAt == null ? null : new Date(sentAt.getTime());
        }

        public void setSentAt(Date sentAt) {
            this.sentAt = sentAt == null
                    ? null : new Date(sentAt.getTime());
        }

        public Date getReadAt() {
            return readAt == null ? null : new Date(readAt.getTime());
        }

        public void setReadAt(Date readAt) {
            this.readAt = readAt == null
                    ? null : new Date(readAt.getTime());
        }
    }

