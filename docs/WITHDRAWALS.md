# Rút tiền về ngân hàng — backend 1.5.0

## Quy trình sử dụng

1. Người dùng mở **Ví → Rút tiền về ngân hàng**, nhập số tiền, ngân hàng, số tài khoản và tên người nhận, kiểm tra lại rồi gửi yêu cầu.
2. Backend giữ khoản cần rút trên chính ví người dùng: giảm `available`, tăng `held` cùng một số tiền. Tổng số dư nội bộ của người đó chưa giảm ở bước này. Chỉ dùng số dư khả dụng; tiền đã giữ cho đặt chỗ không được sử dụng lại.
3. Các Admin `ACTIVE` cùng thấy danh sách trong **Ví quản trị → Yêu cầu rút tiền**. Một Admin bấm **Nhận xử lý** và phải nhận được kết quả thành công trước khi thực hiện chuyển khoản thủ công.
4. Sau khi ngân hàng báo chuyển khoản thành công, Admin đó bấm **Đã chuyển khoản** và xác nhận. **Không có ô nhập mã giao dịch**. Backend chốt trừ khoản giữ và ghi trạng thái đã chuyển khoản.
5. Nếu chưa chuyển tiền, Admin có thể từ chối và ghi lý do. Backend trả toàn bộ khoản giữ về số dư khả dụng của người dùng.

Mỗi người dùng có tối đa một yêu cầu `PENDING` hoặc `PROCESSING`. Số tiền từ 1.000đ đến 50.000.000đ/lần; không vượt số dư khả dụng. Tên ngân hàng nhận được nhập theo ngân hàng thực tế của người dùng, không bị giới hạn ở ngân hàng nạp tiền của hệ thống.

## Trạng thái và quyền xử lý

| Trạng thái | Ý nghĩa | Quyền thao tác tiếp theo |
| --- | --- | --- |
| `PENDING` | Đã giữ tiền, chờ tiếp nhận | Admin đang hoạt động nhận xử lý hoặc từ chối khi chưa chuyển |
| `PROCESSING` | Một Admin đã nhận xử lý | Chỉ Admin đã nhận được bấm Đã chuyển khoản hoặc từ chối khi chưa chuyển |
| `PAID` | Admin xác nhận đã chuyển khoản thủ công | Kết thúc; gửi lại thao tác không trừ tiền lần hai |
| `REJECTED` | Bị từ chối, tiền giữ đã trả lại | Kết thúc; gửi lại thao tác không hoàn thêm tiền |

Không tự giải phóng yêu cầu đang được Admin xử lý theo thời gian, vì người đó có thể đã chuyển tiền ngân hàng nhưng chưa xác nhận trong app. Nếu cần đổi người xử lý và chắc chắn chưa chuyển, Admin đang nhận xử lý từ chối yêu cầu, rồi người dùng gửi lại. Nếu tài khoản xử lý bị khóa hoặc không còn truy cập, nhóm phải đối soát khoản chuyển trước khi can thiệp; không sửa số dư trực tiếp hoặc tự chuyển lại.

Nếu mất kết nối sau khi gửi yêu cầu hoặc bấm Đã chuyển khoản: mở lại yêu cầu và làm mới. Backend dùng khóa thao tác, khóa theo người dùng và giao dịch Firestore để chặn ghi nhận trùng. **Không chuyển khoản ngân hàng lần thứ hai chỉ vì app chưa trả kết quả.** Việc chuyển tiền ngoài ngân hàng là thao tác thủ công; app ghi nhận xác nhận của Admin, không tự xác minh giao dịch chi ngân hàng.

## Số dư ví Admin

Tiền thật đã nằm trong tài khoản ngân hàng quản trị từ lúc người dùng nạp ví. Yêu cầu rút là khoản cần chi lại cho người dùng, không được cộng vào số dư khả dụng của ví Admin. Khoản chờ rút được giữ trong `wallets/{userUid}.held`; màn Admin hiển thị riêng tổng yêu cầu chờ chi. Ví hệ thống `wallets/{ADMIN_UID}` và tiền giữ của các đặt chỗ hiện tại được giữ nguyên.

Ví dụ người dùng có 100.000đ và rút 30.000đ:

| Thời điểm | Khả dụng | Đang giữ | Tổng nội bộ |
| --- | ---: | ---: | ---: |
| Trước yêu cầu | 100.000đ | 0đ | 100.000đ |
| Chờ xử lý | 70.000đ | 30.000đ | 100.000đ |
| Đã chuyển khoản | 70.000đ | 0đ | 70.000đ |
| Hoặc bị từ chối trước khi chuyển | 100.000đ | 0đ | 100.000đ |

## Lưu trữ và API

- `payments/withdrawal_{withdrawalId}`: hồ sơ rút tiền, `recordType: WITHDRAWAL`, người yêu cầu, ngân hàng nhận, số tiền, trạng thái và Admin xử lý. Không lưu khóa ngân hàng hay thông tin đăng nhập ngân hàng của người dùng.
- `payments/_system/withdrawalLocks/{userUid}`: khóa một yêu cầu đang xử lý của mỗi người dùng; chỉ backend ghi.
- Sổ biến động và khóa chống gửi lặp dùng bộ lưu trữ hiện tại. Với schema 2, chúng nằm ở `payments/_system/ledger` và `payments/_system/operations`.
- `POST /v1/withdrawals`: tạo yêu cầu; backend xác định User bằng Firebase ID token, bỏ qua UID/quyền/số dư do client giả trong JSON.
- `POST /v1/withdrawals/get`: chủ yêu cầu hoặc Admin hợp lệ xem chi tiết.
- `POST /v1/withdrawals/action`: `CLAIM`, `PAID`, `REJECT`; backend đọc hồ sơ để kiểm tra đúng vai trò và đúng Admin đã nhận xử lý.
- `POST /v1/snapshot`: thêm `withdrawalVersion: 1`, danh sách yêu cầu, tổng tiền và số yêu cầu chờ xử lý. Hiển thị tất cả yêu cầu đang mở cùng tối đa 100 hồ sơ đã kết thúc gần nhất; chi tiết cũ hơn vẫn đọc được qua API theo ID.

Thông báo rút tiền mở đúng chi tiết rút, không mở nhầm chi tiết thuê phòng. Các Admin dùng cùng hàng đợi qua backend và làm mới khi mở Ví, khi thao tác hoặc theo chu kỳ 30 giây trong lúc xem. User cũng nhận tín hiệu thay đổi số dư từ ví của mình.

Luồng mới đọc/ghi qua backend tin cậy. **Không cần thay Rules để bật chức năng**, không chạy lại migration và không khôi phục collection cũ. Bản bổ sung không sửa `posts`, `postImages`, `images`, `exchange-service.js`, cơ chế giữ bài hoặc Rules đang có.

## Triển khai và kiểm tra

Sau khi nhóm review và merge, Render triển khai `wallet-backend` từ `main`. Giữ nguyên biến môi trường, `ADMIN_UID`, Secret File, cấu hình ngân hàng và webhook SePay. `/health` cần có `backendVersion: "1.5.0"`, `withdrawalVersion: 1`; với dự án đã chuyển dữ liệu, `storageSchema: 2`, `storageState: "READY"`.

App cũ tiếp tục dùng các API đã có. App mới chỉ cho giao dịch rút khi backend hỗ trợ; không nhận kết quả từ bộ nhớ cục bộ làm xác nhận chi tiền.

Trong thư mục gốc dự án, chạy:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
npm.cmd --prefix .\wallet-backend test
```

Kiểm tra bổ sung trên Firestore Emulator, chỉ dùng project demo cục bộ, không dùng khóa Firebase thật:

```powershell
npm.cmd --prefix .\wallet-backend ci --omit=dev
npm.cmd --prefix .\wallet-backend\rules-tests ci
npm.cmd --prefix .\wallet-backend run test:withdrawals-emulator
```

Thử đủ trên app: gửi yêu cầu; đổi qua hai Admin để so cùng hàng đợi; hai Admin cùng nhận để xác nhận chỉ một người được xử lý; từ chối trước khi chuyển và kiểm tra số dư được trả lại; dùng một yêu cầu khác để kiểm tra Đã chuyển khoản; mở lại hoặc bấm lặp để kiểm tra không trừ/hoàn thêm. Với chuyển khoản thật, chỉ xác nhận sau khi đã chuyển thành công đúng người nhận.
