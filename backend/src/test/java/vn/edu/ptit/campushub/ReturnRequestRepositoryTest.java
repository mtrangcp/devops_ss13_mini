package vn.edu.ptit.campushub;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.campushub.category.Category;
import vn.edu.ptit.campushub.category.CategoryRepository;
import vn.edu.ptit.campushub.post.ItemType;
import vn.edu.ptit.campushub.post.Post;
import vn.edu.ptit.campushub.post.PostRepository;
import vn.edu.ptit.campushub.returnrequest.ReturnRequest;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestRepository;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestStatus;
import vn.edu.ptit.campushub.user.User;
import vn.edu.ptit.campushub.user.UserRepository;

@SpringBootTest
@Transactional
class ReturnRequestRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    @Test
    void canSaveAndRetrieveLostFoundPostAndReturnRequest() {
        User student = userRepository.findByUsername("student").orElseThrow();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        Category lostCategory = categoryRepository.findBySlug("do-that-lac").orElseThrow();

        Instant now = Instant.now();
        Post lostPost = new Post(
                "Mất tai nghe Bluetooth Sony",
                "Rơi ở phòng tự học thư viện tầng 2 chiều nay.",
                lostCategory,
                student,
                ItemType.LOST,
                "Thư viện tầng 2",
                "0987654321",
                now
        );
        Post savedPost = postRepository.save(lostPost);

        assertThat(savedPost.getId()).isNotNull();
        assertThat(savedPost.getItemType()).isEqualTo(ItemType.LOST);
        assertThat(savedPost.getLocation()).isEqualTo("Thư viện tầng 2");
        assertThat(savedPost.getContactInfo()).isEqualTo("0987654321");
        assertThat(savedPost.getOccurredAt()).isEqualTo(now);

        ReturnRequest request = new ReturnRequest(
                savedPost,
                admin,
                "Mình đang giữ tai nghe này tại bàn trực thư viện, có tem dán tên bạn."
        );
        ReturnRequest savedRequest = returnRequestRepository.save(request);

        assertThat(savedRequest.getId()).isNotNull();
        assertThat(savedRequest.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
        assertThat(savedRequest.getVersion()).isNotNull();

        List<ReturnRequest> requests = returnRequestRepository.findByPostIdOrderByCreatedAtDesc(savedPost.getId());
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getVerificationMessage()).contains("bàn trực thư viện");

        savedRequest.changeStatus(ReturnRequestStatus.APPROVED);
        returnRequestRepository.save(savedRequest);

        long approvedCount = returnRequestRepository.countByPostIdAndStatus(savedPost.getId(), ReturnRequestStatus.APPROVED);
        assertThat(approvedCount).isEqualTo(1);
    }
}
