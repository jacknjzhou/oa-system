package com.oa.repository;

import com.oa.entity.Document;
import com.oa.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByAuthorId(Long authorId);

    List<Document> findByStatus(DocumentStatus status);

    List<Document> findByTitleContaining(String title);
}
