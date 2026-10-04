package com.oa;

import com.oa.controller.UserController;
import com.oa.dto.ApiResponse;
import com.oa.dto.JobLevelRequest;
import com.oa.entity.User;
import com.oa.repository.JobLevelRepository;
import com.oa.repository.JobTitleRepository;
import com.oa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 职级/职称（SY-02）：预置、屏蔽、用户绑定。
 */
@SpringBootTest
class JobMasterDataTests {

    @Autowired
    private UserController userController;

    @Autowired
    private JobLevelRepository jobLevelRepository;

    @Autowired
    private JobTitleRepository jobTitleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void seeded_and_blocking() {
        // 按 code 断言（不数总数，避免受其它测试新建数据干扰）
        List<Map<String, Object>> levels = userController.listJobLevels(true).getData();
        for (String code : List.of("L1", "L2", "L3", "L4")) {
            assertTrue(levels.stream().anyMatch(m -> code.equals(m.get("code"))), "缺职级 " + code);
        }
        List<Map<String, Object>> titles = userController.listJobTitles(true).getData();
        for (String code : List.of("ENGINEER", "SENIOR_ENGINEER", "CHIEF_ENGINEER",
                "TECH_DIRECTOR", "PRODUCT_MANAGER", "HR", "FINANCE")) {
            assertTrue(titles.stream().anyMatch(m -> code.equals(m.get("code"))), "缺职称 " + code);
        }

        // 屏蔽 L4 → 可用列表消失，恢复后回来
        Long l4 = levels.stream()
                .filter(m -> "L4".equals(m.get("code")))
                .findFirst().orElseThrow()
                .get("id") instanceof Number n ? n.longValue() : null;
        JobLevelRequest req = new JobLevelRequest();
        req.setEnabled(false);
        userController.updateJobLevel(l4, req);
        assertEquals(false, userController.listJobLevels(false).getData().stream()
                .anyMatch(m -> "L4".equals(m.get("code"))), "屏蔽后不应出现在可用列表");
        req.setEnabled(true);
        userController.updateJobLevel(l4, req);
        assertTrue(userController.listJobLevels(false).getData().stream()
                .anyMatch(m -> "L4".equals(m.get("code"))));
    }

    @Test
    void create_and_duplicate_blocked() {
        JobLevelRequest req = new JobLevelRequest();
        req.setCode("l5-test");
        req.setName("五级测试");
        userController.createJobLevel(req);
        assertEquals("L5-TEST", userController.listJobLevels(true).getData().stream()
                .filter(m -> "L5-TEST".equals(m.get("code")))
                .findFirst().orElseThrow().get("code"));

        JobLevelRequest dup = new JobLevelRequest();
        dup.setCode("L5-TEST");
        dup.setName("重复");
        assertThrows(ResponseStatusException.class, () -> userController.createJobLevel(dup));
    }

    @Test
    void user_bind_job_level() {
        User finance = userRepository.findByUsername("finance").orElseThrow();
        Map<String, Object> l2 = userController.listJobLevels(true).getData().stream()
                .filter(m -> "L2".equals(m.get("code")))
                .findFirst().orElseThrow();
        finance.setJobLevelId(((Number) l2.get("id")).longValue());
        User saved = userRepository.save(finance);
        assertEquals(((Number) l2.get("id")).longValue(), saved.getJobLevelId());
        assertTrue(saved.getJobLevelId() != null);
    }
}
