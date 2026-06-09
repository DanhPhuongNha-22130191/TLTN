# Đặc Tả Các Use Case Hệ Thống Chat GitLab Handbook

---

## 1. USE CASE: ĐĂNG XUẤT

### Tên Use Case
- **Đăng xuất**

### Mô Tả (Description)
- Người dùng đã đăng nhập muốn thoát khỏi hệ thống. Đăng xuất sẽ xóa các token từ phía client và hủy session trên server (Keycloak).

### Actor
- **Người dùng đã xác thực** (Authenticated User)

### Tiền Điều Kiện (Precondition)
- Người dùng đã đăng nhập thành công vào hệ thống
- Access token và refresh token được lưu trong `SessionManager`
- Người dùng đang sử dụng ứng dụng Chat

### Sơ Đồ Luồng (Flow)

#### Luồng Chính (Main Flow)

| Bước | Actor | Hệ Thống |
|------|-------|---------|
| 1 | Người dùng nhấp nút "Đăng xuất" | |
| 2 | | Hiển thị xác nhận đăng xuất |
| 3 | Người dùng xác nhận | |
| 4 | | Gọi API logout tới server xác thực |
| 5 | | Server hủy session và invalidate token |
| 6 | | Xóa token từ bộ nhớ client |
| 7 | | Đóng kết nối real-time |
| 8 | | Chuyển hướng về trang đăng nhập (login-view.fxml) |
| 9 | | Hiển thị thông báo "Đã đăng xuất thành công" |

#### Luồng Thay Thế (Alternative Flows)

**Nếu API logout thất bại:**
- Vẫn clear token từ phía client
- Hiển thị cảnh báo: "Không thể kết nối đến máy chủ. Token đã bị xóa khỏi thiết bị."
- Cho phép người dùng quay lại trang đăng nhập

### Hậu Điều Kiện (Postcondition)
- Người dùng không còn truy cập được dữ liệu cá nhân
- Session mới yêu cầu login lại
- Tất cả các connection real-time bị ngắt
- Người dùng được chuyển về màn hình login

### Các API Liên Quan
- **POST** `/api/users/auth/logout`
  - Request: `{ refreshToken: "string" }`
  - Response: `204 No Content`
  - Error Codes:
    - `400 Bad Request`: Token không hợp lệ hoặc đã hết hạn
    - `401 Unauthorized`: Không có token

### Các Class Chính
- `MainViewModel.logout()` - Quản lý logout
- `SessionManager.clear()` - Xóa token
- `KeycloakTokenAdapter.logout()` - Gọi API Keycloak
- `RealtimeChatService` - Đóng WebSocket connection

### Ghi Chú
- Logout là best practice để tránh token bị lạm dụng
- Refresh token được dùng để invalidate session toàn bộ
- Client phải tự động cleanup tất cả local data

---

## 2. USE CASE: NHẮN TIN

### Tên Use Case
- **Nhắn tin** (Send Message)

### Mô Tả
- Người dùng soạn và gửi tin nhắn text hoặc file tới người nhận hoặc nhóm chat. Tin nhắn sẽ được lưu trữ và truyền đạt real-time tới người nhận.

### Actor
- **Người dùng đã xác thực**

### Tiền Điều Kiện
- Người dùng đã đăng nhập
- Người dùng đã chọn một conversation (chat cá nhân hoặc nhóm)
- Có kết nối mạng

### Sơ Đồ Luồng

#### Luồng Chính

| Bước | Actor | Hệ Thống |
|------|-------|---------|
| 1 | Người dùng nhập nội dung tin nhắn | |
| 2 | Người dùng nhấn "Gửi" hoặc Enter | |
| 3 | | Validate: nội dung không được trống |
| 4 | | Tạo yêu cầu gửi tin nhắn |
| 5 | | Tạo tin nhắn tạm thời ở phía client (trạng thái đang gửi) |
| 6 | | Hiển thị tin nhắn trong danh sách |
| 7 | | Gửi tin nhắn qua kết nối real-time |
| 8 | | Server nhận, xử lý, lưu vào database |
| 9 | | Server lưu tin nhắn và trả về ID |
| 10 | | Client cập nhật tin nhắn: thay đổi ID tạm bằng ID thực, trạng thái = "SENT" |
| 11 | | Broadcast tin nhắn tới tất cả thành viên conversation |
| 12 | | Người nhận nhận được qua WebSocket và render |

#### Luồng Thay Thế

**Nếu content trống:**
- Không cho gửi
- Hiển thị cảnh báo: "Vui lòng nhập nội dung tin nhắn"

**Nếu mất kết nối:**
- Giữ tin nhắn pending trong UI
- Hiển thị: "Đang kết nối..."
- Tự động retry khi kết nối khôi phục

**Nếu gửi file:**
- Upload file trước qua endpoint `/api/messages/upload`
- Nhận URL file
- Gửi message với `fileUrl`, `fileName`, `messageType = "FILE"`

### Hậu Điều Kiện
- Tin nhắn hiển thị ở phía người gửi với trạng thái "SENT" hoặc "SEEN"
- Người nhận nhận được tin nhắn real-time
- Tin nhắn được lưu trữ vĩnh viễn trong database

### Các API Liên Quan
- **POST** `/api/messages`
  - Request:
    ```json
    {
      "conversationId": 123,
      "senderId": "user-id",
      "content": "Xin chào!",
      "messageType": "TEXT"
    }
    ```
  - Response: `MessageResponse` (có ID, status, createdAt)

- **POST** `/api/messages/upload`
  - Form-data: `file`
  - Response: `{ fileUrl: "uploads/..." }`

### Data Model
```
Message {
  id: Long (auto-increment)
  conversationId: Long
  senderId: String
  content: String
  fileUrl: String (optional)
  fileName: String (optional)
  messageType: TEXT | FILE | IMAGE
  status: SENT | DELIVERED | SEEN
  createdAt: LocalDateTime
  isDeleted: boolean
}
```

### Các Class Chính
- `ChatViewModel.sendMessage()` - Logic gửi
- `ChatService.sendMessage()` - API call
- `RealtimeChatService` - WebSocket broadcasting
- `MessageService` (backend) - Business logic
- `MessageRepository` - Persist to DB

### Ghi Chú
- Tin nhắn có trạng thái: SENT → DELIVERED → SEEN
- File tối đa 100 MB
- Hỗ trợ media types: TEXT, FILE, IMAGE
- Message type được tự động detect từ file extension

---

## 3. USE CASE: XÓA TIN NHẮN

### Tên Use Case
- **Xóa tin nhắn** (Delete Message For User)

### Mô Tả
- Người dùng xóa một tin nhắn khỏi cuộc hội thoại của mình. Tin nhắn sẽ biến mất từ view của người dùng, nhưng vẫn có thể tồn tại với người dùng khác.

### Actor
- **Người dùng đã xác thực**

### Tiền Điều Kiện
- Người dùng đã chọn một tin nhắn trong conversation
- Tin nhắn không phải của hệ thống
- Tin nhắn chưa bị xóa

### Sơ Đồ Luồng

#### Luồng Chính

| Bước | Actor | Hệ Thống |
|------|-------|---------|
| 1 | Người dùng click chuột phải/menu trên tin nhắn | |
| 2 | | Hiển thị context menu |
| 3 | Người dùng chọn "Xóa" | |
| 4 | | Hiển thị confirm: "Xóa tin nhắn này?" |
| 5 | Người dùng xác nhận | |
| 6 | | Gửi request: `DELETE /api/messages/{id}?userId={userId}` |
| 7 | | Server đánh dấu tin nhắn là đã xóa cho user này |
| 8 | | Broadcast message update qua WebSocket |
| 9 | | Client cập nhật trạng thái tin nhắn: đánh dấu là đã xóa |
| 10 | | Render lại tin nhắn với trạng thái ẩn hoặc "Bạn đã xóa tin nhắn" |

#### Luồng Thay Thế

**Nếu người dùng hủy:**
- Không thực hiện xóa
- Đóng confirm dialog

**Nếu API thất bại:**
- Hiển thị lỗi: "Không thể xóa tin nhắn"
- Tin nhắn vẫn hiển thị bình thường

### Hậu Điều Kiện
- Tin nhắn không hiển thị trong view của người dùng (người gửi có thể vẫn nhìn thấy)
- Người dùng khác vẫn nhìn thấy tin nhắn bình thường
- Database vẫn lưu dữ liệu

### Các API Liên Quan
- **DELETE** `/api/messages/{id}?userId={userId}`
  - Response: `204 No Content`
  - Error: `404 Not Found`, `403 Forbidden`

### Database Logic
```
// Trước khi xóa
deletedForUsers = ""

// Sau khi xóa bởi user-123
deletedForUsers = "user-123"

// Nếu user-456 cũng xóa
deletedForUsers = "user-123,user-456"
```

### Các Class Chính
- `ChatViewModel.deleteMessageForUser()` - Trigger xóa
- `ChatService.deleteMessageForUser()` - API call
- `MessageService.deleteMessageForUser()` - Business logic (backend)
- `MessageRepository.save()` - Update database

### Ghi Chú
- Xóa là soft delete (chỉ ẩn khỏi user đó)
- Tin nhắn vẫn tồn tại trên server
- Có thể khôi phục bằng cách clear `deletedForUsers`

---

## 4. USE CASE: THU HỒI TIN NHẮN

### Tên Use Case
- **Thu hồi tin nhắn** (Recall Message)

### Mô Tả
- Người dùng thu hồi một tin nhắn đã gửi. Tin nhắn sẽ bị xóa hoàn toàn từ tất cả mọi người nhận, và thay thế bằng thông báo "Tin nhắn đã bị thu hồi".

### Actor
- **Người dùng đã xác thực** (chỉ người gửi)

### Tiền Điều Kiện
- Tin nhắn là của người dùng hiện tại (người gửi)
- Tin nhắn chưa bị thu hồi
- Tin nhắn được gửi **không quá 24 giờ**
- Tin nhắn chưa bị xóa

### Sơ Đồ Luồng

#### Luồng Chính

| Bước | Actor | Hệ Thống |
|------|-------|---------|
| 1 | Người dùng click menu tin nhắn của mình | |
| 2 | | Hiển thị "Thu hồi" (chỉ nếu là 24h gần đây) |
| 3 | Người dùng chọn "Thu hồi" | |
| 4 | | Confirm: "Thu hồi tin nhắn này?" |
| 5 | Người dùng xác nhận | |
| 6 | | Gửi: `PUT /api/messages/{id}/recall?userId={userId}` |
| 7 | | Server kiểm tra: `createdAt + 24h > now()` |
| 8 | | Server kiểm tra: người dùng hiện tại là người gửi |
| 9 | | Server đánh dấu tin nhắn là đã bị thu hồi |
| 10 | | Broadcast cập nhật tin nhắn tới tất cả user |
| 11 | | Tất cả client nhận cập nhật: tin nhắn được đánh dấu là đã xóa |
| 12 | | Render: "Bạn đã thu hồi tin nhắn này" (nếu gửi) hoặc "Tin nhắn đã bị thu hồi" (nếu nhận) |

#### Luồng Thay Thế

**Nếu quá 24 giờ:**
- Menu không hiển thị "Thu hồi"
- Nếu user cố gắng gọi API: `400 Bad Request` "Message can only be recalled within 24 hours"

**Nếu người dùng không phải người gửi:**
- Button "Thu hồi" không hiển thị
- Nếu cố gắng: `403 Forbidden` "You can only recall your own messages"

**Nếu API thất bại:**
- Hiển thị: "Không thể thu hồi tin nhắn"

### Hậu Điều Kiện
- Tin nhắn bị đánh dấu là deleted
- Tất cả user thấy: "Tin nhắn đã bị thu hồi"
- Nội dung gốc không thể khôi phục
- File đính kèm cũng bị xóa khỏi view

### Các API Liên Quan
- **PUT** `/api/messages/{id}/recall?userId={userId}`
  - Response: `MessageResponse` (có `isDeleted=true`)
  - Error:
    - `403 Forbidden`: Không phải người gửi
    - `400 Bad Request`: Quá thời hạn 24h

### Business Logic
```java
Kiểm tra:
- Chỉ người gửi mới có quyền thu hồi
- Tin nhắn phải được gửi không quá 24 giờ

Nếu đạt tiêu chí:
- Đánh dấu tin nhắn là đã bị xóa
- Ghi lại thời gian và người xóa
```

### Các Class Chính
- `ChatViewModel.recallMessage()` - Trigger thu hồi
- `ChatService.recallMessage()` - API call
- `MessageService.recallMessage()` - Business logic (backend)

### Ghi Chú
- Thu hồi = hard delete (xóa vĩnh viễn, không thể undo)
- Giới hạn 24 giờ là để ngăn abuse
- Người nhận vẫn thấy notification "Tin nhắn đã bị thu hồi"

---

## 5. USE CASE: GHIM TIN NHẮN

### Tên Use Case
- **Ghim tin nhắn** (Pin Message)

### Mô Tả
- Người dùng ghim một tin nhắn quan trọng để dễ tìm kiếm sau này. Tin nhắn ghim sẽ hiển thị trong khu vực riêng "Tin nhắn đã ghim" ở trên cùng chat area.

### Actor
- **Người dùng đã xác thực** (bất cứ ai trong conversation)

### Tiền Điều Kiện
- Tin nhắn tồn tại trong conversation
- Tin nhắn chưa bị xóa hoàn toàn
- Người dùng có quyền truy cập conversation

### Sơ Đồ Luồng

#### Luồng Chính - Ghim (Pin)

| Bước | Actor | Hệ Thống |
|------|-------|---------|
| 1 | Người dùng click menu tin nhắn | |
| 2 | | Hiển thị "Ghim" |
| 3 | Người dùng chọn "Ghim" | |
| 4 | | Gửi: `PUT /api/messages/{id}/pin?value=true` |
| 5 | | Server đánh dấu tin nhắn là ghim |
| 6 | | Broadcast message update |
| 5 | | Client cập nhật danh sách tin nhắn ghim |
| 8 | | Hiển thị tin nhắn trong vùng "TIN NHẮN ĐÃ GHIM" |
| 9 | | Button: thay đổi "Ghim" → "Bỏ ghim" |

#### Luồng Phụ - Bỏ Ghim (Unpin)

| Bước | | |
|------|---|--|
| 1 | Người dùng click "Bỏ ghim" hoặc tại vùng pinned | |
| 2 | | Gửi: `PUT /api/messages/{id}/pin?value=false` |
| 3 | | Server bỏ ghim tin nhắn |
| 4 | | Broadcast update |
| 5 | | Client xóa tin nhắn khỏi danh sách ghim |
| 6 | | UI update: button "Ghim" trở lại |

#### Luồng Thay Thế

**Nếu API thất bại:**
- Hiển thị: "Không thể ghim tin nhắn"
- UI revert về trạng thái cũ

**Nếu đã đạt giới hạn ghim:**
- Hiển thị: "Giới hạn tin nhắn ghim"
- Gợi ý bỏ ghim cái cũ trước

### Hậu Điều Kiện
- Tin nhắn được đánh dấu `pinned=true` hoặc `false`
- Tất cả user trong conversation thấy cộng thêm/bớt tin nhắn ở vùng pinned
- Vùng pinned tự động ẩn/hiển thị theo số lượng

### Các API Liên Quan
- **PUT** `/api/messages/{id}/pin?value=true|false`
  - Response: `MessageResponse` (có `pinned=true/false`)

- **GET** `/api/messages/pinned/{conversationId}`
  - Response: `List<MessageResponse>` (những tin nhắn có `pinned=true`)

### Data Model
```
Message {
  ...
  pinned: boolean (default = false)
  ...
}
```

### UI Components
```
VBox pinnedArea {
  Label: "TIN NHẮN ĐÃ GHIM"
  ListView<PinnedMessageItem> pinnedMessageList
    - Hiển thị content + sender + time
    - Nút "Bỏ ghim"
    - Click scroll tới tin nhắn gốc
}
```

### Các Class Chính
- `ChatViewModel.togglePin()` - Toggle ghim/bỏ ghim
- `ChatViewModel.refreshPinnedMessages()` - Refresh list
- `ChatService.setMessagePinned()` - API call
- `MessageService.setPinned()` - Backend logic

### Ghi Chú
- Ghim chỉ là bookmark, không ảnh hưởng tin nhắn gốc
- Tất cả user thấy cùng danh sách pinned
- Có thể ghim nhiều tin nhắn
- Giới hạn pinned nên là 10-20 tin (tùy thiết kế)

---

## 6. USE CASE: TRA CỨU AI

### Tên Use Case
- **Tra cứu AI** (AI Search / Ask AI)

### Mô Tả
- Người dùng hỏi câu hỏi hoặc yêu cầu AI Assistant giúp đỡ. AI sẽ xử lý câu hỏi và trả về câu trả lời.

### Actor
- **Người dùng đã xác thực**

### Tiền Điều Kiện
- Người dùng đã mở chat với TRỢ LÝ AI
- Có kết nối internet tới AI service
- Token xác thực hợp lệ

### Sơ Đồ Luồng

#### Luồng Chính

| Bước | Actor | Hệ Thống |
|------|-------|---------|
| 1 | Người dùng nhập câu hỏi | |
| 2 | Người dùng nhấn gửi | |
| 3 | | Tạo MessageItem tạm (user message) |
| 4 | | Hiển thị câu hỏi trong chat |
| 5 | | Set: `aiLoading = true`, hiển thị "Đang nhập..." |
| 6 | | Gửi câu hỏi tới AI service |
| 7 | | AI service xử lý yêu cầu |
| 8 | | AI service xử lý (LLM) |
| 9 | | Trả về: `{ answer: "..." }` |
| 10 | | Tạo MessageItem trả lời từ AI |
| 11 | | Set: `aiLoading = false` |
| 12 | | Hiển thị câu trả lời |
| 13 | | Scroll to bottom |

#### Luồng Thay Thế

**Nếu AI timeout (> 60s):**
- Hiển thị: "AI không phản hồi. Vui lòng thử lại."
- Set `aiLoading = false`

**Nếu AI không thể xử lý:**
- Trả về: `{ answer: "Tôi không hiểu câu hỏi. Vui lòng rephrase." }`

**Nếu mất kết nối:**
- Hiển thị: "Không thể kết nối tới AI service"
- Allow user retry

### Hậu Điều Kiện
- Câu hỏi được lưu trong lịch sử chat
- Câu trả lời từ AI được hiển thị
- Lịch sử có thể xem lại bất kỳ lúc nào

### Các API Liên Quan
- **POST** `/api/ai`
  - Request: `{ question: "string" }`
  - Response: `{ answer: "string" }`
  - Timeout: 60 giây
  - Headers: `Authorization: Bearer {token}`

### Message Format
```
User Message {
  sender: "Bạn"
  content: "Cho tôi biết cách..."
  time: "14:30"
}

AI Response {
  sender: "TRỢ LÝ AI"
  content: "Tôi có thể giúp bạn bằng cách..."
  time: "14:31"
  type: "ai-message" (style gradient)
}
```

### UI Components
```
TypeLabel: "Đang nhập..." (when aiLoading = true)
AIProgressIndicator: visible when processing
```

### Các Class Chính
- `ChatViewModel.askAI()` - Trigger AI query
- `AIService.callAIAssistant()` - HTTP call
- `ChatController.handleSelectAIAssistant()` - Open AI chat
- Message renderer (special styling for AI)

### Configuration
```
GatewayConfig.getInstance().getAiUrl()
// e.g., http://localhost:8080/api/ai
```

### Ghi Chú
- AI Assistant là user đặc biệt với ID = "AI_ASSISTANT"
- Lưu toàn bộ lịch sử AI queries
- AI không thực sự "learning" - mỗi query độc lập
- Response time tùy thuộc AI backend complexity
- Có thể implement caching cho popular questions
- AI messages được style riêng (gradient)

---

## Tóm Tắt So Sánh

| Use Case | Actor | API | Status Backend | UI Feedback |
|----------|-------|-----|-------------------|------------|
| Đăng xuất | User | POST /logout | 204 No Content | "Về login page" |
| Nhắn tin | User | POST /messages | 200 + SENT | "Pending" → "SENT" |
| Xóa TN | User | DELETE /messages | 204 | "Tin nhắn bị ẩn" |
| Thu hồi | User | PUT /recall | 200 + DELETED | "Đã thu hồi" |
| Ghim | User | PUT /pin | 200 + PINNED | "Hiển thị vùng pinned" |
| Tra cứu AI | User | POST /ai | 200 + ANSWER | "Loading..." → Answer |

---

## Kiến Trúc Hệ Thống

```
┌─────────────────────────────────────────┐
│  GitLab Handbook Chat (JavaFX Client)   │
├──────────────┬──────────────────────────┤
│  ChatController & ChatViewModel         │
│  - Quản lý UI interactions              │
│  - Real-time WebSocket handling         │
│  - Local state management               │
└──────────────┴──────────────────────────┘
           ↓ API Gateway ↓
┌──────────────────────────────────────────┐
│  API Gateway (Port 8080/8443)            │
│  - Route to services                     │
│  - Token validation                      │
└──────────────────────────────────────────┘
    ↓            ↓            ↓
┌──────────┐ ┌──────────┐ ┌──────────┐
│   User   │ │  Chat    │ │   AI     │
│ Service  │ │ Service  │ │ Service  │
│ :8090    │ │ :8091    │ │ :8092    │
└──────────┘ └──────────┘ └──────────┘
    ↓            ↓            ↓
┌──────────┐ ┌──────────┐ ┌──────────┐
│  Keycloak│ │ PostgreSQL    │
│ :8180    │ │ (Messages, Users)
└──────────┘ └──────────┘
```

---

## Authentication & Authorization

- **Token Type**: JWT (Access Token + Refresh Token)
- **Issuer**: Keycloak
- **Storage Client**: SessionManager
- **Validation**: Gateway filters + service-level checks
- **Expiry**: Access token 5-15 min, Refresh token 24 hours

---

## Error Handling

| Error | Code | Message | Action |
|-------|------|---------|--------|
| Invalid Token | 401 | "Token không hợp lệ" | Refresh or login |
| No Permission | 403 | "Không có quyền truy cập" | Show error |
| Resource Not Found | 404 | "Không tìm thấy" | Go back |
| Server Error | 500 | "Lỗi máy chủ" | Retry later |
| Network Error | - | "Mất kết nối" | Offline mode |

---

