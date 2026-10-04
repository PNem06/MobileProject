package com.example.mobileproject.model;

import java.util.Date;

public class Block {

    private String blockId;
    private String blockerId;
    private String blockedUserId;
    private Date createdAt;

    // Constructor rỗng
    public Block() {
    }

    // Constructor đầy đủ
    public Block(String blockId, String blockerId,
                 String blockedUserId, Date createdAt) {
        this.blockId = blockId;
        this.blockerId = blockerId;
        this.blockedUserId = blockedUserId;
        this.createdAt = createdAt;
    }

    // Phương thức Block
    public void block() {
        System.out.println("User " + blockerId
                + " đã block user " + blockedUserId);
    }

    // Phương thức Unblock
    public void unblock() {
        System.out.println("User " + blockerId
                + " đã unblock user " + blockedUserId);
    }

    // Getter / Setter

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getBlockerId() {
        return blockerId;
    }

    public void setBlockerId(String blockerId) {
        this.blockerId = blockerId;
    }

    public String getBlockedUserId() {
        return blockedUserId;
    }

    public void setBlockedUserId(String blockedUserId) {
        this.blockedUserId = blockedUserId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}