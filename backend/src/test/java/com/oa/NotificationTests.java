package com.oa;

import com.oa.dto.NotificationDTO;
import com.oa.dto.ProcessStartRequest;
import com.oa.repository.NotificationRepository;
import com.oa.service.NotificationService;
import com.oa.service.ProcessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站内通知读侧测试：发起流程触发通知 → 未读计数 → 标记已读 → 归属校验。
 */
@SpringBootTest
class NotificationTests {

    @Autowired
    private ProcessService processService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    /** 测试方法间共享 H2 上下文，清空通知表保证未读计数断言不受历史数据干扰。 */
    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startProcess_notifiesCandidate_andReadStateTracks() {
        loginAs("employee");
        startReimbursement("通知测试-小额");

        // 部门经理候选组（MANAGER）收到新任务通知
        loginAs("manager");
        assertEquals(1, notificationService.countUnread());

        List<NotificationDTO> mine = notificationService.listMine(20);
        assertEquals(1, mine.size());
        NotificationDTO n = mine.get(0);
        assertEquals("TASK", n.getNotifyType());
        assertEquals("TASK", n.getRefType());
        assertNotNull(n.getRefId());
        assertFalse(n.getIsRead());

        // 标记已读后未读归零，readAt 落库
        notificationService.markRead(n.getId());
        assertEquals(0, notificationService.countUnread());
        NotificationDTO after = notificationService.listMine(20).get(0);
        assertTrue(after.getIsRead());
        assertNotNull(after.getReadAt());

        // 已读状态下重复标记不报错
        notificationService.markRead(n.getId());
        assertEquals(0, notificationService.countUnread());

        // 全部已读（幂等）
        notificationService.markAllRead();
        assertEquals(0, notificationService.countUnread());
    }

    @Test
    void markRead_rejectsOtherUsersNotification() {
        loginAs("employee");
        startReimbursement("通知测试-归属");

        loginAs("manager");
        NotificationDTO n = notificationService.listMine(20).get(0);

        // 他人通知：403
        loginAs("admin");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.markRead(n.getId()));
        assertEquals(403, ex.getStatusCode().value());
        // 原属主仍未读
        loginAs("manager");
        assertEquals(1, notificationService.countUnread());
    }

    @Test
    void markRead_unknownId_returns404() {
        loginAs("manager");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.markRead(999999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void listMine_unreadFirstAndLimited() {
        loginAs("employee");
        startReimbursement("通知-列表-1");
        startReimbursement("通知-列表-2");

        loginAs("manager");
        // 两条任务通知均未读
        List<NotificationDTO> all = notificationService.listMine(20);
        assertEquals(2, all.size());

        // 读掉第一条 → 列表重排：未读在前
        notificationService.markRead(all.get(0).getId());
        List<NotificationDTO> reordered = notificationService.listMine(20);
        assertEquals(2, reordered.size());
        assertFalse(reordered.get(0).getIsRead());
        assertTrue(reordered.get(1).getIsRead());

        // limit 生效（上限 50）
        assertEquals(1, notificationService.listMine(1).size());
    }

    private void startReimbursement(String title) {
        Long templateId = processService.listDefinitions(true).stream()
                .filter(d -> "reimbursement".equals(d.getDefKey()))
                .findFirst().orElseThrow().getId();
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId);
        req.setTitle(title);
        req.setBusinessData("{\"amount\": 800, \"reason\": \"测试\"}");
        processService.startInstance(req);
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }
}
