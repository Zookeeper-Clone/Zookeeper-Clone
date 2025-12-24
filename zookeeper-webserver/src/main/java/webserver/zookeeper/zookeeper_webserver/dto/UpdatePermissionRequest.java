package webserver.zookeeper.zookeeper_webserver.dto;

public class UpdatePermissionRequest {
    private String permission; // "read" | "edit"
    private Boolean isAdmin;

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Boolean getAdmin() {
        return isAdmin;
    }

    public void setAdmin(Boolean admin) {
        isAdmin = admin;
    }

}
