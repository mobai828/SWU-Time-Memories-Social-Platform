package com.example.logininterface.repository;

import com.example.logininterface.domain.UserFollowRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFollowRelationRepository extends JpaRepository<UserFollowRelation, Long> {

    long countByFolloweeId(Long followeeId);

    long countByFollowerId(Long followerId);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    Optional<UserFollowRelation> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
}
