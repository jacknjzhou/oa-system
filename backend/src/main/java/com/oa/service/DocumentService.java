package com.oa.service;

import com.oa.dto.DocumentDTO;
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

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DocumentRepository documentRepository;
    private final AuthService authService;

    @Transactional
    public DocumentDTO create(DocumentRequest req) {
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
        return toDto(documentRepository.save(doc));
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> list(String title) {
        List<Document> docs = (title != null && !title.isBlank())
                ? documentRepository.findByTitleContaining(title)
                : documentRepository.findAll();
        return docs.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDTO getDetail(Long id) {
        return toDto(loadDocument(id));
    }

    @Transactional
    public DocumentDTO update(Long id, DocumentRequest req) {
        Document doc = loadDocument(id);
        doc.setTitle(req.getTitle());
        doc.setContent(req.getContent());
        doc.setDocType(req.getDocType());
        doc.setUrgency(req.getUrgency());
        doc.setSecrecyLevel(req.getSecrecyLevel());
        return toDto(documentRepository.save(doc));
    }

    @Transactional
    public DocumentDTO archive(Long id) {
        Document doc = loadDocument(id);
        if (doc.getStatus() == DocumentStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公文已归档");
        }
        doc.setStatus(DocumentStatus.ARCHIVED);
        doc.setArchivedAt(LocalDateTime.now());
        return toDto(documentRepository.save(doc));
    }

    private Document loadDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "公文不存在"));
    }

    private DocumentDTO toDto(Document doc) {
        String authorName = doc.getAuthor() != null
                ? (doc.getAuthor().getRealName() != null ? doc.getAuthor().getRealName() : doc.getAuthor().getUsername())
                : null;
        return new DocumentDTO(
                doc.getId(),
                doc.getDocNo(),
                doc.getTitle(),
                doc.getContent(),
                doc.getDocType() != null ? doc.getDocType().name() : null,
                doc.getUrgency() != null ? doc.getUrgency().name() : null,
                doc.getSecrecyLevel() != null ? doc.getSecrecyLevel().name() : null,
                authorName,
                doc.getStatus() != null ? doc.getStatus().name() : null,
                doc.getPublishedAt() != null ? doc.getPublishedAt().format(TS) : null,
                doc.getArchivedAt() != null ? doc.getArchivedAt().format(TS) : null,
                doc.getCreatedAt() != null ? doc.getCreatedAt().format(TS) : null,
                doc.getUpdatedAt() != null ? doc.getUpdatedAt().format(TS) : null);
    }

    private String generateDocNo() {
        return "DOC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
