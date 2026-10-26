# Thanh menu chung cho 5 tab

Bản sửa dựa trên mã nguồn nhóm gửi, commit `8fc47a9c9a0ce7d70e8801a09379c36d0ddfcdd1`.

## Thay đổi

- `MainActivity` chứa `MainNavigationFragment`. Fragment này sở hữu một `BottomNavigationView` và một vùng `FragmentContainerView` để hiển thị nội dung.
- Khám phá, Yêu cầu, Lịch, Tin nhắn và Tài khoản lần lượt dùng `ExploreFragment`, `RequestsFragment`, `ScheduleFragment`, `MessagesFragment`, `AccountFragment`.
- Chọn tab dùng `add/show/hide` với tag ổn định. Tab được tạo khi mở lần đầu; các lần quay lại dùng lại Fragment và view đang có. Chạm lại tab hiện tại không khởi động Activity khác.
- Tab hiện tại ở trạng thái `RESUMED`; tab đã mở nhưng đang ẩn ở `STARTED`. Listener của nội dung dừng ở `onPause` và chạy lại ở `onResume`. Thanh menu nằm ngoài vùng nội dung nên không bị tạo lại khi chuyển tab.
- Khám phá và Tài khoản có ViewModel riêng để không dùng chung trang con, bản nháp và vị trí cuộn. Hai tab tiếp tục sử dụng giao diện Home/Account của nhóm.
- Lưu/khôi phục tab chính, bộ lọc yêu cầu, bộ lọc/tìm kiếm tin nhắn và mục lịch khi Activity được tạo lại. FragmentManager lưu trạng thái view của từng tab.
- Back từ tab khác trở về Khám phá; Back trong trang con Tài khoản trở về trang cha; Back khi drawer mở sẽ đóng drawer trước.
- Xóa các menu riêng khỏi nội dung tab, gồm cả menu bốn nút cũ ở bản xoay ngang của Yêu cầu. Insets hệ thống/bàn phím được áp dụng một lần ở màn chính.
- `Request`, `Chat`, `ExchangeScheduleActivity` còn là điểm chuyển tiếp cho Intent cũ, không còn chứa giao diện tab riêng. Giữ hỗ trợ Intent mở trực tiếp mục Đã nhận.
- Khi UID đăng nhập thay đổi, hủy các Fragment đang giữ dữ liệu tài khoản cũ. Các màn chi tiết vẫn mở từ tab tương ứng; điều hướng từ Ví quay về đúng tab trong màn chính.

Backend, số dư, cách thanh toán, Rules và URL Render không nằm trong phần refactor này. Gói cập nhật không tự commit, push hoặc deploy.

## Tự kiểm tra trong app

1. Chuyển liên tục qua cả 5 tab; thanh menu giữ nguyên vị trí và chỉ có một mục được chọn.
2. Trong Yêu cầu chọn Đã nhận và một bộ lọc, qua tab khác rồi quay lại. Mở chi tiết và quay về danh sách.
3. Trong Tin nhắn chọn bộ lọc, nhập tìm kiếm, qua Lịch rồi quay lại. Mở cuộc trò chuyện rồi Back.
4. Trong Lịch chọn Đang diễn ra/Hoàn tất, qua tab khác rồi quay lại. Kiểm tra mở chi tiết bài đăng.
5. Mở Tài khoản, chỉnh hồ sơ, chọn avatar rồi quay về. Mở Ví và dùng menu để trở lại một trong 5 tab.
6. Xoay dọc/ngang khi đang ở một tab. Kiểm tra tab được chọn và bộ lọc/tìm kiếm được giữ.
7. Thử Back từ các tab và từ trang con; thử đăng xuất/đăng nhập lại để bảo đảm không còn dữ liệu màn hình của tài khoản trước.

## Tài liệu kỹ thuật

- [Android Fragment transactions](https://developer.android.com/guide/fragments/transactions): thay nội dung trong container, cho phép sắp xếp lại transaction và giới hạn lifecycle.
- [Android Fragment lifecycle](https://developer.android.com/guide/fragments/lifecycle): vòng đời Fragment/view và phạm vi quan sát dữ liệu.
