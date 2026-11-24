package com.project.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RAG {

    private static final Logger logger = LoggerFactory.getLogger(RAG.class);

    private final VectorStore vectorStore;

    public RAG(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void pushData(String data, Map<String, Object> metaData) {
        try {
            Document document = new Document(data, metaData);
            vectorStore.add(List.of(document));
        } catch (Exception e) {
            logger.error("Error in processing document {}", e.getMessage());
        }
    }
}
