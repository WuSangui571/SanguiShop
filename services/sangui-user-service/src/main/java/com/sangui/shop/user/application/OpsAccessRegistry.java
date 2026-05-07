package com.sangui.shop.user.application;

import com.sangui.shop.common.security.SanguiPermissionConstants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sangui.security.ops")
public class OpsAccessRegistry {

    private static final List<String> LEGACY_ADMIN_PERMISSIONS = List.of(
            SanguiPermissionConstants.OPS_COMPENSATION_ADMIN,
            SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN,
            SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN,
            SanguiPermissionConstants.LOGISTICS_FULFILLMENT_ADMIN
    );

    private List<OpsAccessBinding> bindings = new ArrayList<>();
    private List<AdminIdentity> admins = new ArrayList<>();

    public Optional<ResolvedOpsAccess> resolve(Long shopId, String username) {
        String normalizedUsername = normalizeUsername(username);
        if (shopId == null || normalizedUsername == null) {
            return Optional.empty();
        }
        return bindings.stream()
                .filter(binding -> binding.matches(shopId, normalizedUsername))
                .findFirst()
                .map(binding -> new ResolvedOpsAccess(
                        binding.getShopId(),
                        binding.getUsername().trim(),
                        normalizePermissions(binding.getPermissions())
                ))
                .or(() -> admins.stream()
                        .filter(identity -> identity.matches(shopId, normalizedUsername))
                        .findFirst()
                        .map(identity -> new ResolvedOpsAccess(
                                identity.getShopId(),
                                identity.getUsername().trim(),
                                LEGACY_ADMIN_PERMISSIONS
                        )));
    }

    public List<OpsAccessBinding> getBindings() {
        return bindings;
    }

    public void setBindings(List<OpsAccessBinding> bindings) {
        this.bindings = bindings == null ? new ArrayList<>() : new ArrayList<>(bindings);
    }

    public boolean isOpsAdmin(Long shopId, String username) {
        return resolve(shopId, username).isPresent();
    }

    public List<AdminIdentity> getAdmins() {
        return admins;
    }

    public void setAdmins(List<AdminIdentity> admins) {
        this.admins = admins == null ? new ArrayList<>() : new ArrayList<>(admins);
    }

    private List<String> normalizePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (permission == null) {
                continue;
            }
            String trimmed = permission.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return List.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN);
        }
        return List.copyOf(normalized);
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ResolvedOpsAccess(
            Long shopId,
            String username,
            List<String> permissions
    ) {
    }

    public static class OpsAccessBinding {

        private Long shopId;
        private String username;
        private List<String> permissions = new ArrayList<>();

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

        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
        }

        private boolean matches(Long expectedShopId, String expectedUsername) {
            if (shopId == null || username == null) {
                return false;
            }
            return shopId.equals(expectedShopId) && username.trim().equals(expectedUsername);
        }
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
            return shopId.equals(expectedShopId) && username.trim().equals(expectedUsername);
        }
    }
}
