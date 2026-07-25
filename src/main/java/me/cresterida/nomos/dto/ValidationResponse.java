package me.cresterida.nomos.dto;

public class ValidationResponse {

    private boolean allowed;
    private String reason;
    private String appId;

    public ValidationResponse() {}

    public ValidationResponse(boolean allowed, String reason, String appId) {
        this.allowed = allowed;
        this.reason = reason;
        this.appId = appId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }
}
