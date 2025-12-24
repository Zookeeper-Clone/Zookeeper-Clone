package webserver.zookeeper.zookeeper_webserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import webserver.zookeeper.zookeeper_webserver.services.UserPermissionService;
import webserver.zookeeper.zookeeper_webserver.dto.UpdatePermissionRequest;
import webserver.zookeeper.zookeeper_webserver.dto.UserDTO;

@RestController
@RequestMapping("/users")
public class UserPermissionController {

    @Autowired
    private UserPermissionService service;

    @GetMapping("/{email}/permissions")
    public UserDTO getPermissions(@PathVariable String email) {
        return service.getUserPermissions(email);
    }

    @PutMapping("/{email}/permission")
    public ResponseEntity<Void> updatePermission(
            @PathVariable String email,
            @RequestBody UpdatePermissionRequest request
    ) {
        service.updatePermission(email, request);
        return ResponseEntity.ok().build();
    }
}
