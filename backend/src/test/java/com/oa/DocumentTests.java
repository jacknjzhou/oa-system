package com.oa;

import com.oa.dto.DocumentDTO;
import com.oa.dto.DocumentRequest;
import com.oa.enums.DocType;
import com.oa.enums.SecrecyLevel;
import com.oa.enums.Urgency;
import com.oa.service.DocumentService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公文模块测试：创建（DTO 带出作者名）→ 列表/搜索 → 编辑 → 归档 → 重复归档 400。
 */
@SpringBootTest
class DocumentTests {

    @Autowired
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        loginAs("employee");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_listDetail_updateArchive_flow() {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("测试公文-" + System.nanoTime());
        req.setContent("正文内容");
        req.setDocType(DocType.NOTICE);
        req.setUrgency(Urgency.NORMAL);
        req.setSecrecyLevel(SecrecyLevel.INTERNAL);

        DocumentDTO created = documentService.create(req);
        assertNotNull(created.getId());
        assertNotNull(created.getDocNo());
        assertEquals("DRAFT", created.getStatus());
        assertEquals("普通员工", created.getAuthorName());

        // 列表可检索 + 作者名带出
        List<DocumentDTO> found = documentService.list(req.getTitle());
        assertEquals(1, found.size());
        assertEquals(created.getId(), found.get(0).getId());
        assertEquals("普通员工", found.get(0).getAuthorName());

        DocumentDTO detail = documentService.getDetail(created.getId());
        assertEquals("正文内容", detail.getContent());

        // 编辑
        DocumentRequest update = new DocumentRequest();
        update.setTitle("新标题");
        update.setContent("新正文");
        update.setDocType(DocType.REPORT);
        DocumentDTO updated = documentService.update(created.getId(), update);
        assertEquals("新标题", updated.getTitle());
        assertEquals("REPORT", updated.getDocType());

        // 归档
        DocumentDTO archived = documentService.archive(created.getId());
        assertEquals("ARCHIVED", archived.getStatus());
        assertNotNull(archived.getArchivedAt());

        // 重复归档 → 400
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.archive(created.getId()));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void getDetail_unknownId_returns404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.getDetail(999999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }
}
