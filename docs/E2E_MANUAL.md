# Kiểm thử E2E thủ công

Trạng thái hiện tại: **NOT TESTED bằng trình duyệt**. Công cụ browser trả về không có browser khả dụng. HTTP smoke checks và tests backend không thay thế browser E2E. Không tạo tài khoản, bài hoặc yêu cầu mới trong đợt audit này.

Chỉ thực hiện luồng có ghi dữ liệu dưới đây khi chủ dự án cho phép, với tài khoản và nội dung kiểm thử do chủ dự án chọn. Không sử dụng dữ liệu cá nhân thật của người khác. Không gửi mật khẩu hoặc header Authorization trong báo cáo. Có thể dùng hai browser/profile độc lập cho A và B; `sessionStorage` của ứng dụng lưu thông tin đăng nhập theo tab.

## Luồng chính

| Bước | Thao tác thủ công | Kết quả cần ghi nhận |
|---|---|---|
| 1 | Mở http://localhost:8088, mở DevTools Network/Console | Giao diện tải được; HTML/JS/CSS và categories HTTP 200; không lỗi JavaScript |
| 2 | Chọn Đăng nhập → Chưa có tài khoản? Đăng ký; đăng ký A và B bằng thông tin được phép | Mỗi lần đăng ký trả 201, không có lỗi UI |
| 3 | A đăng nhập, Đăng bài, chọn Đồ thất lạc và loại Nhặt được (FOUND) | Điền tiêu đề, nội dung, địa điểm, liên hệ và thời gian không ở tương lai; POST /api/posts trả 201; lưu ID bài |
| 4 | B đăng nhập, mở chi tiết bài FOUND của A | Thấy nút Yêu cầu nhận lại đồ |
| 5 | B gửi lời nhắn xác minh dài 10–2000 ký tự | POST /api/posts/{id}/return-requests trả 201; lưu ID yêu cầu; trạng thái PENDING |
| 6 | A đăng nhập, mở bài và phần Yêu cầu nhận đồ, chọn Chấp nhận | PATCH /api/return-requests/{id}/status thành công; APPROVED |
| 7 | A chọn Xác nhận đã trao trả | Yêu cầu COMPLETED; chi tiết bài hiển thị Đã hoàn tất; GET /api/posts/{id} trả status=RESOLVED |
| 8 | Reload tab A và B | Mở lại bài qua ID đã lưu hoặc mục yêu cầu của B; yêu cầu vẫn COMPLETED, bài vẫn RESOLVED |

Lưu ý: danh sách bài chính tìm các bài PUBLISHED. Bài đã RESOLVED có thể không còn trong danh sách. Việc biến mất khỏi danh sách không đủ để kết luận mất dữ liệu. Dùng mục yêu cầu của B hoặc kiểm tra GET /api/posts/{id}; ứng dụng hiện không cung cấp URL riêng cho từng bài.

## Trường hợp âm

Thực hiện kiểm tra duplicate và quyền trước bước COMPLETED của luồng chính. Không cố tạo thêm bản ghi nếu chỉ cần quan sát UI.

| Tình huống | Cách kiểm tra | Kết quả mong đợi | Hiện đã kiểm chứng |
|---|---|---|---|
| Form trống | Submit đăng ký, đăng nhập, đăng bài và yêu cầu khi thiếu trường bắt buộc | Browser validation/notice; không tạo bản ghi; API thiếu dữ liệu bị từ chối | Validation backend PASS trên H2; UI NOT TESTED |
| Chưa đăng nhập | Mở FOUND đang PUBLISHED và bấm Đăng nhập để yêu cầu nhận đồ | Hiện dialog đăng nhập; không gửi yêu cầu trái phép | Protected GET trên stack thật trả 401; UI NOT TESTED |
| Không có quyền | B mở bài của A | Không có thao tác quản lý; nếu gửi PATCH duyệt qua DevTools bằng B thì trả 403, trạng thái không đổi | Tests phân quyền backend PASS trên H2; UI/stack PATCH NOT TESTED |
| Duplicate | B gửi lần hai cho cùng bài khi yêu cầu PENDING hoặc APPROVED | API trả 409, thông báo có yêu cầu đang hoạt động; không thêm yêu cầu | Backend PASS trên H2; UI/stack POST NOT TESTED |
| Bài RESOLVED | B mở chi tiết sau COMPLETED | Không còn nút yêu cầu mới; nếu gửi POST qua DevTools thì trả 400 | Backend PASS trên H2; UI/stack POST NOT TESTED |

Ghi vào biên bản: người kiểm tra, thời gian, build/release ID, ID bài/yêu cầu, trạng thái từng bước, HTTP status và ảnh chụp đã che dữ liệu nhạy cảm. Chỉ đánh dấu PASS sau khi thực thi. Không suy ra PASS từ việc đọc source.
