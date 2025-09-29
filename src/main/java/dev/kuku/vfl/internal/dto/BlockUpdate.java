package dev.kuku.vfl.internal.dto;

public class BlockUpdate {
    private Long enteredAt;
    private Long exitedAt;
    private Long returnedAt;
    private String exitMessage;
    private String returnMessage;

    public BlockUpdate(Long enteredAt, Long exitedAt, Long returnedAt, String exitMessage, String returnMessage) {
        this.enteredAt = enteredAt;
        this.exitedAt = exitedAt;
        this.returnedAt = returnedAt;
        this.exitMessage = exitMessage;
        this.returnMessage = returnMessage;
    }

    public BlockUpdate() {
    }

    public Long getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Long enteredAt) {
        this.enteredAt = enteredAt;
    }

    public Long getExitedAt() {
        return exitedAt;
    }

    public void setExitedAt(Long exitedAt) {
        this.exitedAt = exitedAt;
    }

    public Long getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(Long returnedAt) {
        this.returnedAt = returnedAt;
    }

    public String getExitMessage() {
        return exitMessage;
    }

    public void setExitMessage(String exitMessage) {
        this.exitMessage = exitMessage;
    }

    public String getReturnMessage() {
        return returnMessage;
    }

    public void setReturnMessage(String returnMessage) {
        this.returnMessage = returnMessage;
    }
}
