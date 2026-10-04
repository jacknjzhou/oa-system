package com.oa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.enums.ProcessInstanceStatus;

/**
 * 回归测试：flyway 启用时上下文必须能启动。
 * <p>
 * 历史事故：spring.jpa.defer-datasource-initialization=true 时，Boot 把
 * EntityManagerFactory 登记为"数据库初始化器"，与 flyway 互为 dependsOn 形成环，
 * Hibernate 6.4 + Boot 3.2 组合下启动即抛 Circular depends-on 异常。
 * 本测试钉住该配置组合（application.yml / application-mysql.yml 中 defer 必须为 false）。
 */
@SpringBootTest(properties = {"spring.flyway.enabled=true"})
class FlywayBootstrapTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void contextBootsAndMigrationsApplied() {
        // 当前迁移版本应为 V5（V1 业务表 / V2 refresh_token / V3 种子 / V4 财务种子 / V5 审批四态）
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
    }

    @Test
    void v5_widenedChecksAcceptDraftStatusAndDenyAction() {
        // V5 回归：放宽后的 CHECK 必须能容纳 DRAFT(4)/DENY(5)；
        // 若 V5 未应用（CHECK 仍是 0..3 / 0..4），下面的插入会直接抛约束异常。
        String probeNo = "PROBE_" + System.nanoTime();
        jdbc.update("""
                insert into process_instance (instance_no, title, status, priority,
                                               def_id, initiator_id, created_at, updated_at)
                select ?, 'V5探针', ?, 0,
                       id, (select min(id) from sys_user),
                       current_timestamp, current_timestamp
                from process_definition where def_key = 'reimbursement'
                """, probeNo, ProcessInstanceStatus.DRAFT.ordinal());
        jdbc.update("""
                insert into approval_record (instance_id, node_key, node_name, action,
                                             operator_name, from_node, to_node, created_at)
                select id, 'start', '发起', ?, 'V5探针', 'start', null, current_timestamp
                from process_instance where instance_no = ?
                """, com.oa.enums.ApprovalAction.DENY.ordinal(), probeNo);

        assertThat(jdbc.queryForObject(
                "select count(*) from process_instance where instance_no = ?", Integer.class, probeNo))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from approval_record where operator_name = 'V5探针'", Integer.class))
                .isEqualTo(1);

        jdbc.update("delete from approval_record where operator_name = 'V5探针'");
        jdbc.update("delete from process_instance where instance_no = ?", probeNo);
    }

    @Test
    void businessTablesAndSeedDataPresent() {
        assertThat(jdbc.queryForObject("select count(*) from organization", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sys_user", Integer.class)).isGreaterThanOrEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from role where role_code = 'FINANCE'", Integer.class)).isEqualTo(1);
    }
}
