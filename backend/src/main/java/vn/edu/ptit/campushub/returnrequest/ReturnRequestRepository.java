package vn.edu.ptit.campushub.returnrequest;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    List<ReturnRequest> findByPostIdOrderByCreatedAtDesc(Long postId);
    List<ReturnRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    boolean existsByPostIdAndRequesterIdAndStatusIn(Long postId, Long requesterId, Collection<ReturnRequestStatus> statuses);
    long countByPostId(Long postId);
    long countByPostIdAndStatus(Long postId, ReturnRequestStatus status);
}
