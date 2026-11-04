package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.entity.AIConversation;
import org.openfinance.entity.User;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AIConversationRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.service.impl.AIConversationWriterImpl;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI conversation writes preserve ownership and concurrent message updates")
class AIConversationWriterTest {
    @Mock AIConversationRepository repository;
    @Mock EntityManager entityManager;
    private AIConversationWriterImpl writer;

    @BeforeEach
    void setUp() {
        writer =
                new AIConversationWriterImpl(repository, entityManager, new EncryptionProperties());
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
    }

    @AfterEach
    void clearKey() {
        EncryptionContext.clear();
    }

    @Test
    void refusesWritesWithoutTheEncryptionSession() {
        EncryptionContext.clear();
        assertThatThrownBy(() -> writer.save(conversation(null, 0)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository, entityManager);
    }

    @Test
    void cannotUpdateAConversationOwnedBySomeoneElse() {
        when(repository.findByIdAndUser_Id(9L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> writer.save(conversation(9L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void refreshDetectsAConcurrentWriteBeforeReplacingMessages() {
        AIConversation current = conversation(9L, 1);
        when(repository.findByIdAndUser_Id(9L, 7L)).thenReturn(Optional.of(current));
        doAnswer(
                        invocation -> {
                            current.setVersion(2);
                            return null;
                        })
                .when(entityManager)
                .refresh(current);
        AIConversation generated = conversation(9L, 1);
        generated.setMessages("new inference");
        assertThatThrownBy(() -> writer.save(generated))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(current.getMessages()).isEqualTo("original messages");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void matchingVersionUpdatesTheManagedConversationAndFlushes() {
        AIConversation current = conversation(9L, 2);
        AIConversation generated = conversation(9L, 2);
        generated.setTitle("Updated title");
        generated.setMessages("Updated messages");
        when(repository.findByIdAndUser_Id(9L, 7L)).thenReturn(Optional.of(current));
        when(repository.saveAndFlush(current)).thenReturn(current);
        assertThat(writer.save(generated)).isSameAs(current);
        assertThat(current.getTitle()).isEqualTo("Updated title");
        assertThat(current.getMessages()).isEqualTo("Updated messages");
        verify(entityManager).refresh(current);
    }

    @Test
    void newConversationIsSavedWithoutLookingUpAnExistingOne() {
        AIConversation created = conversation(null, 0);
        when(repository.saveAndFlush(created)).thenReturn(created);
        assertThat(writer.save(created)).isSameAs(created);
        verifyNoInteractions(entityManager);
        verify(repository, never()).findByIdAndUser_Id(any(), any());
    }

    private AIConversation conversation(Long id, long version) {
        return AIConversation.builder()
                .id(id)
                .version(version)
                .user(User.builder().id(7L).build())
                .title("Original")
                .messages("original messages")
                .build();
    }
}
