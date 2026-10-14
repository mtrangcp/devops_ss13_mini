# Triển khai Campus Hub trên VPS Ubuntu

Tài liệu là phương án chuẩn bị, **chưa triển khai public**. Chỉ thực hiện thao tác trên VPS sau khi chủ dự án duyệt. Không thay đổi các migration đã áp dụng hoặc xóa volume.

## Điều kiện trước release

- Hoàn thành checklist browser trong `E2E_MANUAL.md`; thử backup/restore ở môi trường tách biệt.
- Review và tạo commit chứa Dockerfiles, Compose, Nginx, cấu hình seeder và tài liệu. HEAD tại audit là `ac6b9a6d8bf223160c4c4630e6df893f65e8b380`, nhưng các file Docker đang chưa commit; chỉ lấy HEAD này sẽ thiếu chúng.
- Gán release ID bất biến theo commit đã review. Giữ image của release trước và ghi image digest, SHA, phiên bản schema, file cấu hình và backup tương ứng.
- Quyết định mang dữ liệu hiện có sang VPS hay khởi tạo database mới. Có hai bài và dữ liệu thử từ lần triển khai local trước; đợt audit này không tạo thêm. Không tự chuyển các bản ghi này thành dữ liệu production.
- Chấp nhận hoặc xử lý cảnh báo Flyway 11.7.2/MySQL 8.4, và hai npm audit high ở dependency development. Chưa có CI/CD; thực hiện tests/build bằng tay trước release.

## Ubuntu và Docker

Chọn VPS Ubuntu được Docker hỗ trợ. Cài Engine, Buildx và Compose plugin theo [hướng dẫn Docker chính thức](https://docs.docker.com/engine/install/ubuntu/); kiểm tra máy hiện có trước khi thay thế package. Không dùng script cài đặt không rõ nguồn.

Sau khi cấu hình repository APT chính thức theo hướng dẫn trên:

```bash
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

Đặt source release đã review tại `/opt/campus-hub`. Không copy `.env` local, node_modules hoặc build artifacts. Chuẩn bị quyền truy cập SSH và backup; chỉ cho phép SSH từ nguồn quản trị và TCP 80/443 cho web. Không mở 3306, 33060, 8080 hoặc 8088 trên firewall/cloud security group. Docker frontend vẫn bind loopback; không dựa riêng vào UFW để bảo vệ port Docker đã publish.

## Biến môi trường production

Tạo `.env` ngoài Git, quyền 600, trong root release; dùng mật khẩu production mới do quản trị viên sinh và lưu ở nơi quản lý secrets. Không đưa giá trị vào shell history, log hoặc ảnh chụp.

| Biến | Yêu cầu |
|---|---|
| APP_PORT | 8088 hoặc cổng loopback khác khớp reverse proxy |
| MYSQL_DATABASE | Giữ tên database tương ứng backup; ví dụ campus_hub |
| MYSQL_USER | User ứng dụng riêng; không dùng root cho backend |
| MYSQL_PASSWORD | Mật khẩu ngẫu nhiên mạnh cho user ứng dụng |
| MYSQL_ROOT_PASSWORD | Mật khẩu ngẫu nhiên khác, dùng quản trị/backup |
| PUBLIC_ORIGIN | Origin HTTPS chính xác, không có path; ví dụ https://lostfound.example.com |
| RELEASE_ID | Tag bất biến của release đã review, không dùng latest |

`APP_DEMO_ENABLED=false` được ép trong Compose và overlay. URL/user/password Spring được Compose ánh xạ, không cần VITE_API_URL khi frontend dùng `/api`. `PUBLIC_ORIGIN` chỉ được ánh xạ vào CORS khi dùng overlay production. Thay mật khẩu `.env` không đổi user đã tồn tại trong volume; rotation cần quy trình riêng.

Chạy các lệnh Bash dưới đây trong root release. Giữ project name `campus-hub-base` trên cùng VPS qua mọi release để tiếp tục dùng `campus-hub-base_mysql_data`:

```bash
chmod 600 .env
dc() { sudo docker compose -p campus-hub-base -f compose.yaml -f deploy/compose.production.yaml "$@"; }
dc config --quiet
dc build backend frontend
```

Image được gắn tag `campus-hub-backend:${RELEASE_ID}` và `campus-hub-frontend:${RELEASE_ID}` từ overlay. Không ghi đè tag đã release. Không chạy backend trước khi restore nếu cần chuyển database hiện có.

Database mới đã được chủ dự án chọn:

```bash
dc up -d --no-build
dc ps
dc logs --tail=100 backend
curl --fail http://127.0.0.1:8088/api/categories
```

## Domain và HTTPS

Đề xuất Caddy chạy trên host Ubuntu, proxy vào Nginx loopback. Trỏ DNS A về IPv4 VPS; chỉ thêm AAAA nếu IPv6 thực sự hoạt động. Cài Caddy theo [hướng dẫn chính thức](https://caddyserver.com/docs/install), giữ thư mục dữ liệu chứng chỉ và bật service khi boot. Caddy có [automatic HTTPS và renewal](https://caddyserver.com/docs/automatic-https); domain và cổng challenge phải truy cập được.

Mẫu `/etc/caddy/Caddyfile`, thay domain bằng domain được duyệt:

```caddyfile
lostfound.example.com {
    reverse_proxy 127.0.0.1:8088
}
```

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Đường đi là HTTPS → Caddy host → Nginx container → backend → MySQL. `/api/` được giữ nguyên vì `proxy_pass http://backend:8080` không có URI suffix. HTTP Basic chỉ dùng qua HTTPS public. Caddy xử lý TLS; Nginx hiện chỉ nghe HTTP nội bộ và không cấp chứng chỉ.

Nginx hiện ghi đè `X-Forwarded-Proto` bằng `$scheme` nội bộ. Frontend dùng API relative và backend hiện không tạo redirect nên đây chưa phải lỗi runtime đã quan sát; nếu sau này backend cần biết origin HTTPS để sinh redirect/URL, phải cấu hình chuỗi proxy và trusted forwarded headers rồi kiểm thử trước release. Không tự tin cậy forwarded headers từ client Internet.

Kiểm tra thực tế sau deploy: chứng chỉ/domain đúng, HTTP redirect sang HTTPS, frontend assets và `/api/categories` HTTP 200, protected API anonymous 401, browser E2E, không có mixed content, renewal timer hoạt động. Tất cả hiện **NOT TESTED public**.

## Backup MySQL và chuyển VPS

Quy trình logical dump bên dưới dựa trên [MySQL mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html). Snapshot InnoDB dùng `--single-transaction`; không chạy migration/DDL đồng thời. Với cutover cần dừng frontend/backend hoặc đóng ghi trước snapshot cuối, để không bỏ sót thay đổi sau snapshot.

Các lệnh sau dành cho **Bash trên Ubuntu**, không dùng PowerShell 5 redirection vì có thể tạo SQL UTF-16. Tạo thư mục backup ngoài repository, quyền 700, cho user vận hành. Chạy trong root release, dùng hàm `dc` ở trên:

```bash
set -euo pipefail
umask 077
backup_dir=/var/backups/campus-hub
# Quản trị viên chuẩn bị backup_dir với owner phù hợp trước khi chạy.
backup_path="$backup_dir/campus-hub-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
dc exec -T db sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases "$MYSQL_DATABASE"' \
    | gzip > "$backup_path.partial"
gzip -t "$backup_path.partial"
mv "$backup_path.partial" "$backup_path"
sha256sum "$backup_path" > "$backup_path.sha256"
```

Không in SQL dump hoặc credentials. Backup gồm users và password hashes, cần mã hóa bản offsite và giới hạn quyền truy cập. Đề xuất chạy hằng ngày và trước release; giữ 7 bản ngày, 4 bản tuần, 3 bản tháng theo dung lượng/RPO được chủ dự án duyệt. Không chỉ giữ backup trên chính VPS. Lên lịch restore drill định kỳ; gzip/checksum PASS không chứng minh restore PASS.

Chuyển VPS: giữ nguyên máy/volume nguồn; ngừng ghi trong thời gian cutover, xuất snapshot cuối, chuyển backup qua kênh SSH bảo mật và xác minh checksum. Với nguồn Windows, xuất bằng mysqldump `--result-file` bên trong container và copy file ra bằng `docker compose cp`, không pipe SQL qua PowerShell `>`; chưa thực hiện export trong audit này.

Khởi động chỉ database đích trên VPS dùng cùng MySQL 8.4.8. Chờ `dc ps` báo db healthy. **Restore chỉ vào database đích mới/rỗng đã được xác nhận**, chưa có backend chạy; dump chứa DROP TABLE nên không dùng lệnh này vào database có dữ liệu cần giữ:

```bash
dc up -d db
# Xác nhận healthy và target mới/rỗng trước khi tiếp tục.
gzip -dc "$backup_path" \
    | dc exec -T db sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --default-character-set=utf8mb4'
dc up -d --no-build backend frontend
dc ps
printf '%s\n' 'SELECT version, success FROM flyway_schema_history ORDER BY installed_rank; SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM posts; SELECT COUNT(*) FROM return_requests;' \
    | dc exec -T db sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
```

So sánh số lượng và các ID/trạng thái đã có với nguồn; kiểm tra Flyway v3 và Hibernate validate, rồi E2E. Chỉ đổi DNS/cắt traffic sau xác minh và duyệt. Không copy thư mục data MySQL đang chạy từ Windows sang Ubuntu, không downgrade server trên volume đã khởi tạo, không xóa volume nguồn. Không thể cam kết không mất dữ liệu cho đến khi thử restore và cutover hoàn tất.

## Update và rollback

Trước update, ghi release đang chạy và digest, backup, giữ image và cấu hình cũ. Review compatibility của schema với code cũ; không sửa V1–V3. Build tag release mới, rồi `dc up -d --no-build`; kiểm tra health, logs và smoke tests trước khi mở traffic.

Rollback đơn giản khi schema còn tương thích: đặt `RELEASE_ID` về tag release trước trong `.env`, dùng bản Compose/config của release trước và cùng project name/volume; chạy:

```bash
dc config --quiet
dc up -d --no-build backend frontend
dc ps
```

Không rebuild tag cũ từ source mới. Database không rollback tự động bằng Flyway Community. Nếu migration mới không tương thích với code cũ, dừng ghi và lập kế hoạch recovery sang database/volume mới từ backup; việc restore về snapshot cũ có thể mất ghi mới nên cần chủ dự án quyết định. Không tự phục hồi đè production hoặc xóa volume.

## Thông tin còn thiếu

VPS/provider/IP/CPU/RAM/disk và Ubuntu; SSH access; domain và quyền DNS; chính sách firewall; nguồn release đã commit và quyền chuyển source/images; quyết định dữ liệu nào được chuyển; secrets production; lịch downtime/cutover; RPO/RTO, nơi backup và người vận hành; kết quả browser E2E/restore drill; quyết định cảnh báo dependency/Flyway; phê duyệt deploy public. CI/CD chưa được tạo trong phạm vi này.
