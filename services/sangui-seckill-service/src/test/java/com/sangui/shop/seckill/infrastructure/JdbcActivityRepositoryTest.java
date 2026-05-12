package com.sangui.shop.seckill.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sangui.shop.seckill.domain.ActivityRepository;
import com.sangui.shop.seckill.domain.SeckillActivity;
import com.sangui.shop.seckill.domain.SeckillActivitySku;
import com.sangui.shop.seckill.domain.SeckillActivityStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class JdbcActivityRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcActivityRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcActivityRepository(jdbcTemplate);
    }

    @Test
    void findByIdReturnsActivityWhenFound() {
        SeckillActivity activity = activityRow(100L);
        lenient().when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("sk_activity") && !sql.contains("sk_activity_sku")), any(RowMapper.class), eq(1L), eq(100L)))
                .thenReturn(List.of(activity));
        lenient().when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("sk_activity_sku")), any(RowMapper.class), eq(1L), eq(100L)))
                .thenReturn(List.of(skuRow(200L, 100L)));

        Optional<SeckillActivity> result = repository.findById(1L, 100L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(100L);
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq(999L)))
                .thenReturn(List.of());

        Optional<SeckillActivity> result = repository.findById(1L, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findPageReturnsActivities() {
        lenient().when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("sk_activity")), any(RowMapper.class), eq(1L), anyInt(), anyInt()))
                .thenReturn(List.of(activityRow(100L)));
        lenient().when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("sk_activity_sku")), any(RowMapper.class), eq(1L), eq(100L)))
                .thenReturn(List.of());

        List<SeckillActivity> activities = repository.findPage(1L, null, 0, 20);

        assertThat(activities).hasSize(1);
    }

    @Test
    void countReturnsTotal() {
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(1L)))
                .thenReturn(5L);

        assertThat(repository.count(1L, null)).isEqualTo(5);
    }

    @Test
    void countWithStatusReturnsTotal() {
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(1L), anyString()))
                .thenReturn(3L);

        assertThat(repository.count(1L, "draft")).isEqualTo(3);
    }

    @Test
    void updateActivityStatusReturnsAffectedRows() {
        lenient().when(jdbcTemplate.update(anyString(), anyString(), any(), eq(1L), eq(100L), anyString()))
                .thenReturn(1);

        int result = repository.updateActivityStatus(1L, 100L, SeckillActivityStatus.DRAFT, SeckillActivityStatus.SCHEDULED);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void upsertSkuInsertsWhenNoExistingRow() {
        lenient().when(jdbcTemplate.update(argThat(sql -> sql != null && sql.contains("UPDATE")), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        lenient().when(jdbcTemplate.update(argThat(sql -> sql != null && sql.contains("INSERT")), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        SeckillActivitySku sku = new SeckillActivitySku(null, 100L, 301L, "Running Shoe", 401L, "RS-42", "42", 59900L, 49900L, 20L, 10L, 0L, "req-sku");

        assertThat(repository.upsertSku(1L, 100L, sku)).isEqualTo(1);
    }

    @Test
    void findSkuByRequestIdReturnsSku() {
        lenient().when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("sk_activity_sku")), any(RowMapper.class), eq(1L), eq(100L), eq("req-sku")))
                .thenReturn(List.of(skuRow(200L, 100L)));

        Optional<SeckillActivitySku> sku = repository.findSkuByRequestId(1L, 100L, "req-sku");
        assertThat(sku).isPresent();
    }

    @Test
    void findStatusRequestByRequestIdReturnsRecord() {
        lenient().when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("sk_activity_status_request")), any(RowMapper.class), eq(1L), eq(100L), eq("req-status")))
                .thenReturn(List.of(new ActivityRepository.StatusRequestRecord(1L, 100L, "req-status", SeckillActivityStatus.SCHEDULED)));

        Optional<ActivityRepository.StatusRequestRecord> record = repository.findStatusRequestByRequestId(1L, 100L, "req-status");
        assertThat(record).isPresent();
        assertThat(record.get().targetStatus()).isEqualTo(SeckillActivityStatus.SCHEDULED);
    }

    @Test
    void saveStatusRequestInsertsRow() {
        repository.saveStatusRequest(1L, 100L, "req-status", SeckillActivityStatus.SCHEDULED, "trace-status");

        verify(jdbcTemplate).update(anyString(), eq(1L), eq(100L), eq("req-status"), eq("scheduled"), eq("trace-status"), any(), any());
    }

    private SeckillActivity activityRow(Long id) {
        LocalDateTime now = LocalDateTime.now();
        return new SeckillActivity(id, 1L, "Test Activity", null, SeckillActivityStatus.DRAFT,
                now, now, "req-activity", "trace-1", null, now, now);
    }

    private SeckillActivitySku skuRow(Long id, Long activityId) {
        return new SeckillActivitySku(id, activityId, 301L, "Running Shoe", 401L, "RS-42", "42",
                59900L, 49900L, 20L, 10L, 0L, "req-sku");
    }
}
