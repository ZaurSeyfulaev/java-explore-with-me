package ru.practicum;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StatsClient {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RestTemplate restTemplate;
    private final String serverUrl;

    public StatsClient(RestTemplate restTemplate, String serverUrl) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl;
    }

    public void setHit(EndpointHitDto hit) {
        restTemplate.postForEntity(serverUrl + "/hit", hit, Void.class);
    }

    public List<ViewStatsDto> getViewStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        String startText = start.format(FORMATTER);
        String endText = end.format(FORMATTER);
        StringBuilder url = new StringBuilder(serverUrl);
        url.append("/stats?start=").append(encode(startText));
        url.append("&end=").append(encode(endText));
        url.append("&unique=").append(unique);
        if (uris != null) {
            for (String uri : uris) {
                url.append("&uris=").append(encode(uri));
            }
        }
        ResponseEntity<ViewStatsDto[]> response = restTemplate.getForEntity(url.toString(), ViewStatsDto[].class);
        ViewStatsDto[] body = response.getBody();
        if (body == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(body);
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
