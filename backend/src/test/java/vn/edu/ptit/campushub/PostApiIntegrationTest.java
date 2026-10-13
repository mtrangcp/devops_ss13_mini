package vn.edu.ptit.campushub;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class PostApiIntegrationTest {

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
            userRepository.save(new User("otheruser", "Người Dùng Khác",
                    passwordEncoder.encode("other123"), Role.USER));
        }
    }

    @Test
    void publicUserCanReadSeededPosts() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void anonymousUserCannotCreatePost() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bài mới","content":"Nội dung hợp lệ","categoryId":1}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCreateRegularPost() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tìm nhóm học DevOps","content":"Cần thêm hai thành viên cùng thực hành.","categoryId":6}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Tìm nhóm học DevOps"))
                .andExpect(jsonPath("$.author.username").value("student"))
                .andExpect(jsonPath("$.itemType").value(nullValue()))
                .andExpect(jsonPath("$.location").value(nullValue()));
    }

    @Test
    void invalidPostReturnsReadableMessage() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","content":"Nội dung","categoryId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void authenticatedUserCanCreateValidLostPost() throws Exception {
        String body = """
                {
                    "title": "Mất thẻ sinh viên tại giảng đường B3",
                    "content": "Mình để quên thẻ sinh viên số B21DCCN001 trên bàn giảng đường B3 sáng nay.",
                    "categoryId": 2,
                    "itemType": "LOST",
                    "location": "Giảng đường B3 tầng 2",
                    "contactInfo": "0912345678",
                    "occurredAt": "2026-10-13T08:30:00Z"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Mất thẻ sinh viên tại giảng đường B3"))
                .andExpect(jsonPath("$.category.slug").value("do-that-lac"))
                .andExpect(jsonPath("$.itemType").value("LOST"))
                .andExpect(jsonPath("$.location").value("Giảng đường B3 tầng 2"))
                .andExpect(jsonPath("$.contactInfo").value("0912345678"))
                .andExpect(jsonPath("$.occurredAt").value("2026-10-13T08:30:00Z"))
                .andExpect(jsonPath("$.author.username").value("student"));
    }

    @Test
    void authenticatedUserCanCreateValidFoundPost() throws Exception {
        String body = """
                {
                    "title": "Nhặt được chùm chìa khóa xe máy",
                    "content": "Chùm chìa khóa Honda có móc hình gấu ở nhà xe tầng hầm.",
                    "categoryId": 2,
                    "itemType": "FOUND",
                    "location": "Nhà xe tầng hầm khu A",
                    "contactInfo": "0987654321",
                    "occurredAt": "2026-10-13T09:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Nhặt được chùm chìa khóa xe máy"))
                .andExpect(jsonPath("$.itemType").value("FOUND"))
                .andExpect(jsonPath("$.location").value("Nhà xe tầng hầm khu A"))
                .andExpect(jsonPath("$.contactInfo").value("0987654321"));
    }

    @Test
    void missingLostFoundFieldsRejected() throws Exception {
        // Thiếu itemType
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Mất balo",
                                    "content": "Để quên balo màu xám",
                                    "categoryId": 2,
                                    "location": "Căn tin",
                                    "contactInfo": "0911111111",
                                    "occurredAt": "2026-10-13T08:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bài đăng đồ thất lạc bắt buộc phải có loại tin (LOST hoặc FOUND)"));

        // Thiếu location
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Mất balo",
                                    "content": "Để quên balo màu xám",
                                    "categoryId": 2,
                                    "itemType": "LOST",
                                    "contactInfo": "0911111111",
                                    "occurredAt": "2026-10-13T08:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bài đăng đồ thất lạc bắt buộc phải có địa điểm"));

        // Thiếu occurredAt
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Mất balo",
                                    "content": "Để quên balo màu xám",
                                    "categoryId": 2,
                                    "itemType": "LOST",
                                    "location": "Căn tin",
                                    "contactInfo": "0911111111"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bài đăng đồ thất lạc bắt buộc phải có thời gian xảy ra"));

        // occurredAt ở tương lai
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Mất balo",
                                    "content": "Để quên balo màu xám",
                                    "categoryId": 2,
                                    "itemType": "LOST",
                                    "location": "Căn tin",
                                    "contactInfo": "0911111111",
                                    "occurredAt": "2099-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thời gian xảy ra không được ở tương lai"));
    }

    @Test
    void regularCategoryWithLostFoundFieldsRejected() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Hội thảo lập trình Web",
                                    "content": "Giới thiệu Spring Boot 3",
                                    "categoryId": 1,
                                    "itemType": "LOST",
                                    "location": "Hội trường 1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bài viết chuyên mục thông thường không được chứa thông tin đồ thất lạc"));
    }

    @Test
    void filterPostsByItemType() throws Exception {
        // Tạo 1 bài LOST
        mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Đánh rơi tai nghe AirPods",
                                    "content": "Rơi tại sân bóng rổ",
                                    "categoryId": 2,
                                    "itemType": "LOST",
                                    "location": "Sân bóng rổ",
                                    "contactInfo": "0922222222",
                                    "occurredAt": "2026-10-13T07:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        // Lọc itemType=LOST
        mockMvc.perform(get("/api/posts?itemType=LOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].itemType", everyItem(is("LOST"))));
    }

    @Test
    void otherUserCannotUpdatePost() throws Exception {
        // student tạo bài
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Mất máy tính Casio 580",
                                    "content": "Để quên ở phòng A2-301",
                                    "categoryId": 2,
                                    "itemType": "LOST",
                                    "location": "Phòng A2-301",
                                    "contactInfo": "0933333333",
                                    "occurredAt": "2026-10-13T08:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        long postId = jsonNode.get("id").asLong();

        // otheruser cố gắng sửa bài của student
        mockMvc.perform(put("/api/posts/" + postId)
                        .with(httpBasic("otheruser", "other123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Hack bài đăng",
                                    "content": "Nội dung đã bị can thiệp",
                                    "categoryId": 2,
                                    "itemType": "LOST",
                                    "location": "Phòng A2-301",
                                    "contactInfo": "0999999999",
                                    "occurredAt": "2026-10-13T08:00:00Z"
                                }
                                """))
                .andExpect(status().isForbidden());

        // student (chủ bài) sửa bài thành công
        mockMvc.perform(put("/api/posts/" + postId)
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Mất máy tính Casio 580 - Đã cập nhật",
                                    "content": "Để quên ở phòng A2-301 hoặc A2-302",
                                    "categoryId": 2,
                                    "itemType": "LOST",
                                    "location": "Phòng A2-301 hoặc A2-302",
                                    "contactInfo": "0933333333",
                                    "occurredAt": "2026-10-13T08:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mất máy tính Casio 580 - Đã cập nhật"))
                .andExpect(jsonPath("$.location").value("Phòng A2-301 hoặc A2-302"));
    }

    @Test
    void postResponseDataMatchesPersistedData() throws Exception {
        String originalContent = "Để quên sổ tay bìa da màu nâu tại thư viện bàn 12";
        String body = """
                {
                    "title": "Tìm sổ tay ghi chép môn DevOps",
                    "content": "%s",
                    "categoryId": 2,
                    "itemType": "LOST",
                    "location": "Bàn 12 tầng 2 thư viện",
                    "contactInfo": "student_contact@ptit.edu.vn",
                    "occurredAt": "2026-10-13T09:15:00Z"
                }
                """.formatted(originalContent);

        MvcResult createResult = mockMvc.perform(post("/api/posts")
                        .with(httpBasic("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long postId = createdNode.get("id").asLong();

        // Đọc lại bài đăng khi đã đăng nhập
        mockMvc.perform(get("/api/posts/" + postId)
                        .with(httpBasic("student", "student123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.title").value("Tìm sổ tay ghi chép môn DevOps"))
                .andExpect(jsonPath("$.content").value(originalContent))
                .andExpect(jsonPath("$.itemType").value("LOST"))
                .andExpect(jsonPath("$.location").value("Bàn 12 tầng 2 thư viện"))
                .andExpect(jsonPath("$.contactInfo").value("student_contact@ptit.edu.vn"))
                .andExpect(jsonPath("$.occurredAt").value("2026-10-13T09:15:00Z"))
                .andExpect(jsonPath("$.author.username").value("student"));

        // Đọc bài đăng khi chưa đăng nhập: contactInfo được bảo vệ
        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.contactInfo").value("Đăng nhập để xem liên hệ"));
    }
}
