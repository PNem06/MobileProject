# Ghép Rules vào Firebase project đang dùng

ZIP gốc không chứa Firestore Rules đang chạy trên Firebase Console, vì vậy chưa thể xác nhận rules của nhóm tương thích hoàn toàn với file mẫu. `firestore.wallet.rules` là bộ rules tham chiếu theo các collection tìm thấy trong source hiện tại; sao lưu rules hiện tại trước khi thử trên project demo.

Để ghép vào rules đang có, cần:

1. Thêm các block `wallets`, `walletTopups`, `walletLedger`, `walletBankEvents`, `walletOperations`, `walletTopupLocks`, `walletBookingLocks`.
2. Thay block `exchanges` bằng logic bảo vệ `walletManaged` trong file mẫu. Không để block cũ cấp quyền ghi cho dữ liệu tài chính.
3. Trong `accounts`, chặn người dùng tự đổi `role`, `accountId`, `authUid`, hoặc đổi `googleUid` sang UID người khác. Không dùng trường balance do client tự ghi.
4. Trong `notifications`, thông báo `walletManaged: true` chỉ backend tạo; người nhận chỉ được đánh dấu đã đọc.
5. Gỡ hoặc thu hẹp mọi match tổng quát đang cho phép đọc/ghi rộng. Nhiều allow trùng đường dẫn được xét theo OR, không phải deny ghi đè allow.
6. Quyền đọc `exchanges` phải hỗ trợ cả UID và accountId của source. Query yêu cầu nhận đã được bổ sung lọc host_accountId trước khi đọc.

Không triển khai đồng thời rules mẫu rồi ghi đè lại bằng allow-all để sửa lỗi. Gửi rules thật nếu cần ghép chính xác với những quyền bổ sung của nhóm. Mã backend dùng Admin SDK nên không phụ thuộc quyền ghi client; quyền client vẫn bắt buộc để chống tự tăng số dư/đổi trạng thái bằng app đã sửa.

Các kiểm tra trên Rules Playground/Emulator trước giao dịch thật:

- Không đăng nhập: đọc/ghi wallets bị từ chối.
- A đọc được wallets/A, không đọc được wallets/B.
- A không tạo/sửa số dư wallets/A hoặc walletLedger.
- A không tạo exchanges với walletManaged=true hoặc paymentStatus=PAID.
- A/B không cập nhật trực tiếp status của exchanges đã walletManaged=true.
- A không tự đổi role=ADMIN, accountId=B hoặc googleUid=B.
- A vẫn đọc được yêu cầu do A gửi; B đọc được yêu cầu do B nhận.
- Query của màn ReceivedRequestsDetailActivity lọc postId và host_accountId phải chạy được.
- Đăng nhập, đăng bài, ảnh, yêu thích, trao đổi phòng và nhắn tin của nhóm vẫn hoạt động theo quyền thực tế.

Các ca backend tự động nằm trong `test/`; các ca Rules trên cần chạy trên Firestore Emulator/Console với rules thật, chưa được coi là đã nghiệm thu chỉ vì test Node.js đạt.
