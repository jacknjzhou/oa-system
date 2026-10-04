package com.oa;

import com.oa.controller.AuthController;
import com.oa.controller.CompanyController;
import com.oa.dto.ApiResponse;
import com.oa.dto.LoginRequest;
import com.oa.entity.LoginLog;
import com.oa.repository.LoginLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 企业信息 + 登录日志（SY-04）。 */
@SpringBootTest
class CompanyLoginLogTests {

    @Autowired
    private CompanyController companyController;

    @Autowired
    private AuthController authController;

    @Autowired
    private LoginLogRepository loginLogRepository;

    @Test
    void company_save_and_get() {
        CompanyController.CompanyRequest req = new CompanyController.CompanyRequest();
        req.setName("智慧云科技有限公司");
        req.setShortName("智慧云");
        req.setAddress("上海市浦东新区");
        req.setEmail("oa@zhidieyun.example.com");
        companyController.save(req);

        ApiResponse<Map<String, Object>> res = companyController.get();
        assertEquals("智慧云科技有限公司", res.getData().get("name"));
        assertEquals("智慧云", res.getData().get("shortName"));
        assertEquals("上海市浦东新区", res.getData().get("address"));

        // 仍是单行（id=1 覆盖更新）
        assertEquals("智慧云科技有限公司", companyController.get().getData().get("name"));
    }

    @Test
    void login_logs_success_and_failure() {
        long before = loginLogRepository.count();
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("127.0.0.1");
        http.addHeader("User-Agent", "oa-test");

        LoginRequest ok = new LoginRequest();
        ok.setUsername("admin");
        ok.setPassword("admin123");
        ApiResponse<?> res = authController.login(ok, http);
        assertNotNull(res.getData());
        assertTrue(loginLogRepository.count() > before, "成功登录应有日志");

        LoginRequest bad = new LoginRequest();
        bad.setUsername("admin");
        bad.setPassword("wrong-password");
        assertThrows(Exception.class, () -> authController.login(bad, http));

        Page<LoginLog> all = loginLogRepository.findAll(PageRequest.of(0, 200));
        assertTrue(all.getContent().stream().anyMatch(
                        l -> !l.getSuccess() && "用户名或密码错误".equals(l.getFailReason())),
                "失败登录应记录原因");
        assertTrue(all.getContent().stream().anyMatch(
                        l -> l.getSuccess() && "admin".equals(l.getUsername()) && "oa-test".equals(l.getUserAgent())));
    }
}
