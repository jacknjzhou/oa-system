package com.oa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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
        // 当前迁移版本应为 V4（V1 业务表 / V2 refresh_token / V3 种子 / V4 财务种子）
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
    }

    @Test
    void businessTablesAndSeedDataPresent() {
        assertThat(jdbc.queryForObject("select count(*) from organization", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sys_user", Integer.class)).isGreaterThanOrEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from role where role_code = 'FINANCE'", Integer.class)).isEqualTo(1);
    }
}
