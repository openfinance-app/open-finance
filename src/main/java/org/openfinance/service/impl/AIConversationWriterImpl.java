package org.openfinance.service.impl;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.AIConversation;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AIConversationRepository;
import org.openfinance.service.AIConversationWriter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AIConversationWriterImpl implements AIConversationWriter {
    private final AIConversationRepository repository;
    private final EntityManager entityManager;
    private final org.openfinance.config.EncryptionProperties encryptionProperties;

    @Override
    @Transactional
    public AIConversation save(AIConversation conversation) {
        if (encryptionProperties.isEnabled()
                && org.openfinance.security.EncryptionContext.getKey() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Encryption session required");
        }
        if (conversation.getId() != null) {
            AIConversation current =
                    repository
                            .findByIdAndUser_Id(conversation.getId(), conversation.getUserId())
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Conversation not found"));
            entityManager.refresh(current);
            if (current.getVersion() != conversation.getVersion()) {
                throw new OptimisticLockingFailureException(
                        "Conversation changed during inference");
            }
            current.setMessages(conversation.getMessages());
            current.setTitle(conversation.getTitle());
            return repository.saveAndFlush(current);
        }
        return repository.saveAndFlush(conversation);
    }
}
