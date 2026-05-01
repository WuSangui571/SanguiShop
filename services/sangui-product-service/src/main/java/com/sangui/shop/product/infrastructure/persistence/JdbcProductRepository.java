package com.sangui.shop.product.infrastructure.persistence;

import com.sangui.shop.common.core.api.PageRequest;
import com.sangui.shop.common.core.api.PageResponse;
import com.sangui.shop.product.domain.ProductDraft;
import com.sangui.shop.product.domain.ProductListItem;
import com.sangui.shop.product.domain.ProductRecord;
import com.sangui.shop.product.domain.ProductRepository;
import com.sangui.shop.product.domain.ProductSkuDraft;
import com.sangui.shop.product.domain.ProductSkuRecord;
import com.sangui.shop.product.domain.ProductSnapshot;
import com.sangui.shop.product.domain.ProductStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final RowMapper<ProductRecord> PRODUCT_ROW_MAPPER = (rs, rowNum) -> new ProductRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getString("product_name"),
            rs.getString("product_description"),
            ProductStatus.fromValue(rs.getString("status")),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private static final RowMapper<ProductSkuRecord> SKU_ROW_MAPPER = (rs, rowNum) -> new ProductSkuRecord(
            rs.getLong("id"),
            rs.getLong("product_id"),
            rs.getString("sku_code"),
            rs.getString("sku_name"),
            rs.getLong("sale_price_cent")
    );

    private static final RowMapper<ProductListItem> PRODUCT_LIST_ROW_MAPPER = (rs, rowNum) -> new ProductListItem(
            rs.getLong("id"),
            rs.getString("product_name"),
            rs.getString("product_description"),
            rs.getLong("min_price_cent"),
            rs.getLong("max_price_cent"),
            ProductStatus.fromValue(rs.getString("status"))
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResponse<ProductListItem> listActiveProducts(Long shopId, PageRequest pageRequest) {
        List<ProductListItem> items = jdbcTemplate.query(
                """
                        SELECT p.id,
                               p.product_name,
                               p.product_description,
                               p.status,
                               MIN(s.sale_price_cent) AS min_price_cent,
                               MAX(s.sale_price_cent) AS max_price_cent
                        FROM pms_product p
                        JOIN pms_sku s
                          ON s.product_id = p.id
                         AND s.shop_id = p.shop_id
                         AND s.deleted = 0
                        WHERE p.deleted = 0
                          AND p.shop_id = ?
                          AND p.status = 'active'
                        GROUP BY p.id, p.product_name, p.product_description, p.status
                        ORDER BY p.id DESC
                        LIMIT ? OFFSET ?
                        """,
                PRODUCT_LIST_ROW_MAPPER,
                shopId,
                pageRequest.size(),
                (pageRequest.page() - 1L) * pageRequest.size()
        );
        Long total = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pms_product
                        WHERE shop_id = ? AND deleted = 0 AND status = 'active'
                        """,
                Long.class,
                shopId
        );
        return new PageResponse<>(items, total == null ? 0L : total, pageRequest.page(), pageRequest.size());
    }

    @Override
    public Optional<ProductSnapshot> findPublicProduct(Long shopId, Long productId) {
        return findProduct(
                """
                        SELECT id, shop_id, product_name, product_description, status, created_by, updated_by
                        FROM pms_product
                        WHERE shop_id = ? AND id = ? AND deleted = 0 AND status = 'active'
                        LIMIT 1
                        """,
                shopId,
                productId
        );
    }

    @Override
    public Optional<ProductSnapshot> findAdminProduct(Long shopId, Long productId) {
        return findProduct(
                """
                        SELECT id, shop_id, product_name, product_description, status, created_by, updated_by
                        FROM pms_product
                        WHERE shop_id = ? AND id = ? AND deleted = 0
                        LIMIT 1
                        """,
                shopId,
                productId
        );
    }

    @Override
    public List<ProductSkuRecord> findActiveSkus(Long shopId, List<Long> skuIds) {
        String placeholders = skuIds.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[skuIds.size() + 1];
        args[0] = shopId;
        for (int index = 0; index < skuIds.size(); index++) {
            args[index + 1] = skuIds.get(index);
        }
        return jdbcTemplate.query(
                """
                        SELECT s.id, s.product_id, s.sku_code, s.sku_name, s.sale_price_cent
                        FROM pms_sku s
                        JOIN pms_product p
                          ON p.id = s.product_id
                         AND p.shop_id = s.shop_id
                         AND p.deleted = 0
                         AND p.status = 'active'
                        WHERE s.shop_id = ?
                          AND s.deleted = 0
                          AND s.id IN (%s)
                        ORDER BY s.id ASC
                        """.formatted(placeholders),
                SKU_ROW_MAPPER,
                args
        );
    }

    @Override
    public Long createProduct(Long shopId, String operatorUserId, ProductDraft draft, ProductStatus status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO pms_product (
                                shop_id, product_name, product_description, status, created_by, updated_by
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, shopId);
            statement.setString(2, draft.productName());
            statement.setString(3, draft.productDescription());
            statement.setString(4, status.value());
            statement.setString(5, operatorUserId);
            statement.setString(6, operatorUserId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Product insert did not return a generated id");
        }
        Long productId = key.longValue();
        insertSkus(shopId, productId, operatorUserId, draft.skus());
        return productId;
    }

    @Override
    public void updateProduct(Long shopId, Long productId, String operatorUserId, ProductDraft draft) {
        jdbcTemplate.update(
                """
                        UPDATE pms_product
                        SET product_name = ?, product_description = ?, updated_by = ?
                        WHERE shop_id = ? AND id = ? AND deleted = 0
                        """,
                draft.productName(),
                draft.productDescription(),
                operatorUserId,
                shopId,
                productId
        );
        jdbcTemplate.update(
                "DELETE FROM pms_sku WHERE shop_id = ? AND product_id = ?",
                shopId,
                productId
        );
        insertSkus(shopId, productId, operatorUserId, draft.skus());
    }

    @Override
    public void updateProductStatus(Long shopId, Long productId, String operatorUserId, ProductStatus status) {
        jdbcTemplate.update(
                """
                        UPDATE pms_product
                        SET status = ?, updated_by = ?
                        WHERE shop_id = ? AND id = ? AND deleted = 0
                        """,
                status.value(),
                operatorUserId,
                shopId,
                productId
        );
    }

    private Optional<ProductSnapshot> findProduct(String sql, Object... args) {
        return jdbcTemplate.query(sql, PRODUCT_ROW_MAPPER, args).stream()
                .findFirst()
                .map(product -> new ProductSnapshot(
                        product,
                        jdbcTemplate.query(
                                """
                                        SELECT id, product_id, sku_code, sku_name, sale_price_cent
                                        FROM pms_sku
                                        WHERE shop_id = ? AND product_id = ? AND deleted = 0
                                        ORDER BY id ASC
                                        """,
                                SKU_ROW_MAPPER,
                                product.shopId(),
                                product.id()
                        )
                ));
    }

    private void insertSkus(Long shopId, Long productId, String operatorUserId, List<ProductSkuDraft> skus) {
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO pms_sku (
                            shop_id, product_id, sku_code, sku_name, sale_price_cent, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                skus,
                skus.size(),
                (PreparedStatement statement, ProductSkuDraft sku) -> {
                    statement.setLong(1, shopId);
                    statement.setLong(2, productId);
                    statement.setString(3, sku.skuCode());
                    statement.setString(4, sku.skuName());
                    statement.setLong(5, sku.priceCent());
                    statement.setString(6, operatorUserId);
                    statement.setString(7, operatorUserId);
                }
        );
    }
}
