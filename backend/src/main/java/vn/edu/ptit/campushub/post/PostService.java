package vn.edu.ptit.campushub.post;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.campushub.category.Category;
import vn.edu.ptit.campushub.category.CategoryRepository;
import vn.edu.ptit.campushub.comment.Comment;
import vn.edu.ptit.campushub.comment.CommentRepository;
import vn.edu.ptit.campushub.common.ApiException;
import vn.edu.ptit.campushub.post.PostDtos.AuthorView;
import vn.edu.ptit.campushub.post.PostDtos.CategoryView;
import vn.edu.ptit.campushub.post.PostDtos.CommentRequest;
import vn.edu.ptit.campushub.post.PostDtos.CommentView;
import vn.edu.ptit.campushub.post.PostDtos.PostRequest;
import vn.edu.ptit.campushub.post.PostDtos.PostView;
import vn.edu.ptit.campushub.reaction.Reaction;
import vn.edu.ptit.campushub.reaction.ReactionRepository;
import vn.edu.ptit.campushub.user.Role;
import vn.edu.ptit.campushub.user.User;
import vn.edu.ptit.campushub.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository posts;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final CommentRepository comments;
    private final ReactionRepository reactions;

    public PostService(PostRepository posts, CategoryRepository categories, UserRepository users,
                       CommentRepository comments, ReactionRepository reactions) {
        this.posts = posts;
        this.categories = categories;
        this.users = users;
        this.comments = comments;
        this.reactions = reactions;
    }

    public PageView<PostView> search(String keyword, Long categoryId, ItemType itemType, int page, int size, Principal principal) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<Post> result = posts.searchPublished(keyword == null ? "" : keyword.trim(), categoryId, itemType,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<PostView> content = result.getContent().stream()
                .map(post -> toView(post, principal, false))
                .toList();
        return new PageView<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    public PageView<PostView> search(String keyword, Long categoryId, int page, int size, Principal principal) {
        return search(keyword, categoryId, null, page, size, principal);
    }

    public PostView findOne(Long id, Principal principal) {
        return toView(findVisiblePost(id, principal), principal, true);
    }

    @Transactional
    public PostView create(PostRequest request, Principal principal) {
        User author = currentUser(principal);
        Category category = findCategory(request.categoryId());
        validateLostAndFound(request, category);

        boolean isLostAndFound = "do-that-lac".equalsIgnoreCase(category.getSlug());
        ItemType itemType = isLostAndFound ? request.itemType() : null;
        String location = isLostAndFound ? request.location().trim() : null;
        String contactInfo = isLostAndFound ? request.contactInfo().trim() : null;
        Instant occurredAt = isLostAndFound ? request.occurredAt() : null;

        Post post = posts.save(new Post(request.title().trim(), request.content().trim(), category, author,
                itemType, location, contactInfo, occurredAt));
        return toView(post, principal, true);
    }

    @Transactional
    public PostView update(Long id, PostRequest request, Principal principal) {
        Post post = findPost(id);
        requireOwnerOrAdmin(post, currentUser(principal));
        Category category = findCategory(request.categoryId());
        validateLostAndFound(request, category);

        boolean isLostAndFound = "do-that-lac".equalsIgnoreCase(category.getSlug());
        ItemType itemType = isLostAndFound ? request.itemType() : null;
        String location = isLostAndFound ? request.location().trim() : null;
        String contactInfo = isLostAndFound ? request.contactInfo().trim() : null;
        Instant occurredAt = isLostAndFound ? request.occurredAt() : null;

        post.update(request.title().trim(), request.content().trim(), category);
        post.updateLostAndFound(itemType, location, contactInfo, occurredAt);
        return toView(post, principal, true);
    }

    @Transactional
    public PostView changeStatus(Long id, PostStatus status, Principal principal) {
        Post post = findPost(id);
        User user = currentUser(principal);
        if (status == PostStatus.HIDDEN && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được ẩn bài đăng");
        }
        requireOwnerOrAdmin(post, user);
        post.changeStatus(status);
        return toView(post, principal, true);
    }

    @Transactional
    public void delete(Long id, Principal principal) {
        Post post = findPost(id);
        requireOwnerOrAdmin(post, currentUser(principal));
        comments.deleteByPostId(id);
        reactions.deleteByPostId(id);
        posts.delete(post);
    }

    @Transactional
    public CommentView addComment(Long postId, CommentRequest request, Principal principal) {
        Post post = findVisiblePost(postId, principal);
        Comment comment = comments.save(new Comment(request.content().trim(), post, currentUser(principal)));
        return CommentView.from(comment);
    }

    @Transactional
    public boolean toggleReaction(Long postId, Principal principal) {
        Post post = findVisiblePost(postId, principal);
        User user = currentUser(principal);
        return reactions.findByPostIdAndUserId(postId, user.getId())
                .map(reaction -> {
                    reactions.delete(reaction);
                    return false;
                })
                .orElseGet(() -> {
                    reactions.save(new Reaction(post, user));
                    return true;
                });
    }

    private void validateLostAndFound(PostRequest request, Category category) {
        boolean isLostAndFound = "do-that-lac".equalsIgnoreCase(category.getSlug());
        if (isLostAndFound) {
            if (request.itemType() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bài đăng đồ thất lạc bắt buộc phải có loại tin (LOST hoặc FOUND)");
            }
            if (request.location() == null || request.location().trim().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bài đăng đồ thất lạc bắt buộc phải có địa điểm");
            }
            if (request.contactInfo() == null || request.contactInfo().trim().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bài đăng đồ thất lạc bắt buộc phải có thông tin liên hệ");
            }
            if (request.occurredAt() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bài đăng đồ thất lạc bắt buộc phải có thời gian xảy ra");
            }
            if (request.occurredAt().isAfter(Instant.now())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Thời gian xảy ra không được ở tương lai");
            }
        } else {
            if (request.itemType() != null
                    || (request.location() != null && !request.location().trim().isEmpty())
                    || (request.contactInfo() != null && !request.contactInfo().trim().isEmpty())
                    || request.occurredAt() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bài viết chuyên mục thông thường không được chứa thông tin đồ thất lạc");
            }
        }
    }

    private String resolveContactInfo(Post post, User current) {
        if (post.getContactInfo() == null) {
            return null;
        }
        if (current != null) {
            return post.getContactInfo();
        }
        return "Đăng nhập để xem liên hệ";
    }

    private PostView toView(Post post, Principal principal, boolean includeComments) {
        User current = findCurrentUser(principal);
        List<CommentView> commentViews = includeComments
                ? comments.findByPostIdOrderByCreatedAtAsc(post.getId()).stream().map(CommentView::from).toList()
                : List.of();
        String contact = resolveContactInfo(post, current);
        return new PostView(post.getId(), post.getTitle(), post.getContent(), post.getStatus(),
                post.getItemType(), post.getLocation(), contact, post.getOccurredAt(),
                new CategoryView(post.getCategory().getId(), post.getCategory().getName(), post.getCategory().getSlug()),
                new AuthorView(post.getAuthor().getId(), post.getAuthor().getUsername(),
                        post.getAuthor().getDisplayName()),
                post.getCreatedAt(), post.getUpdatedAt(), reactions.countByPostId(post.getId()),
                comments.countByPostId(post.getId()),
                current != null && reactions.findByPostIdAndUserId(post.getId(), current.getId()).isPresent(),
                commentViews);
    }

    private Post findVisiblePost(Long id, Principal principal) {
        Post post = findPost(id);
        User current = findCurrentUser(principal);
        if (post.getStatus() == PostStatus.HIDDEN
                && (current == null || current.getRole() != Role.ADMIN)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng");
        }
        return post;
    }

    private Post findPost(Long id) {
        return posts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng"));
    }

    private Category findCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Chuyên mục không hợp lệ"));
    }

    private User currentUser(Principal principal) {
        User user = findCurrentUser(principal);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        }
        return user;
    }

    private User findCurrentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return users.findByUsername(principal.getName()).orElse(null);
    }

    private void requireOwnerOrAdmin(Post post, User user) {
        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền thay đổi bài đăng này");
        }
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
