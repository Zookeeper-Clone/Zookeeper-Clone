package webserver.zookeeper.zookeeper_webserver.dto;

import java.util.List;

public class UpdatePermissionRequest {

    private String directory;
    private List<String> permissions; // create, read, update, delete

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
