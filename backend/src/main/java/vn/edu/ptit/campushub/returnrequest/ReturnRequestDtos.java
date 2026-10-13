package vn.edu.ptit.campushub.returnrequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import vn.edu.ptit.campushub.post.ItemType;
import vn.edu.ptit.campushub.post.Post;
import vn.edu.ptit.campushub.post.PostStatus;
import vn.edu.ptit.campushub.user.User;

public final class ReturnRequestDtos {
    private ReturnRequestDtos() {
    }

    public record CreateReturnRequest(
            @NotBlank(message = "Lời nhắn xác minh không được để trống")
            @Size(min = 10, max = 2000, message = "Lời nhắn xác minh cần từ 10 đến 2000 ký tự")
            String verificationMessage) {
    }

    public record StatusUpdateRequest(
            @NotNull(message = "Trạng thái không được để trống")
            ReturnRequestStatus status) {
    }

    public record RequesterView(Long id, String username, String displayName) {
        public static RequesterView from(User user) {
            return new RequesterView(user.getId(), user.getUsername(), user.getDisplayName());
        }
    }

    public record ReturnRequestPostSummary(
            Long id,
            String title,
            PostStatus status,
            ItemType itemType,
            String location) {
        public static ReturnRequestPostSummary from(Post post) {
            return new ReturnRequestPostSummary(post.getId(), post.getTitle(), post.getStatus(),
                    post.getItemType(), post.getLocation());
        }
    }

    public record ReturnRequestView(
            Long id,
            Long postId,
            RequesterView requester,
            String verificationMessage,
            ReturnRequestStatus status,
            Instant createdAt,
            Instant updatedAt,
            ReturnRequestPostSummary post) {
        public static ReturnRequestView from(ReturnRequest request) {
            return new ReturnRequestView(
                    request.getId(),
                    request.getPost().getId(),
                    RequesterView.from(request.getRequester()),
                    request.getVerificationMessage(),
                    request.getStatus(),
                    request.getCreatedAt(),
                    request.getUpdatedAt(),
                    ReturnRequestPostSummary.from(request.getPost()));
        }
    }
}
