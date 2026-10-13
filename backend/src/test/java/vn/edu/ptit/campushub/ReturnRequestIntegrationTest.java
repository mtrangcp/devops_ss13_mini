package vn.edu.ptit.campushub;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.edu.ptit.campushub.user.Role;
import vn.edu.ptit.campushub.user.User;
import vn.edu.ptit.campushub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ReturnRequestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("otheruser").isEmpty()) {
            userRepository.save(new User("otheruser", "Người Dùng B",
                    passwordEncoder.encode("other123"), Role.USER));
        }
        if (userRepository.findByUsername("thirduser").isEmpty()) {
            userRepository.save(new User("thirduser", "Người Dùng C",
                    passwordEncoder.encode("third123"), Role.USER));
        }
    }

    private long createFoundPost(String title) throws Exception {
        String body = """
                {
                    "title": "%s",
                    "content": "Nhặt được đồ rơi ở khuôn viên trường, vui lòng liên hệ xác minh.",
                    "categoryId": 2,
                    "itemType": "FOUND",
                    "location": "Sân A2 cạnh căng tin",
                    "contactInfo": "0912345678",
                    "occurredAt": "2026-10-13T08:00:00Z"
                }
                """.formatted(title);

        MvcResult result = mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private long createLostPost(String title) throws Exception {
        String body = """
                {
                    "title": "%s",
                    "content": "Tôi làm rơi đồ, bạn nào thấy cho mình xin lại.",
                    "categoryId": 2,
                    "itemType": "LOST",
                    "location": "Thư viện tầng 2",
                    "contactInfo": "0912345678",
                    "occurredAt": "2026-10-13T08:00:00Z"
                }
                """.formatted(title);

        MvcResult result = mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    @Test
    void userCanSubmitValidReturnRequest() throws Exception {
        long postId = createFoundPost("Nhặt được ví nam màu đen");

        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Ví da màu đen bên trong có thẻ sinh viên tên Nguyễn Văn B."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verificationMessage").value("Ví da màu đen bên trong có thẻ sinh viên tên Nguyễn Văn B."))
                .andExpect(jsonPath("$.requester.username").value("otheruser"));
    }

    @Test
    void anonymousCannotSubmitReturnRequest() throws Exception {
        long postId = createFoundPost("Nhặt được tai nghe AirPods");

        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Tai nghe của mình bị rơi chiều nay."
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorCannotSubmitReturnRequestOnOwnPost() throws Exception {
        long postId = createFoundPost("Nhặt được chìa khóa xe máy");

        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Tự nhận là chìa khóa của tôi để test."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tác giả bài đăng không thể tự gửi yêu cầu nhận đồ của chính mình"));
    }

    @Test
    void missingOrTooShortVerificationMessageRejected() throws Exception {
        long postId = createFoundPost("Nhặt được bình giữ nhiệt");

        // Ngắn hơn 10 ký tự
        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Của tôi"
                                }
                                """))
                .andExpect(status().isBadRequest());

        // Bỏ trống
        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateActiveRequestReturnsConflict() throws Exception {
        long postId = createFoundPost("Nhặt được đồng hồ Casio");

        // Gửi lần 1 thành công
        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Đồng hồ Casio màu bạc mặt kính có vết xước nhỏ."
                                }
                                """))
                .andExpect(status().isCreated());

        // Gửi lần 2 khi lần 1 đang PENDING -> 409 Conflict
        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Gửi lại lời nhắn lần hai để kiểm tra duplicate."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bạn đã có yêu cầu đang chờ xử lý hoặc đã được duyệt cho bài đăng này"));
    }

    @Test
    void returnRequestOnLostPostRejected() throws Exception {
        long lostPostId = createLostPost("Mất laptop Dell Inspiron");

        mockMvc.perform(post("/api/posts/" + lostPostId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Tôi muốn xin nhận lại chiếc máy tính này."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chỉ bài đăng nhặt được đồ (FOUND) mới có thể gửi yêu cầu nhận lại"));
    }

    @Test
    void returnRequestOnResolvedPostRejected() throws Exception {
        long postId = createFoundPost("Nhặt được cặp tài liệu");

        // Chủ bài đánh dấu hoàn tất
        mockMvc.perform(patch("/api/posts/" + postId + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "RESOLVED"
                                }
                                """))
                .andExpect(status().isOk());

        // Gửi yêu cầu vào bài đã RESOLVED -> bị từ chối
        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Xin nhận lại tài liệu môn học của mình."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bài đăng không ở trạng thái sẵn sàng tiếp nhận yêu cầu"));
    }

    @Test
    void nonOwnerCannotApproveReturnRequest() throws Exception {
        long postId = createFoundPost("Nhặt được kính cận");

        MvcResult result = mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Kính gọng đen độ cận 2.5 đi kèm hộp xanh."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // thirduser (không phải chủ bài) cố gắng APPROVE
        mockMvc.perform(patch("/api/return-requests/" + requestId + "/status")
                        .with(httpBasic("thirduser", "third123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền cập nhật trạng thái yêu cầu này"));
    }

    @Test
    void ownerCanApproveAndRejectReturnRequest() throws Exception {
        long postId = createFoundPost("Nhặt được thẻ xe buýt");

        // otheruser gửi yêu cầu 1
        MvcResult res1 = mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Thẻ xe buýt tuyến 02 có dán sticker anime."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long req1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asLong();

        // Chủ bài duyệt yêu cầu 1 (PENDING -> APPROVED)
        mockMvc.perform(patch("/api/return-requests/" + req1 + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Tạo bài khác để test REJECT
        long postId2 = createFoundPost("Nhặt được ô che mưa");
        MvcResult res2 = mockMvc.perform(post("/api/posts/" + postId2 + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Ô màu đỏ gấp 3 bị bung nan nhẹ."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long req2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asLong();

        // Chủ bài từ chối yêu cầu 2 (PENDING -> REJECTED)
        mockMvc.perform(patch("/api/return-requests/" + req2 + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "REJECTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void directTransitionFromPendingToCompletedRejected() throws Exception {
        long postId = createFoundPost("Nhặt được tai nghe Sony");

        MvcResult result = mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Tai nghe Sony màu trắng có kèm dây sạc Type-C."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long reqId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // Nhảy cóc từ PENDING trực tiếp lên COMPLETED -> 400 Bad Request
        mockMvc.perform(patch("/api/return-requests/" + reqId + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Không thể chuyển trạng thái từ PENDING sang COMPLETED")));
    }

    @Test
    void approvedToCompletedResolvesPost() throws Exception {
        long postId = createFoundPost("Nhặt được túi đựng laptop");

        MvcResult res = mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Túi laptop chống sốc hiệu Tomtoc màu xám."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long reqId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Bước 1: APPROVE
        mockMvc.perform(patch("/api/return-requests/" + reqId + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Bước 2: COMPLETE
        mockMvc.perform(patch("/api/return-requests/" + reqId + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Kiểm tra post đã tự động chuyển sang RESOLVED
        mockMvc.perform(get("/api/posts/" + postId)
                        .with(httpBasic("student", "student123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void cannotApproveTwoRequestsOnSamePost() throws Exception {
        long postId = createFoundPost("Nhặt được chìa khóa tủ đồ");

        // otheruser gửi yêu cầu
        MvcResult res1 = mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Chìa khóa số 104 móc nhựa màu đỏ."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long req1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asLong();

        // thirduser cũng gửi yêu cầu
        MvcResult res2 = mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("thirduser", "third123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Tôi nghĩ chìa khóa này của tôi số 104."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long req2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asLong();

        // Chủ bài duyệt req1 thành công
        mockMvc.perform(patch("/api/return-requests/" + req1 + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk());

        // Chủ bài cố duyệt thêm req2 trong khi req1 đang APPROVED -> 409 Conflict
        mockMvc.perform(patch("/api/return-requests/" + req2 + "/status")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bài đăng này đã có một yêu cầu được phê duyệt. Vui lòng hoàn tất hoặc hủy trước."));
    }

    @Test
    void requesterOnlyViewsOwnRequestsInMine() throws Exception {
        long postId1 = createFoundPost("Nhặt được bút cảm ứng");
        long postId2 = createFoundPost("Nhặt được sạc dự phòng Anker");

        // otheruser gửi yêu cầu cho bài 1
        mockMvc.perform(post("/api/posts/" + postId1 + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Bút stylus Apple Pencil 2 có khắc chữ B."
                                }
                                """))
                .andExpect(status().isCreated());

        // thirduser gửi yêu cầu cho bài 2
        mockMvc.perform(post("/api/posts/" + postId2 + "/return-requests")
                        .with(httpBasic("thirduser", "third123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Sạc dự phòng Anker 10000mAh màu đen trầy mặt sau."
                                }
                                """))
                .andExpect(status().isCreated());

        // otheruser gọi /mine -> chỉ thấy yêu cầu của otheruser
        mockMvc.perform(get("/api/return-requests/mine")
                        .with(httpBasic("otheruser", "other123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].requester.username", everyItem(is("otheruser"))))
                .andExpect(jsonPath("$[?(@.verificationMessage == 'Bút stylus Apple Pencil 2 có khắc chữ B.')]").exists())
                .andExpect(jsonPath("$[?(@.verificationMessage == 'Sạc dự phòng Anker 10000mAh màu đen trầy mặt sau.')]").doesNotExist());

        // thirduser gọi /mine -> chỉ thấy yêu cầu của thirduser
        mockMvc.perform(get("/api/return-requests/mine")
                        .with(httpBasic("thirduser", "third123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].requester.username", everyItem(is("thirduser"))))
                .andExpect(jsonPath("$[?(@.verificationMessage == 'Sạc dự phòng Anker 10000mAh màu đen trầy mặt sau.')]").exists())
                .andExpect(jsonPath("$[?(@.verificationMessage == 'Bút stylus Apple Pencil 2 có khắc chữ B.')]").doesNotExist());
    }

    @Test
    void otherUserCannotViewVerificationMessagesOfPost() throws Exception {
        long postId = createFoundPost("Nhặt được áo khoác đồng phục");

        mockMvc.perform(post("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "verificationMessage": "Áo size L bên trong túi có vé gửi xe số 45."
                                }
                                """))
                .andExpect(status().isCreated());

        // thirduser (người ngoài) cố xem danh sách return-requests của bài này -> 403 Forbidden
        mockMvc.perform(get("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("thirduser", "third123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền xem danh sách yêu cầu nhận lại của bài đăng này"));

        // Chưa đăng nhập -> 401 Unauthorized
        mockMvc.perform(get("/api/posts/" + postId + "/return-requests"))
                .andExpect(status().isUnauthorized());

        // Chủ bài (student) xem -> 200 OK
        mockMvc.perform(get("/api/posts/" + postId + "/return-requests")
                        .with(httpBasic("student", "student123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].verificationMessage").value("Áo size L bên trong túi có vé gửi xe số 45."));
    }
}
