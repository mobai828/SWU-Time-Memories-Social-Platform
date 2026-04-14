package com.example.logininterface.repository;

import com.example.logininterface.domain.UserLikeRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLikeRelationRepository extends JpaRepository<UserLikeRelation, Long> {

    long countByTargetUserId(Long targetUserId);

    boolean existsByActorIdAndTargetUserId(Long actorId, Long targetUserId);

    Optional<UserLikeRelation> findByActorIdAndTargetUserId(Long actorId, Long targetUserId);
}
