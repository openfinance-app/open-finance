package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.AIDto;
import org.springframework.test.util.ReflectionTestUtils;

class AIContextBudgetTest {
    @Test
    void boundsActualOpenAiInputIncludingInstructionsAndOutputAllowance() {
        OpenAIProvider provider =
                new OpenAIProvider("fixture", "gpt-4o-mini", 0.2, 128, 1, "http://127.0.0.1:1");
        AIContextBudget budget = new AIContextBudget(provider, new ObjectMapper());
        ReflectionTestUtils.setField(budget, "providerName", "openai");
        ReflectionTestUtils.setField(budget, "maxContextTokens", 1200);
        ReflectionTestUtils.setField(budget, "openaiOutputTokens", 128);
        List<AIDto.Message> history = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            history.add(
                    AIDto.Message.builder()
                            .role(i % 2 == 0 ? "user" : "assistant")
                            .content("Turn " + i + " word".repeat(120))
                            .build());
        String context =
                budget.compose(
                        "What is my cash flow?",
                        "[VERIFIED_FINANCIAL_DATA]\n[FACT] {\"id\":\"cashflow.expenses\",\"amount\":\"170.00\",\"currency\":\"EUR\"}\n",
                        history,
                        10);
        assertThat(provider.countInputTokens("What is my cash flow?", context))
                .isLessThanOrEqualTo(1200 - 128);
        assertThat(context).contains("cashflow.expenses", "Turn 19").doesNotContain("Turn 0 ");
        assertThat(history).hasSize(20);
    }

    @Test
    void neverCutsAFinancialFactInHalf() {
        AIProvider provider =
                new AIProvider() {
                    public reactor.core.publisher.Mono<String> sendPrompt(String p, String c) {
                        return reactor.core.publisher.Mono.empty();
                    }

                    public reactor.core.publisher.Flux<String> streamResponse(String p, String c) {
                        return reactor.core.publisher.Flux.empty();
                    }

                    public reactor.core.publisher.Mono<Boolean> isAvailable() {
                        return reactor.core.publisher.Mono.just(false);
                    }

                    public String getProviderName() {
                        return "fixture";
                    }
                };
        AIContextBudget budget = new AIContextBudget(provider, new ObjectMapper());
        ReflectionTestUtils.setField(budget, "maxContextTokens", 600);
        ReflectionTestUtils.setField(budget, "ollamaOutputTokens", 100);
        String context =
                budget.compose(
                        "Question",
                        "[VERIFIED_FINANCIAL_DATA]\n[FACT] {\"entity\":\""
                                + "漢".repeat(500)
                                + "\"}\n",
                        List.of(),
                        1);
        assertThat(context).doesNotContain("[FACT]").contains("[VERIFIED_FINANCIAL_DATA]");
        assertThatThrownBy(() -> budget.compose("漢".repeat(500), "", List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
