package ru.practicum.ewm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.model.Subscription;
import ru.practicum.ewm.model.User;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    Optional<Subscription> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("select s.following from Subscription s where s.follower.id = :followerId")
    Page<User> findFollowing(@Param("followerId") Long followerId, Pageable pageable);

    @Query("select s.following.id from Subscription s where s.follower.id = :followerId")
    List<Long> findFollowingIds(@Param("followerId") Long followerId);
}
