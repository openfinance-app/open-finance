package org.openfinance.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RssFeedItem {
    private String title;
    private String link;
    private String description;
    private LocalDateTime pubDate;
    private String source;
}
