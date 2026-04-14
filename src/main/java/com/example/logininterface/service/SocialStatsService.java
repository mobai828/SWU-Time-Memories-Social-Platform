package com.example.logininterface.service;

import com.example.logininterface.domain.UserAccount;
import com.example.logininterface.domain.UserFollowRelation;
import com.example.logininterface.domain.UserLikeRelation;
import com.example.logininterface.repository.UserAccountRepository;
import com.example.logininterface.repository.UserFollowRelationRepository;
import com.example.logininterface.repository.UserLikeRelationRepository;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class SocialStatsService {

    private final UserAccountRepository userAccountRepository;
    private final UserFollowRelationRepository userFollowRelationRepository;
    private final UserLikeRelationRepository userLikeRelationRepository;

    public SocialStatsService(
            UserAccountRepository userAccountRepository,
            UserFollowRelationRepository userFollowRelationRepository,
            UserLikeRelationRepository userLikeRelationRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userFollowRelationRepository = userFollowRelationRepository;
        this.userLikeRelationRepository = userLikeRelationRepository;
    }

    @Transactional
    public UserAccount syncCounters(UserAccount userAccount) {
        long followers = userFollowRelationRepository.countByFolloweeId(userAccount.getId());
        long following = userFollowRelationRepository.countByFollowerId(userAccount.getId());
        long likesReceived = userLikeRelationRepository.countByTargetUserId(userAccount.getId());

        userAccount.setFollowerCount((int) followers);
        userAccount.setFollowingCount((int) following);
        userAccount.setLikesReceivedCount((int) likesReceived);
        return userAccountRepository.save(userAccount);
    }

    @Transactional(readOnly = true)
    public List<SocialUserCard> getSuggestedUserCards(UserAccount actor) {
        LocalDateTime now = LocalDateTime.now();
        return userAccountRepository.findByIdNot(actor.getId()).stream()
                .sorted(Comparator
                        .comparingInt((UserAccount user) -> recommendationScore(user, now)).reversed()
                        .thenComparing(UserAccount::getCreatedAt, Comparator.reverseOrder()))
                .limit(8)
                .map(target -> new SocialUserCard(
                        target.getId(),
                        target.getUsername(),
                        target.getAvatarUrl(),
                        target.getFollowerCount(),
                        target.getLikesReceivedCount(),
                        userFollowRelationRepository.existsByFollowerIdAndFolloweeId(actor.getId(), target.getId()),
                        userLikeRelationRepository.existsByActorIdAndTargetUserId(actor.getId(), target.getId())
                ))
                .toList();
    }

    private int recommendationScore(UserAccount target, LocalDateTime now) {
        int followerScore = target.getFollowerCount() * 3;
        int likeScore = target.getLikesReceivedCount() * 2;
        long accountAgeDays = Math.max(0, Duration.between(target.getCreatedAt(), now).toDays());
        int freshnessBonus = (int) Math.max(0, 30 - Math.min(30, accountAgeDays));
        return followerScore + likeScore + freshnessBonus;
    }

    @Transactional
    public void follow(UserAccount actor, Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new ValidationException("目标用户不存在");
        }
        if (actor.getId().equals(targetUserId)) {
            throw new ValidationException("不能关注自己");
        }
        if (userFollowRelationRepository.findByFollowerIdAndFolloweeId(actor.getId(), targetUserId).isPresent()) {
            return;
        }
        UserFollowRelation relation = new UserFollowRelation();
        relation.setFollowerId(actor.getId());
        relation.setFolloweeId(targetUserId);
        userFollowRelationRepository.save(relation);
        syncCounters(actor);
        userAccountRepository.findById(targetUserId).ifPresent(this::syncCounters);
    }

    @Transactional
    public void unfollow(UserAccount actor, Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0 || actor.getId().equals(targetUserId)) {
            return;
        }
        userFollowRelationRepository.findByFollowerIdAndFolloweeId(actor.getId(), targetUserId)
                .ifPresent(userFollowRelationRepository::delete);
        syncCounters(actor);
        userAccountRepository.findById(targetUserId).ifPresent(this::syncCounters);
    }

    @Transactional
    public void likeUser(UserAccount actor, Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new ValidationException("目标用户不存在");
        }
        if (actor.getId().equals(targetUserId)) {
            throw new ValidationException("不能给自己点赞");
        }
        if (userLikeRelationRepository.findByActorIdAndTargetUserId(actor.getId(), targetUserId).isPresent()) {
            return;
        }
        UserLikeRelation relation = new UserLikeRelation();
        relation.setActorId(actor.getId());
        relation.setTargetUserId(targetUserId);
        userLikeRelationRepository.save(relation);
        userAccountRepository.findById(targetUserId).ifPresent(this::syncCounters);
    }

    @Transactional
    public void unlikeUser(UserAccount actor, Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0 || actor.getId().equals(targetUserId)) {
            return;
        }
        userLikeRelationRepository.findByActorIdAndTargetUserId(actor.getId(), targetUserId)
                .ifPresent(userLikeRelationRepository::delete);
        userAccountRepository.findById(targetUserId).ifPresent(this::syncCounters);
    }

    public record SocialUserCard(
            Long userId,
            String username,
            String avatarUrl,
            int followerCount,
            int likesReceivedCount,
            boolean followed,
            boolean liked
    ) {
    }
}
