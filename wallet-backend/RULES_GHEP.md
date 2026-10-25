# Ghép Firestore Rules cho backend 1.3.1

firestore.wallet.rules là bộ tham chiếu đã chạy 27 kiểm tra emulator, không phải Rules được tải từ Firebase Console. Chưa triển khai Rules hoặc sửa Firebase thật trong phiên dựng lại mã.

Trước khi nghiệm thu quyền riêng tư, lấy và sao lưu Rules hiện tại của project đang dùng. Đối chiếu các collection riêng của nhóm rồi ghép các phần sau:

- Wallets/topups/ledger và các bảng operation/lock: client chỉ đọc dữ liệu của mình, tài chính chỉ backend ghi.
- exchanges và exchangeRequests: client chỉ tạo đề nghị chưa chốt cho các bài còn mở. Chốt/hủy/đổi bài/đổi hình thức/duyệt hủy gọi backend, không ghi trạng thái trực tiếp.
- posts: public query theo APPROVED; BOOKED chỉ hai bên, chủ bài và admin ACTIVE đọc được. bookingKey, bookingPartyIds, bookingState và các trường khóa chỉ backend quản lý. Client không tự bỏ khóa/xuất bản lại/xóa bài khóa.
- images và postImages: quyền đọc theo bài cha; không sửa/xóa ảnh bài đang khóa từ client.
- accounts: bảo vệ role, accountId, authUid, googleUid và các trường số dư. Account alias phải được liên kết tin cậy từ hồ sơ.
- notifications chung và thông báo nested cũ: bảo vệ thông báo ví, cho người nhận đánh dấu đã đọc; admin ACTIVE có quyền quản trị tương ứng.
- conversations/messages: thành viên bất biến; getAfter cho batch tin đầu tiên; chặn một trong hai chiều ngăn gửi mới nhưng vẫn đọc lịch sử/đánh dấu đã đọc.
- blocks: chỉ hai bên đọc, chỉ người chặn tạo/xóa, ID chuẩn theo hai UID.
- payments cũ: admin đọc, client không ghi tài chính.

Gỡ hoặc thu hẹp mọi allow đọc/ghi tổng quát đang trùng đường dẫn. Firestore xét nhiều allow theo OR; thêm một block hạn chế không ghi đè quyền rộng cũ.

Chạy kiểm tra giả lập (Node 22+, Java 25):

```powershell
Set-Location "D:\MobileProject-Rebuilt\wallet-backend\rules-tests"
npm.cmd ci
npm.cmd test
```

Lệnh dùng project demo-stayexchange-rebuild và emulator cục bộ, không triển khai Rules thật. Sau khi ghép Rules thật, dùng tài khoản thử A/B/C để đối chiếu: B chốt với A, C không đọc/đặt được; A/B/admin vẫn xem. Kiểm tra cả tiền và hai chiều, cả hai bài của trao đổi hai chiều, hủy/từ chối, bài hoàn tất và bài bị moderator ẩn.
