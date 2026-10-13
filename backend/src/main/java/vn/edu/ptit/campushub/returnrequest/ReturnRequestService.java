package vn.edu.ptit.campushub.returnrequest;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.campushub.common.ApiException;
import vn.edu.ptit.campushub.post.ItemType;
import vn.edu.ptit.campushub.post.Post;
import vn.edu.ptit.campushub.post.PostRepository;
import vn.edu.ptit.campushub.post.PostStatus;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestDtos.CreateReturnRequest;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestDtos.ReturnRequestView;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestDtos.StatusUpdateRequest;
import vn.edu.ptit.campushub.user.Role;
import vn.edu.ptit.campushub.user.User;
import vn.edu.ptit.campushub.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class ReturnRequestService {
    private final ReturnRequestRepository returnRequests;
    private final PostRepository posts;
    private final UserRepository users;

    public ReturnRequestService(ReturnRequestRepository returnRequests, PostRepository posts, UserRepository users) {
        this.returnRequests = returnRequests;
        this.posts = posts;
        this.users = users;
    }

    @Transactional
    public ReturnRequestView create(Long postId, CreateReturnRequest request, Principal principal) {
        User user = currentUser(principal);
        String message = request.verificationMessage() == null ? "" : request.verificationMessage().trim();
        if (message.length() < 10 || message.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lời nhắn xác minh cần từ 10 đến 2000 ký tự");
        }

        Post post = posts.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng"));

        if (!"do-that-lac".equalsIgnoreCase(post.getCategory().getSlug())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ bài đăng thuộc chuyên mục đồ thất lạc mới được gửi yêu cầu nhận lại");
        }

        if (post.getItemType() != ItemType.FOUND) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ bài đăng nhặt được đồ (FOUND) mới có thể gửi yêu cầu nhận lại");
        }

        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bài đăng không ở trạng thái sẵn sàng tiếp nhận yêu cầu");
        }

        if (post.getAuthor().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tác giả bài đăng không thể tự gửi yêu cầu nhận đồ của chính mình");
        }

        boolean hasActive = returnRequests.existsByPostIdAndRequesterIdAndStatusIn(
                postId, user.getId(), List.of(ReturnRequestStatus.PENDING, ReturnRequestStatus.APPROVED));
        if (hasActive) {
            throw new ApiException(HttpStatus.CONFLICT, "Bạn đã có yêu cầu đang chờ xử lý hoặc đã được duyệt cho bài đăng này");
        }

        ReturnRequest returnRequest = returnRequests.save(new ReturnRequest(post, user, message));
        return ReturnRequestView.from(returnRequest);
    }

    public List<ReturnRequestView> findByPost(Long postId, Principal principal) {
        User user = currentUser(principal);
        Post post = posts.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng"));

        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem danh sách yêu cầu nhận lại của bài đăng này");
        }

        return returnRequests.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .map(ReturnRequestView::from)
                .toList();
    }

    public List<ReturnRequestView> findMine(Principal principal) {
        User user = currentUser(principal);
        return returnRequests.findByRequesterIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(ReturnRequestView::from)
                .toList();
    }

    @Transactional
    public ReturnRequestView updateStatus(Long requestId, StatusUpdateRequest updateRequest, Principal principal) {
        User user = currentUser(principal);
        ReturnRequest request = returnRequests.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu nhận lại đồ"));

        Post post = request.getPost();

        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền cập nhật trạng thái yêu cầu này");
        }

        if (post.getStatus() == PostStatus.HIDDEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bài đăng đã bị ẩn, không thể thao tác");
        }

        ReturnRequestStatus currentStatus = request.getStatus();
        ReturnRequestStatus newStatus = updateRequest.status();

        if (currentStatus == newStatus) {
            return ReturnRequestView.from(request);
        }

        boolean validTransition = switch (currentStatus) {
            case PENDING -> (newStatus == ReturnRequestStatus.APPROVED || newStatus == ReturnRequestStatus.REJECTED);
            case APPROVED -> (newStatus == ReturnRequestStatus.COMPLETED);
            case REJECTED, COMPLETED -> false;
        };

        if (!validTransition) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Không thể chuyển trạng thái từ " + currentStatus + " sang " + newStatus);
        }

        if (newStatus == ReturnRequestStatus.APPROVED) {
            if (post.getStatus() == PostStatus.RESOLVED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bài đăng đã hoàn tất (RESOLVED), không thể phê duyệt thêm yêu cầu");
            }
            long approvedCount = returnRequests.countByPostIdAndStatus(post.getId(), ReturnRequestStatus.APPROVED);
            if (approvedCount > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Bài đăng này đã có một yêu cầu được phê duyệt. Vui lòng hoàn tất hoặc hủy trước.");
            }
        }

        if (newStatus == ReturnRequestStatus.COMPLETED) {
            post.changeStatus(PostStatus.RESOLVED);
        }

        request.changeStatus(newStatus);
        return ReturnRequestView.from(request);
    }

    private User currentUser(Principal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        }
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không hợp lệ"));
    }
}
