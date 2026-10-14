package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Transaction;
import org.openfinance.mapper.TransactionMapper;

class TagsDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TransactionMapper mapper = Mappers.getMapper(TransactionMapper.class);

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"\"", "\"  \"", "[null, \"  \"]"})
    @DisplayName("Explicit empty tags clear the existing transaction tags")
    void clearsTags(String jsonTags) throws Exception {
        TransactionRequest request =
                objectMapper.readValue("{\"tags\":" + jsonTags + "}", TransactionRequest.class);
        Transaction transaction = Transaction.builder().tags("holiday,work").build();

        mapper.updateEntityFromRequest(request, transaction);

        assertThat(transaction.getTags()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"tags\":null}"})
    @DisplayName("Unspecified tags preserve the existing transaction tags")
    void preservesUnspecifiedTags(String json) throws Exception {
        TransactionRequest request = objectMapper.readValue(json, TransactionRequest.class);
        Transaction transaction = Transaction.builder().tags("holiday,work").build();

        mapper.updateEntityFromRequest(request, transaction);

        assertThat(transaction.getTags()).isEqualTo("holiday,work");
    }
}
