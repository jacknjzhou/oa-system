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
        // 当前迁移版本：V22 假期单位/日志/部门 + V23(h2) 账本 decimal 化
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("23");
    }

    @Test
    void v6_ccRecordHasBaseEntityColumns() {
        // 回归：cc_record 必须含 BaseEntity 的 created_at/updated_at 列。
        // 历史事故：V6 初版漏了 updated_at，H2 因 ddl-auto=update 自动补列而测试全绿，
        // MySQL（ddl-auto=none）上线即 500（Unknown column），抄送把整个审批事务卷回。
        for (String column : new String[]{"created_at", "updated_at"}) {
            Long count = jdbc.queryForObject(
                    "select count(*) from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME = 'CC_RECORD' and COLUMN_NAME = ?",
                    Long.class, column.toUpperCase());
            assertThat(count).as("cc_record.%s", column).isEqualTo(1L);
        }
    }

    @Test
    void v10_businessTypeIsString() {
        // 回归：business_type 必须接受任意字符串类型代码（V10 前为 TINYINT + CHECK 0..3，
        // 自定义审批类型如 'CONTRACT' 会被约束拒绝）。
        String probeNo = "PROBE_BT_" + System.nanoTime();
        jdbc.update("""
                insert into process_instance (instance_no, title, status, priority,
                                               def_id, initiator_id, business_type,
                                               created_at, updated_at)
                select ?, 'V10探针', ?, 0,
                       id, (select min(id) from sys_user), 'CUSTOM_CODE',
                       current_timestamp, current_timestamp
                from process_definition where def_key = 'reimbursement'
                """, probeNo, ProcessInstanceStatus.DRAFT.ordinal());
        String actual = jdbc.queryForObject(
                "select business_type from process_instance where instance_no = ?", String.class, probeNo);
        assertThat(actual).isEqualTo("CUSTOM_CODE");
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
