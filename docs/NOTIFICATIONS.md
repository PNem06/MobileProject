# Thông báo một lần và mở đúng chi tiết

## Hành vi

- Bỏ Toast thông báo trong `HomeViewModel`. Danh sách và dấu chưa đọc vẫn được đồng bộ tại đây, còn nhắc thông báo do một listener toàn ứng dụng quản lý.
- Mỗi cặp Firebase UID + ID document thông báo chỉ được nhắc một lần trên bản cài ứng dụng. Dấu đã nhắc được lưu trên máy, độc lập với `isRead`; chuyển tab, xoay máy, nối lại listener, khởi động lại app hoặc đăng nhập lại cùng tài khoản không nhắc lại document đã hiện.
- Thông báo đã đọc không được đưa lên banner. Tại một thời điểm chỉ có một banner; các thông báo chưa được nhắc đợi tới lượt khi có Activity ở foreground. Hiện banner không tự đánh dấu đã đọc.
- Listener xử lý cả lần nhận từ cache và lần xác nhận server: chỉ thêm banner từ dữ liệu được xác nhận, đồng thời loại mục đã đọc khỏi hàng đợi. Dùng ID lưu bền để không phụ thuộc vào việc Firestore báo ADDED khi nối lại query.
- Chuông và banner dùng cùng `NotificationNavigator`. Bài đăng mở `Detail`; chat mở đúng đối tác; thuê có ví mở chi tiết giao dịch ví; yêu cầu khác mở đúng chi tiết đã gửi hoặc tự mở đúng yêu cầu con trong mục đã nhận, hỗ trợ cả `exchanges` và dữ liệu `exchangeRequests` cũ.
- Bấm mở thông báo cập nhật `isRead` và `readAt`, phù hợp Rules đang có. Trường `read` cũ vẫn được đọc để tương thích; cập nhật mới ghi `isRead`.
- Thông báo duyệt/từ chối/ẩn bài mới được kèm `postId`. Không dùng `requestId` làm mã bài viết.
- Callback cũ sau khi đổi tài khoản bị bỏ qua; banner và hàng đợi của tài khoản trước được dọn khi chuyển UID.

## Giới hạn dữ liệu cũ

Thông báo cũ chỉ có tiêu đề/nội dung mà thiếu mã bài, yêu cầu hoặc cuộc trò chuyện không thể xác định duy nhất đích. App mở nội dung thông báo trong trường hợp đó, thay vì đoán một bài khác theo tên. Thông báo mới từ các luồng đã sửa có mã đích. Nếu nội dung đã bị xóa, mất quyền đọc hoặc mạng lỗi, app báo rõ và không thay đổi trạng thái giao dịch.

Dấu đã nhắc lưu theo người dùng trên bản cài ứng dụng; xóa dữ liệu ứng dụng có thể làm thông báo chưa đọc được nhắc lại. Thiết bị khác có dấu nhắc riêng, nhưng trạng thái đã đọc được đồng bộ qua Firestore.

## Kiểm tra trên app

1. Tạo một thông báo chưa đọc mới, thấy nhắc một lần. Chuyển cả 5 tab, mở Ví, trở lại, tắt/mở app và thử nối lại mạng: cùng document không nhắc lại.
2. Banner hết thời gian nhưng chưa bấm đọc: thông báo vẫn còn trong chuông với dấu chưa đọc. Mở nó từ chuông để vào chi tiết và cập nhật dấu đọc.
3. Admin duyệt/từ chối một bài: chủ bài bấm thông báo mới để mở đúng bài. Kiểm tra cả thông báo ẩn bài mới.
4. Gửi yêu cầu trao đổi: người nhận bấm thông báo để mở đúng yêu cầu con; kiểm tra các nút hiện có. Yêu cầu có ví vào đúng chi tiết ví. Thông báo cho bên gửi vào chi tiết đã gửi.
5. Gửi tin nhắn từ tài khoản khác: bấm thông báo trong chuông hoặc banner để vào đúng cuộc trò chuyện.
6. Hai thông báo đến cùng lúc chỉ có một banner được gắn trên màn hình. Nếu một mục được đọc từ thiết bị khác khi đang chờ, nó được loại khỏi hàng đợi.
7. Đăng xuất/đổi tài khoản khi đang có banner để xác nhận không còn hiển thị nội dung của tài khoản trước.

## Tài liệu đối chiếu

- [Firestore realtime listeners](https://firebase.google.com/docs/firestore/query-data/listen): snapshot ban đầu có thể đến từ cache trước khi nhận server; listener mới có thể báo các document đang có là ADDED.
- [Android SharedPreferences](https://developer.android.com/training/data-storage/shared-preferences): lưu các cờ nhỏ phục vụ nhận biết đã nhắc qua những lần mở ứng dụng.

Không cần đổi schema thanh toán hoặc triển khai Rules mới cho phần này. Backend và mã xử lý tiền không được sửa.
