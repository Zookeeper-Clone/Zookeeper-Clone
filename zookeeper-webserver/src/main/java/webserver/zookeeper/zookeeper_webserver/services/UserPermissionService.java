package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import webserver.zookeeper.zookeeper_webserver.dto.UpdatePermissionRequest;
import webserver.zookeeper.zookeeper_webserver.dto.UserDTO;

@Service
public class UserPermissionService {

    @Autowired
    private ZookeeperClient zkClient;

    public UserPermissionService(ZookeeperClient zkClient) {
        this.zkClient = zkClient;
    }

    public UserDTO getUserPermissions(String email) {
        ZookeeperClient.PermissionsResult result =
                zkClient.getUserPermissionsByEmail(email);

        if (!result.isSuccess()) {
            throw new RuntimeException(result.getMessage());
        }

        var perms = result.getUserPermissions();

        UserDTO dto = new UserDTO();
        dto.setEmail(email);
        dto.setAdmin(perms.getIsAdmin());

        // UI mapping rule (you already use this on frontend)
        dto.setPermission(perms.getIsAdmin() ? "edit" : "read");

        return dto;
    }

    public void updatePermission(String email, UpdatePermissionRequest req) {
        if (req.getAdmin() != null) {
            zkClient.setIsAdmin(email, req.getAdmin());
        }
    }
}
