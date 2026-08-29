package com.oa.service;

import com.oa.dto.DocumentRequest;
import com.oa.entity.Document;
import com.oa.entity.User;
import com.oa.enums.DocumentStatus;
import com.oa.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final AuthService authService;

    @Transactional
    public Document create(DocumentRequest req) {
        User author = authService.getCurrentUser();
        Document doc = new Document();
        doc.setDocNo(generateDocNo());
        doc.setTitle(req.getTitle());
        doc.setContent(req.getContent());
        doc.setDocType(req.getDocType());
        doc.setUrgency(req.getUrgency());
        doc.setSecrecyLevel(req.getSecrecyLevel());
        doc.setAuthor(author);
        doc.setStatus(DocumentStatus.DRAFT);
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<Document> list(String title) {
        if (title != null && !title.isBlank()) {
            return documentRepository.findByTitleContaining(title);
        }
        return documentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Document getDetail(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "公文不存在"));
    }

    @Transactional
    public Document update(Long id, DocumentRequest req) {
        Document doc = getDetail(id);
        doc.setTitle(req.getTitle());
        doc.setContent(req.getContent());
        doc.setDocType(req.getDocType());
        doc.setUrgency(req.getUrgency());
        doc.setSecrecyLevel(req.getSecrecyLevel());
        return documentRepository.save(doc);
    }

    @Transactional
    public Document archive(Long id) {
        Document doc = getDetail(id);
        if (doc.getStatus() == DocumentStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公文已归档");
        }
        doc.setStatus(DocumentStatus.ARCHIVED);
        doc.setArchivedAt(LocalDateTime.now());
        return documentRepository.save(doc);
    }

    private String generateDocNo() {
        return "DOC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
