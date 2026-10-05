package com.oa;

import com.oa.dto.CheckRecordDTO;
import com.oa.service.CheckService;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodName.class)
class AttendanceTests {

    @Autowired
    CheckService checkService;

    /** employee(userId=3)：9:00 上班卡 + 18:30 下班卡。 */
    @Test
    @Order(1)
    void clock_in_out_today_and_month() {
        CheckRecordDTO in = checkService.clock(3L, "in", "manual", "到岗");
        CheckRecordDTO out = checkService.clock(3L, "out", "scan", null);
        assertNotNull(in.getId());

        Map<String, Object> today = checkService.today(3L);
        assertNotNull(today.get("firstIn"), "应有上班卡");
        assertNotNull(today.get("lastOut"), "应有下班卡");
        long minutes = ((Number) today.get("workMinutes")).longValue();
        assertTrue(minutes >= 0 && minutes <= 24 * 60, "工作时长异常: " + minutes);
        assertEquals(2, ((List<?>) today.get("records")).size());

        int year = in.getCheckTime().getYear();
        int month = in.getCheckTime().getMonthValue();
        List<Map<String, Object>> monthDays = checkService.month(3L, year, month);
        assertEquals(1, monthDays.size(), "当月应只有 1 天记录");
        long cnt = ((Number) monthDays.get(0).get("count")).longValue();
        assertEquals(2, cnt, "当天 2 张卡");
    }

    /** 全部考勤（HD 简化版）：月度窗口 + 可选人员过滤，行带用户姓名。 */
    @Test
    @Order(2)
    void list_all_attendance_with_month_filter() {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        List<Map<String, Object>> rows = checkService.listAll(year, month, null);
        assertFalse(rows.isEmpty(), "本月已有打卡记录");
        assertTrue(rows.stream().anyMatch(r -> "普通员工".equals(r.get("userName")) && java.util.Objects.equals(3L, r.get("userId"))));
        assertTrue(rows.stream().allMatch(r -> r.get("checkTime") != null && r.get("type") != null));

        List<Map<String, Object>> onlyEmp = checkService.listAll(year, month, 3L);
        assertEquals(rows.size(), onlyEmp.size());
        assertTrue(onlyEmp.stream().allMatch(r -> java.util.Objects.equals(3L, r.get("userId"))));
    }
}
