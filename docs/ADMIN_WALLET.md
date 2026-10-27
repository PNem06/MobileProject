# Ví quản trị dùng chung

## Cách hoạt động từ backend 1.4.1

- Một ví hệ thống duy nhất: `wallets/{ADMIN_UID}`. Giữ nguyên giá trị `ADMIN_UID` đang dùng trên Render.
- Backend xác thực Firebase ID token và đọc hồ sơ `accounts` ở từng yêu cầu. Tài khoản có `role=ADMIN`, trạng thái `ACTIVE` được xem ví này.
- `userId` của snapshot vẫn là UID người đang đăng nhập; `walletOwnerId` là UID chủ ví hệ thống. Hai giá trị có thể khác nhau.
- Admin có cùng số dư khả dụng, tiền giữ, sổ giao dịch, yêu cầu thuê và thông báo của ví hệ thống. Tên người đang đăng nhập vẫn là tên riêng của từng Admin.
- Người dùng thông thường tiếp tục đọc ví cá nhân. Gửi `role`, `isAdmin` hoặc `walletOwnerId` giả trong JSON không cấp thêm quyền.
- Ví quản trị phục vụ theo dõi và đối soát; không dùng các API nạp/thuê cá nhân để chi tiền của ví hệ thống. Các quyết định giao dịch vẫn kiểm tra đúng người thuê/chủ nhà.

Số dư hiển thị là `available + held` của ví nội bộ hệ thống, không phải truy vấn số dư trực tiếp của tài khoản ngân hàng. Tiền còn trong ví từng người dùng không được cộng thêm vào thẻ số dư này.

Không sao chép, cộng dồn hoặc xóa số dư đã tồn tại ở các UID Admin khác. Các bản ghi đó được giữ nguyên để đối soát nếu từng phát sinh giao dịch riêng trước đây.

## Sửa đường mở ví

`AccountUi` được dùng bởi cả `MainActivity` lẫn `DashboardAdmin`. Trước đây nút Ví gọi trường `activity` chỉ có giá trị khi màn chủ là `MainActivity`, nên từ DashboardAdmin có thể phát sinh NullPointerException.

Nút Ví hiện gọi `hostActivity`, mở cùng `WalletActivity`. Mục tài khoản Admin có nhãn “Ví quản trị”; backend quyết định nội dung ví sau xác thực, không lấy quyền từ nhãn hoặc Intent của Android.

Android bỏ qua cache cũ chưa có hợp đồng ví chung; kiểm tra UID người xem và chủ ví trước khi hiển thị. Admin nhận tín hiệu thay đổi từ các trao đổi do ví quản lý và tải số dư qua backend; không đọc thẳng ví của UID khác bằng Firestore SDK. Khi không có tín hiệu, màn hình vẫn làm mới khi mở và thăm dò mỗi 30 giây trong lúc đang hiển thị.

## Thứ tự triển khai

1. Review các tệp trong bản vá rồi đưa lên GitHub bằng nhánh riêng. Không cần commit/push tự động khi cài bản vá.
2. Triển khai thư mục `wallet-backend` mới lên Render. Giữ các biến môi trường, Secret File và `ADMIN_UID` hiện có.
3. Kiểm tra `/health` có `backendVersion: "1.4.1"` và `sharedAdminWalletVersion: 1`.
4. Sau đó build/chạy Android đã cập nhật. App mới sẽ báo cần backend 1.4.1 nếu dịch vụ chưa được cập nhật, thay vì âm thầm hiển thị ví riêng của Admin khác.

Không cần chạy migration, `--apply`, `--prune-legacy`, hoặc đổi Rules để bật ví chung. Bộ lưu trữ thanh toán phiên bản 1/2 được giữ như đang dùng. Bản vá không sửa Rules, posts, postImages, images hoặc các hàm khóa bài.

Không đổi `ADMIN_UID` sang UID của từng người đăng nhập: làm vậy sẽ chỉ sang ví khác và số dư có thể trông như bị mất. Các Admin mới dùng hồ sơ ADMIN/ACTIVE do người có quyền quản trị cấp; không mở chức năng tự nâng quyền trong app.

## Đối chiếu trên máy nhóm

1. Admin A: từ Dashboard → Tài khoản → Ví quản trị. Xác nhận không văng và ghi lại số khả dụng, đang giữ, tổng, các mã yêu cầu.
2. Admin B: mở cùng đường dẫn. Bấm Làm mới trạng thái, đối chiếu các số và mã với A. Tên người đăng nhập có thể khác.
3. Từ màn bài/yêu cầu mở ví: vẫn dùng cùng nguồn dữ liệu hệ thống khi là Admin.
4. Tài khoản USER: chỉ thấy ví riêng và giao dịch liên quan; không thấy sổ hệ thống hoặc sự kiện ngân hàng của các khách khác.
5. Khóa một Admin bằng công cụ quản trị đang dùng: yêu cầu tải mới của tài khoản đó phải bị từ chối. Không dùng cache làm bằng chứng đã đồng bộ.
6. Sau một giao dịch thử hợp lệ, làm mới cả hai Admin để so cùng số dư. Chỉ dùng giao dịch đã được nhóm đồng ý thử.

Kiểm tra tại thư mục dự án trên máy đã cài Android SDK/JDK:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
npm.cmd --prefix .\wallet-backend test
```

## Kết quả xác minh bản vá

- 130/130 kiểm tra backend đạt, gồm 10 kiểm tra mới về ví chung, hai cấu trúc dữ liệu, phân quyền và HTTP.
- Tệp XML tài nguyên đã kiểm tra cú pháp.
- Có thêm 8 ca Robolectric về nút mở Ví của màn không phải MainActivity và hợp đồng snapshot/cache.
- Chưa chạy build hoặc các ca Android trong môi trường tạo bản vá vì không có Android SDK/Gradle/JDK đầy đủ; cần chạy hai lệnh trên và thử màn hình trên máy nhóm. Không ghi nhận đây là kết quả Android đã đạt.
- Không dùng khóa Firebase thật, không cập nhật dữ liệu thật, không deploy Render và không commit/push từ môi trường tạo bản vá.
