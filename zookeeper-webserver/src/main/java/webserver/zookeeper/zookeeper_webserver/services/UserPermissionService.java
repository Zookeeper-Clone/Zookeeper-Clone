package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import webserver.zookeeper.zookeeper_webserver.dto.UpdatePermissionRequest;
import webserver.zookeeper.zookeeper_webserver.dto.UserDTO;

import java.util.HashMap;
import java.util.List;

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
        dto.setPermissions(perms.getDirectoryPermissionsMap());
        System.out.println(email);
        System.out.println(perms.getIsAdmin());
        System.out.println(perms.getDirectoryPermissionsMap());
        return dto;
    }
    public void setIsAdmin(String email){
        zkClient.setIsAdmin(email, true);
    }
    public void setCanCreateDirectory(String email){
        zkClient.setCanCreateDirectories(email, true);
    }
    public void updatePermission(String email, UpdatePermissionRequest req) {
        int permissionNum = getPermissionNum(req);
        HashMap<String, Integer> permissionsMap = new HashMap<>();
        permissionsMap.put(req.getDirectory(),permissionNum);
        zkClient.setDirectoryPermissions(email, permissionsMap);
    }

    private static int getPermissionNum(UpdatePermissionRequest req) {
        List<String> permissions = req.getPermissions();
        int permissionNum = 0;
        for(String permission : permissions){
            if(permission.equals("create"))
                permissionNum+=1;
            if(permission.equals("read"))
                permissionNum+=2;
            if(permission.equals("update"))
                permissionNum+=4;
            if(permission.equals("delete"))
                permissionNum+=8;
        }
        return permissionNum;
    }
}