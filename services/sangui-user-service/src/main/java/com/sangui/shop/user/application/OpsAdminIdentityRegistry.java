package com.sangui.shop.user.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sangui.security.ops")
public class OpsAdminIdentityRegistry {

    private List<AdminIdentity> admins = new ArrayList<>();

    public boolean isOpsAdmin(Long shopId, String username) {
        if (shopId == null || username == null) {
            return false;
        }
        return admins.stream().anyMatch(identity -> identity.matches(shopId, username));
    }

    public List<AdminIdentity> getAdmins() {
        return admins;
    }

    public void setAdmins(List<AdminIdentity> admins) {
        this.admins = admins == null ? new ArrayList<>() : new ArrayList<>(admins);
    }

    public static class AdminIdentity {

        private Long shopId;
        private String username;

        public Long getShopId() {
            return shopId;
        }

        public void setShopId(Long shopId) {
            this.shopId = shopId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        private boolean matches(Long expectedShopId, String expectedUsername) {
            if (shopId == null || username == null) {
                return false;
            }
            return shopId.equals(expectedShopId) && username.trim().equals(expectedUsername.trim());
        }
    }
}
