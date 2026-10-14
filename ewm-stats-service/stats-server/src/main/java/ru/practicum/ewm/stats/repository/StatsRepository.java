package ru.practicum.ewm.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ViewStatsDto;
import ru.practicum.ewm.stats.model.EndpointHit;

import java.time.LocalDateTime;
import java.util.List;

public interface StatsRepository extends JpaRepository<EndpointHit, Long> {

    @Query("select new ru.practicum.ViewStatsDto(h.app, h.uri, count(h.ip)) " +
            "from EndpointHit h " +
            "where h.created between :start and :end " +
            "group by h.app, h.uri " +
            "order by count (h.ip) desc")
    List<ViewStatsDto> getStats(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("select new ru.practicum.ViewStatsDto(h.app, h.uri, count(h.ip)) " +
            "from EndpointHit h " +
            "where h.created between :start and :end " +
            "and h.uri in :uris " +
            "group by h.app, h.uri " +
            "order by count(h.ip) desc")
    List<ViewStatsDto> getStatsByUris(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("uris") List<String> uris);

    @Query("select new ru.practicum.ViewStatsDto(h.app, h.uri, count(distinct h.ip)) " +
            "from EndpointHit h " +
            "where h.created between :start and :end " +
            "group by h.app, h.uri " +
            "order by count(distinct h.ip) desc ")
    List<ViewStatsDto> getUniqueStats(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    @Query("select new ru.practicum.ViewStatsDto(h.app, h.uri, count(distinct h.ip)) " +
            "from EndpointHit h " +
            "where h.created between :start and :end " +
            "and h.uri in :uris " +
            "group by h.app, h.uri " +
            "order by count (distinct h.ip) desc")
    List<ViewStatsDto> getUniqueStatsByUris(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("uris") List<String> uris);

}
