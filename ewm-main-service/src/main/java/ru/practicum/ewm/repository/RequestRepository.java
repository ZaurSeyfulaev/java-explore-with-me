package ru.practicum.ewm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.model.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findAllByRequesterId(Long requesterId);

    List<Request> findAllByEventId(Long eventId);

    Optional<Request> findByIdAndRequesterId(Long id, Long requesterId);

    boolean existsByEventIdAndRequesterId(Long eventId, Long requesterId);

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    List<Request> findAllByEventIdAndStatus(Long eventId, RequestStatus status);

    @Query("select r.event.id, count(r.id) from Request r " +
            "where r.status = :status and r.event.id in :ids " +
            "group by r.event.id")
    List<Object[]> countByEventIdsAndStatus(@Param("ids") List<Long> ids,
                                            @Param("status") RequestStatus status);
}
