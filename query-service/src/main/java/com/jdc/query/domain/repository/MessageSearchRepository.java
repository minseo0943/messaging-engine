package com.jdc.query.domain.repository;

import com.jdc.query.domain.document.MessageSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MessageSearchRepository extends ElasticsearchRepository<MessageSearchDocument, String> {
}
