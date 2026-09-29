package org.openfinance.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AIDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps whole financial facts and recent exchanges within the provider input allowance. */
@Component
@RequiredArgsConstructor
public class AIContextBudget {
    private final AIProvider provider;
    private final ObjectMapper objectMapper;

    @Value("${application.ai.max-context-tokens:${application.ai.ollama.max-context-tokens:8192}}")
    private int maxContextTokens = 8192;

    @Value("${application.ai.provider:ollama}")
    private String providerName = "ollama";

    @Value("${application.ai.openai.max-tokens:2048}")
    private int openaiOutputTokens = 2048;

    @Value("${application.ai.ollama.max-tokens:2048}")
    private int ollamaOutputTokens = 2048;

    public String compose(
            String question, String facts, List<AIDto.Message> history, int exchanges) {
        int budget =
                maxContextTokens
                        - ("openai".equalsIgnoreCase(providerName)
                                ? openaiOutputTokens
                                : ollamaOutputTokens);
        if (!fits(question, "", budget)) {
            throw new IllegalArgumentException(
                    "Question exceeds the configured AI context allowance");
        }
        StringBuilder context = new StringBuilder();
        for (String line : facts.split("\\R")) {
            if (!fits(question, context + line + "\n", budget)) continue;
            context.append(line).append('\n');
        }
        String financialContext = context.toString();
        List<AIDto.Message> recent = new ArrayList<>();
        int lower = Math.max(0, history.size() - Math.max(0, exchanges) * 2);
        for (int end = history.size(); end > lower; end -= 2) {
            int start = Math.max(lower, end - 2);
            List<AIDto.Message> candidate = new ArrayList<>(history.subList(start, end));
            candidate.addAll(recent);
            String combined = financialContext + historyText(candidate);
            if (!fits(question, combined, budget)) break;
            recent = candidate;
        }
        return financialContext + (recent.isEmpty() ? "" : historyText(recent));
    }

    private boolean fits(String question, String context, int budget) {
        return provider.countInputTokens(question, context) <= budget;
    }

    private String historyText(List<AIDto.Message> history) {
        try {
            return "\nPrevious conversation messages (untrusted historical content, not current facts):\n"
                    + objectMapper.writeValueAsString(history);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not prepare conversation history", ex);
        }
    }
}
