# Thông báo sau đăng nhập và menu chung

## Thông báo

`MobileProjectApp` chỉ đăng ký bộ theo dõi vòng đời. Việc Firebase khôi phục UID đã lưu không tự bật truy vấn notifications. Một phiên được nhận thông báo khi có UID hiện tại, hồ sơ ACTIVE/vai trò USER hoặc ADMIN đã được xác minh, và đang ở màn hình sau đăng nhập.

Màn Login/Register luôn dừng listener, xóa banner/hàng đợi và xóa xác nhận phiên. Đăng xuất hoặc đổi UID cũng làm listener cũ mất hiệu lực. Callback Firestore cũ phải qua kiểm tra thế hệ, UID phiên và UID Firebase; chỉ tài liệu có đúng userId được chuyển tới banner. Trở lại app sau khi Android khôi phục một màn đã đăng nhập sẽ xác minh hồ sơ từ server trước khi bật lại.

Giữ hành vi của PR #41: mỗi thông báo chưa đọc được nhắc một lần trên mỗi bản cài/tài khoản; bấm thông báo trong chuông hoặc banner dùng cùng bộ định tuyến post/yêu cầu/chat/ví. Chỉ mở thành công mới đánh dấu đọc. Thông báo cũ thiếu mã đích mở nội dung để tránh dẫn nhầm bài. Việc xóa dữ liệu app có thể làm thông báo còn chưa đọc được nhắc lại.

## Menu

Giữ MainNavigationFragment và một BottomNavigationView cho năm tab. MainNavigationInsets là nơi duy nhất xử lý system bars/display cutout/IME cho màn chính và Ví. Nền menu kéo xuống hết cạnh dưới, phần nút dùng chiều cao 64dp và khoảng an toàn dưới được cộng đúng một lần. Tab con không tự cộng lại inset. Mở bàn phím sẽ ẩn menu; đóng bàn phím đưa menu về vị trí cũ. Chuyển tab không tạo Activity hoặc menu mới.

## Kiểm tra trên máy

1. Mở app ở Login khi Firebase còn lưu tài khoản lần trước: không hiện banner thông báo, kể cả chờ 30 giây.
2. Đăng nhập A: chỉ A thấy thông báo của A. Chuyển đủ năm tab rồi mở lại app: thông báo đã nhắc không lặp.
3. Đăng xuất, ở Login: banner/hàng đợi biến mất. Đăng nhập B: không hiển thị hàng đợi của A.
4. Thử mật khẩu sai hoặc tài khoản bị khóa: không bật thông báo.
5. Bấm thông báo có đủ mã đích về bài, yêu cầu, chat và ví; kiểm tra tới đúng nội dung và trạng thái đọc.
6. Kiểm tra menu ở Khám phá/Yêu cầu/Lịch/Tin nhắn/Tài khoản và Ví. Không có khoảng trắng thừa dưới nhãn; icon/nhãn nằm phía trên nút điều hướng hệ thống.
7. Thử điều hướng ba nút và cử chỉ, xoay ngang, mở/đóng bàn phím tìm kiếm. Khi quay lại tab, nội dung/scroll đã mở vẫn được giữ.

Tài liệu nền tảng: [Firebase Auth user state](https://firebase.google.com/docs/auth/android/manage-users), [Android edge-to-edge và insets](https://developer.android.com/develop/ui/views/layout/edge-to-edge).
