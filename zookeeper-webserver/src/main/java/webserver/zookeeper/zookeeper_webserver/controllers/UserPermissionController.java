package webserver.zookeeper.zookeeper_webserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import webserver.zookeeper.zookeeper_webserver.services.UserPermissionService;
import webserver.zookeeper.zookeeper_webserver.dto.UpdatePermissionRequest;
import webserver.zookeeper.zookeeper_webserver.dto.UserDTO;
import webserver.zookeeper.zookeeper_webserver.services.ZookeeperService;

@RestController
@RequestMapping("/users")
public class UserPermissionController {

    @Autowired
    private ZookeeperService zookeeperService;
    @Autowired
    private UserPermissionService service;

    @GetMapping("/{email}/permissions")
    public UserDTO getPermissions(@PathVariable String email, @CookieValue("SESSION_TOKEN") String sessionToken)
    {
        System.out.println("######################################3");
        zookeeperService.setToken(sessionToken);
        return service.getUserPermissions(email);
    }

    @PutMapping("/{email}/permissions")
    public ResponseEntity<Void> updatePermission(
            @PathVariable String email,
            @RequestBody UpdatePermissionRequest request,
            @CookieValue("SESSION_TOKEN") String sessionToken
    ) {
        zookeeperService.setToken(sessionToken);
        service.updatePermission(email, request);
        return ResponseEntity.ok().build();
    }
    @PutMapping("/{email}/set-can-create-directory")
    public ResponseEntity<Void> setCanCreateDirectory(
            @PathVariable String email,
            @CookieValue("SESSION_TOKEN") String sessionToken
    ){
        zookeeperService.setToken(sessionToken);
        service.setCanCreateDirectory(email);
        return ResponseEntity.ok().build();
    }
    @PutMapping("/{email}/set-is-admin")
    public ResponseEntity<Void> setIsAdmin(
            @PathVariable String email,
            @CookieValue("SESSION_TOKEN") String sessionToken
    ){
        zookeeperService.setToken(sessionToken);
        service.setIsAdmin(email);
        return ResponseEntity.ok().build();
    }
}