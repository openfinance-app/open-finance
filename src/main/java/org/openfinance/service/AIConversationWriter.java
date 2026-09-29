package org.openfinance.service;

import org.openfinance.entity.AIConversation;

/** Persists a completed exchange in a transaction separate from model inference. */
public interface AIConversationWriter {
    AIConversation save(AIConversation conversation);
}
