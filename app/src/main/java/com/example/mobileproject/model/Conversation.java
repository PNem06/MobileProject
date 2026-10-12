package com.example.mobileproject.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Conversation {

    private String conversationId;
    private String participant1Id;
    private String participant2Id;
    private Date createdAt;
    private Date lastMessageAt;
    private final List<Message> messages = new ArrayList<>();

    public Conversation() {
    }

    public Conversation(String conversationId,
                        String participant1Id,
                        String participant2Id,
                        Date createdAt,
                        Date lastMessageAt) {
        this.conversationId = conversationId;
        this.participant1Id = participant1Id;
        this.participant2Id = participant2Id;
        setCreatedAt(createdAt);
        setLastMessageAt(lastMessageAt);
    }

    public void addMessage(Message message) {
        validate();

        if (message == null) {
            throw new IllegalArgumentException(
                    "Tin nhắn không được null."
            );
        }

        if (!conversationId.equals(message.getConversationId())) {
            throw new IllegalArgumentException(
                    "Tin nhắn không thuộc hội thoại này."
            );
        }

        if (!hasParticipant(message.getSenderId())) {
            throw new IllegalArgumentException(
                    "Người gửi không thuộc hội thoại."
            );
        }

        for (Message item : messages) {
            if (item == message
                    || (message.getMessageId() != null
                    && !message.getMessageId().trim().isEmpty()
                    && message.getMessageId().equals(item.getMessageId()))) {
                throw new IllegalArgumentException(
                        "Tin nhắn đã có trong hội thoại."
                );
            }
        }

        message.send();
        messages.add(message);

        Date sentAt = message.getSentAt();

        if (lastMessageAt == null || sentAt.after(lastMessageAt)) {
            setLastMessageAt(sentAt);
        }
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public boolean hasParticipant(String userId) {
        return userId != null
                && !userId.trim().isEmpty()
                && (userId.equals(participant1Id)
                || userId.equals(participant2Id));
    }

    public String getOtherParticipantId(String userId) {
        validate();

        if (!hasParticipant(userId)) {
            throw new IllegalArgumentException(
                    "Người dùng không thuộc hội thoại."
            );
        }

        return userId.equals(participant1Id)
                ? participant2Id
                : participant1Id;
    }

    private void validate() {
        if (conversationId == null
                || conversationId.trim().isEmpty()
                || participant1Id == null
                || participant1Id.trim().isEmpty()
                || participant2Id == null
                || participant2Id.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Phải có mã hội thoại và hai người dùng."
            );
        }

        if (participant1Id.equals(participant2Id)) {
            throw new IllegalArgumentException(
                    "Hai người dùng phải khác nhau."
            );
        }
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getParticipant1Id() {
        return participant1Id;
    }

    public void setParticipant1Id(String participant1Id) {
        this.participant1Id = participant1Id;
    }

    public String getParticipant2Id() {
        return participant2Id;
    }

    public void setParticipant2Id(String participant2Id) {
        this.participant2Id = participant2Id;
    }

    public Date getCreatedAt() {
        return createdAt == null
                ? null
                : new Date(createdAt.getTime());
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt == null
                ? null
                : new Date(createdAt.getTime());
    }

    public Date getLastMessageAt() {
        return lastMessageAt == null
                ? null
                : new Date(lastMessageAt.getTime());
    }

    public void setLastMessageAt(Date lastMessageAt) {
        this.lastMessageAt = lastMessageAt == null
                ? null
                : new Date(lastMessageAt.getTime());
    }
}