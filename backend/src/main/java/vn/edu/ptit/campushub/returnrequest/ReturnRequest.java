package vn.edu.ptit.campushub.returnrequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import vn.edu.ptit.campushub.post.Post;
import vn.edu.ptit.campushub.user.User;

@Entity
@Table(name = "return_requests")
public class ReturnRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String verificationMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnRequestStatus status = ReturnRequestStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ReturnRequest() {
    }

    public ReturnRequest(Post post, User requester, String verificationMessage) {
        this.post = post;
        this.requester = requester;
        this.verificationMessage = verificationMessage;
        this.status = ReturnRequestStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(ReturnRequestStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public User getRequester() { return requester; }
    public String getVerificationMessage() { return verificationMessage; }
    public ReturnRequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
