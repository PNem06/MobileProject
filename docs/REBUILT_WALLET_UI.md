# Bản dựng lại: giao diện Ví, Lịch và một trao đổi mỗi bài

Bản này dựng lại từ MobileProject.zip người dùng gửi, lịch sử Git gốc 7a9ebf5 và main của PNem06/MobileProject tại 592a935. Không phải khôi phục nguyên byte ZIP đã mất. Nhánh mới là integrate/wallet-ui-single-exchange-20261025; các commit của nhóm vẫn nằm trong lịch sử.

Ví và Lịch bỏ logo/chữ StayExchange trên thanh đầu. Cả hai dùng tiêu đề 48dp và kiểu menu dưới của màn Yêu cầu. Ví không cộng khoảng đệm hệ thống ở đáy lần thứ hai. Lịch dùng cùng layout ở hai chiều, danh sách chuyển thành hai cột khi xoay ngang.

Một bài chỉ thuộc một giao dịch đã chốt hoặc đang giữ tiền. Nhiều đề nghị PENDING có thể cùng tồn tại, nhưng chỉ một đề nghị được chốt. Thanh toán ví khóa bài trong cùng transaction trừ/giữ tiền. Chốt hai chiều khóa cả bài đích và bài đề xuất. Hai luồng cùng đọc và ghi khóa trên document posts nên hai thao tác đồng thời không thể thắng cùng một bài.

Bài khóa có status BOOKED, bookingKey, bookingPartyIds và trạng thái khóa. Danh sách công khai chỉ lấy APPROVED; hai bên có danh sách riêng theo UID. Tìm kiếm và bản đồ nghe cập nhật để gỡ bài vừa khóa. Chi tiết không còn nút đặt ở bài khóa, người ngoài bị đóng màn chi tiết nếu quyền đọc bị thu hồi. Admin vẫn đọc được, mục bài đã ẩn hiển thị nhãn đã có trao đổi và không xuất bản lại bài đang khóa.

Hủy/từ chối hợp lệ mở lại đúng bài trong cùng transaction; chính sách phí hủy và hoàn tiền ví giữ nguyên. Bài bị admin ẩn do vi phạm vẫn giữ ẩn. Giao dịch hoàn tất giữ bài đóng; muốn một lượt trao đổi mới thì tạo bài mới.

Các quyết định giao dịch không dùng ví và duyệt hủy cũ đi qua API backend để không bỏ qua khóa bài. Android không trực tiếp đổi trạng thái giao dịch theo Rules tham chiếu. Số tiền/trạng thái ví ở màn admin lấy từ exchanges, còn payments chỉ phục vụ dữ liệu cũ. Cache Room có migration 2 sang 3 cho walletManaged. Thông báo quản trị ghi vào collection chung, mở đúng thanh toán hoặc Chat.

Giữ giao diện Chat của nhóm; tạo conversation trước khi nghe messages, gửi message và summary trong batch, chỉ xóa nội dung nhập khi server xác nhận, kiểm tra chặn hai chiều. Ví có nút nhắn đúng UID chủ nhà/khách.

Backend 1.3.1 bổ sung khóa dùng chung và tác vụ đồng bộ khóa lịch sử khi khởi động. Tác vụ khóa không sửa số dư, tự hoàn tiền hay tự chọn người thắng nếu dữ liệu cũ đã trùng. Các bài có xung đột được giữ ẩn với CONFLICT và in mã trong log để admin đối soát. Bài hoàn tất cũ cũng giữ đóng. Luồng tất toán/hoàn tiền định kỳ của ví tiếp tục theo điều kiện giao dịch như trước.

Kiểm tra trên bản này: Android assembleDebug và 13 unit test thành công; 97 backend test; 27 Firestore Emulator test, gồm hai thử nghiệm cạnh tranh bằng Admin SDK trên emulator. Chưa chạy UI trên máy của người dùng, chưa triển khai Rules, hosting hoặc thử giao dịch tiền thật.

Rules trong wallet-backend/firestore.wallet.rules là tham chiếu theo mã nguồn, chưa phải Rules đang chạy trên Firebase Console. Cần ghép Rules thật trước khi nghiệm thu quyền đọc/chặn client cũ. Các allow rộng trùng đường dẫn sẽ làm mất hiệu lực giới hạn của bản tham chiếu. Tệp .env và private keys không nằm trong nhánh hoặc gói phát hành; script cài đặt chỉ lấy chúng từ dự án trên máy người dùng.
