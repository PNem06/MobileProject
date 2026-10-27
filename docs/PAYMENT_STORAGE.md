# Lưu trữ thanh toán thống nhất

Bản backend 1.4.0 hỗ trợ hai cấu trúc. Khi chưa chuyển đổi, backend tiếp tục đọc/ghi cấu trúc cũ. Chỉ công cụ `payment-migration.js --apply` mới kích hoạt cấu trúc 2, sau khi sao lưu và xác nhận dữ liệu đích khớp nguồn. Các lệnh thao tác với Firestore dùng Firebase Admin trên máy quản trị, không chạy từ Android.

## Mỗi mục lưu gì

Sau khi chuyển và dọn xong, dữ liệu ví dùng hai collection gốc `payments` và `wallets`.

| Trước | Sau | Nội dung |
|---|---|---|
| `walletTopups/NAP...` | `payments/NAP...`, `recordType=TOPUP` | Yêu cầu nạp: chủ ví, số tiền, QR, nội dung chuyển khoản, hạn, trạng thái nạp, liên kết sự kiện ngân hàng. Giữ nguyên mã NAP và QR đã phát hành. |
| Thông tin tài chính của `exchanges/WX...` | `payments/rental_WX...`, `recordType=RENTAL` | Bản ghi thanh toán thuê phòng, khoản giữ, hoàn tiền, phí hủy, tất toán. Được cập nhật cùng transaction với ví và exchanges. |
| `payments/<mã cũ>` | Giữ nguyên | Các bản thanh toán cũ của nhóm vẫn đọc được; không ghi đè hoặc xóa. |
| `walletLedger/<id>` | `payments/_system/ledger/<id>` | Sổ biến động có dấu, theo ví và ngăn available/held; mã sự kiện/nạp/trao đổi để đối chiếu. |
| `walletBankEvents/<id>` | `payments/_system/bankEvents/<id>` | Sự kiện SePay đã chuẩn hóa, kết quả xử lý; chống ghi nhận một sự kiện hai lần. |
| `walletOperations/<id>` | `payments/_system/operations/<id>` | Ánh xạ operationId tới mã nạp/trao đổi để khôi phục khi người dùng bấm lại hoặc mạng ngắt. |
| `walletTopupLocks/<uid>` | `payments/_system/topupLocks/<uid>` | Con trỏ tới yêu cầu nạp đang chờ của mỗi người. |
| `walletBookingLocks/<id>` | `payments/_system/bookingLocks/<id>` | Chống lặp đặt phòng theo người, bài và khoảng ngày. Khóa một bài cho một trao đổi vẫn ở `posts.bookingKey`. |
| `wallets/<uid>` | Giữ nguyên | Số dư available, held và currency; chỉ backend được ghi. |

`payments/_system` là tài liệu điều khiển phiên bản lưu trữ và chuyển đổi, không phải một khoản thanh toán. Các subcollection chứa khóa/sự kiện không được client đọc. Sổ biến động chỉ cho chủ ví đọc các dòng của mình; bản TOPUP chỉ chủ ví/admin đọc; bản RENTAL chỉ hai bên/admin đọc. Android không có quyền ghi tiền, khóa, sự kiện hoặc marker phiên bản.

`exchanges` tiếp tục lưu nghiệp vụ yêu cầu/trao đổi, hai bên, bài, ngày, trạng thái và các trường tài chính tương thích app cũ. Các trường đó và bản RENTAL được ghi trong cùng một Firestore transaction. Không gộp bài đăng, yêu cầu và chat vào payments.

## Triển khai theo thứ tự

1. Kiểm tra Android ở nhánh sửa; thống nhất với nhóm rồi mới commit/push và mở PR. Việc gộp dữ liệu không tự chạy khi cài app hoặc khởi động backend.
2. Triển khai backend 1.4.0 trên Render. Giữ repository, biến môi trường và Secret File hiện có. Build `npm ci --omit=dev`, Start `node server.js`, Root Directory `wallet-backend`.
3. Mở `https://stayexchange-wallet-tea.onrender.com/health`. Cần có `backendVersion: "1.4.0"`, `storageAdapterVersion: 1`, `projectId: "mobileprojectdatabase-89d11"`. `storageSchema` ban đầu là 1.
4. Ghép khối `payments` trong Rules mới vào Rules đang chạy. Nếu Rules thật đã có chỉnh sửa khác của nhóm, giữ nguyên các khối khác. Bản đầy đủ tham chiếu là `wallet-backend/firestore.wallet.rules`; khối thêm/thay riêng có ở `PAYMENT_RULES_PATCH.txt` trong gói. Publish trước khi kích hoạt cấu trúc 2.
5. Dừng các backend cũ trên laptop hoặc máy thành viên khác. Mọi tiến trình có thể ghi ví phải dùng bản 1.4.0 trở lên; chỉ các bản này mới tôn trọng khóa chuyển đổi. Giữ cấu hình tự gửi lại webhook khi server lỗi trên SePay. Trong lúc chuyển, backend trả 503 cho thao tác ví và tự nhận lại sau khi xong.
6. Dùng các lệnh dưới đây ở máy đang có khóa Firebase hợp lệ. Khóa và tệp sao lưu chỉ để trong `wallet-backend/private`, không đưa lên GitHub.

### Xem trước, không ghi dữ liệu

```powershell
Set-Location "D:\MobileProject-Fixed\wallet-backend"
npm.cmd ci --omit=dev
node --env-file=.env payment-migration.js --project mobileprojectdatabase-89d11
```

Kết quả `mode: PREVIEW` cho biết số tài liệu từng loại, tổng available/held và số bản cần sao chép. Chưa có khoản tiền nào được cộng, trừ, hoàn hay tất toán bởi công cụ này.

### Sao lưu, sao chép rồi kích hoạt

```powershell
node --env-file=.env payment-migration.js --project mobileprojectdatabase-89d11 --apply --backend-url https://stayexchange-wallet-tea.onrender.com --backup private/payments-before-v2-20261027.json
```

Lệnh tạo tệp sao lưu mới, không ghi đè tệp đã có. Sao lưu chứa nguồn, ví, thanh toán cũ, mã kiểm tra và danh sách ánh xạ. Công cụ chặn các thao tác ví qua marker MIGRATING, sao chép theo transaction, kiểm lại nguồn/số dư/đích rồi mới đổi marker sang cấu trúc 2. Số dư giữ nguyên. Nếu gặp lỗi trước khi kích hoạt, writer tiếp tục cấu trúc 1; bản sao chưa hoàn tất chưa được sử dụng. Đích có dữ liệu lạ sẽ làm công cụ dừng.

Chờ `mode: APPLIED`, `balancesChanged: false`. Mở `/health` xác nhận `storageSchema: 2`, `storageState: "READY"`. Mở app kiểm tra số dư, lịch sử, yêu cầu đang có và QR đang chờ. Các collection cũ vẫn còn ở bước này để đối chiếu.

### Dọn các collection cũ sau khi đối chiếu

```powershell
node --env-file=.env payment-migration.js --project mobileprojectdatabase-89d11 --prune-legacy --backend-url https://stayexchange-wallet-tea.onrender.com --backup private/payments-before-v2-20261027.json
```

Chỉ chạy sau khi bước kiểm tra trên thành công. Công cụ đòi đúng tệp sao lưu của lượt kích hoạt, kiểm tra nguồn cũ không bị ghi thêm và bản đích còn hợp lệ. Nó chỉ xóa các tài liệu thuộc sáu collection wallet cũ đã sao lưu. Không xóa wallets, exchanges, posts, notifications hoặc các payments của nhóm. `legacyRemaining: 0` cho biết đã dọn hết các tài liệu cũ đó. Giữ tệp sao lưu sau khi dọn.

Nếu terminal bị đóng cưỡng bức ở chế độ MIGRATING, bảo đảm tiến trình chuyển đổi cũ đã dừng rồi dùng:

```powershell
node --env-file=.env payment-migration.js --project mobileprojectdatabase-89d11 --cancel-migration --backend-url https://stayexchange-wallet-tea.onrender.com
```

Lệnh này chỉ mở lại writer cấu trúc 1 khi cấu trúc 2 chưa kích hoạt. Sau đó có thể chạy apply lại với tên sao lưu mới; bản sao dở được kiểm tra và làm mới từ nguồn. Không đặt marker về cấu trúc 1 hoặc triển khai backend 1.3.1 sau khi đã kích hoạt cấu trúc 2: bản cũ không biết đường dẫn mới. Nếu cần hoàn tác giao diện/thông báo, làm riêng các commit Android, giữ bộ đọc/ghi thanh toán 1.4.0.

## Kiểm tra tự động

- `npm test`: bộ kiểm tra nghiệp vụ, chống lặp, bảo toàn số tiền, chuyển dữ liệu, sao lưu, xung đột và dọn dữ liệu.
- `npm --prefix rules-tests test`: Rules trên Firestore Emulator, project demo.
- `npm run test:payments-emulator`: chuyển thật giữa hai cấu trúc trên Firestore Emulator, gửi webhook đồng thời và tất toán. Chỉ chấp nhận emulator localhost và project `demo-stayexchange-rebuild`.

Các kiểm tra không thao tác dữ liệu Firebase thật. Chạy Rules/emulator cần Java 25 và các dependency ở `rules-tests` (`npm --prefix rules-tests ci`).

Tài liệu nền tảng: [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions), [Rules và queries](https://firebase.google.com/docs/firestore/security/rules-query). Tất cả lượt đọc của một transaction được hoàn tất trước lượt ghi đầu tiên.

## Rules đang dùng của nhóm — đối chiếu ngày 27/10/2026

`wallet-backend/firestore.wallet.rules` được đồng bộ với bản Rules người dùng
đã gửi từ Firebase Console và bổ sung riêng các khối `payments`. Tất cả nội dung
ngoài khối thanh toán, bao gồm `posts`, `postImages`, `images` và các hàm quyền
xem bài/ảnh, giữ nguyên từng ký tự so với bản Console đó. Không dùng lại toàn bộ
bản Rules tham chiếu cũ khi triển khai.

Theo yêu cầu giữ nguyên phần bài/ảnh, chính sách hiện tại vẫn cho mọi người đọc
bài `BOOKED` chưa hoàn tất và ảnh liên kết; khi vẫn `BOOKED` và có
`bookingState` hoặc `exchangeStatus` là `COMPLETE`/`COMPLETED`, Rules chặn cả
hai bên và admin khi đọc trực tiếp tài liệu đó. Đây là hành vi đang
có được ghi nhận trong kiểm tra, không phải quyền mới được thêm cho thanh toán.
Quyền A/B/admin chỉ đọc bài đã chốt cần được nhóm thống nhất riêng nếu muốn đổi.

Reviews giữ đúng payload của `view/Review.java`:
`reviewer_accountId`, `postId`, `rating`, `comment`, `createdAt`, `reviewId`.
Rules hiện tại không cho client sửa/xóa review. Kiểm tra Rules dùng chính bản
Console đã ghép thanh toán này; các kiểm tra đồng thời vẫn kiểm tra một đặt
phòng thành công và đúng một lần trừ tiền.

`mode: PREVIEW` hiển thị `storageSchema: 2` là cấu trúc đích của kế hoạch.
Chỉ `mode: APPLIED` cùng health schema 2 READY mới xác nhận đã kích hoạt.
Commit/push và merge PR không tự chạy công cụ chuyển hoặc dọn dữ liệu.
