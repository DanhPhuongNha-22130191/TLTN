---
controlled_document: true
description: Chính sách này nêu rõ các yêu cầu liên quan đến việc sử dụng tài nguyên
  máy tính và tài sản dữ liệu GitLab
tags:
- security_policy
- security_policy_caplscsi
title: Chính sách sử dụng nội bộ được chấp nhận của GitLab
---
{{< label name="Khả năng hiển thị: Kiểm toán" color="#E24329" >}}

## Mục đích

Chính sách này nêu rõ các yêu cầu liên quan đến việc Thành viên nhóm GitLab sử dụng tài nguyên máy tính và tài sản dữ liệu để bảo vệ khách hàng, Thành viên nhóm, nhà thầu, công ty và các đối tác khác của chúng tôi khỏi bị tổn hại do sử dụng sai mục đích cố ý và vô ý. Mục đích của chúng tôi khi xuất bản chính sách này là nhằm phác thảo các nguyên tắc bảo mật thông tin nhằm bảo vệ tài sản GitLab.

Trách nhiệm của mọi thành viên trong Cộng đồng của chúng tôi là tương tác với tài nguyên và dữ liệu máy tính GitLab một cách an toàn và vì mục đích đó, chúng tôi cung cấp các tiêu chuẩn sử dụng được chấp nhận sau đây liên quan đến tài nguyên máy tính, dữ liệu của công ty và khách hàng, thiết bị di động và máy tính bảng cũng như các thiết bị lưu trữ phương tiện bên ngoài và di động.

## Phạm vi

Chính sách này áp dụng cho tất cả Thành viên nhóm GitLab, nhà thầu, cố vấn và các bên ký hợp đồng tương tác với tài nguyên máy tính GitLab và truy cập dữ liệu của công ty hoặc khách hàng.

## Vai trò & Trách nhiệm

| Vai trò | Trách nhiệm |
|----------||----------|
| Thành viên nhóm GitLab | Chịu trách nhiệm thực hiện các yêu cầu trong quy trình này |
| An ninh, Pháp lý và Con ngườiOps | Chịu trách nhiệm triển khai và thực hiện quy trình này |
| Bảo mật, Pháp lý và Con người (Chủ sở hữu mã) | Chịu trách nhiệm phê duyệt những thay đổi quan trọng và ngoại lệ đối với thủ tục này |

## Thủ tục

### Các yêu cầu về sử dụng và bảo mật được chấp nhận đối với tài nguyên máy tính tại GitLab

Các tài sản do GitLab quản lý được cung cấp để tiến hành hoạt động kinh doanh GitLab, có xem xét đến việc sử dụng cá nhân có giới hạn tùy theo mọi tuyên bố mâu thuẫn có trong bất kỳ hợp đồng hoặc thỏa thuận lao động cá nhân nào. Công ty chúng tôi sử dụng các tài nguyên và thông tin liên lạc điện tử toàn cầu như một phần thường lệ trong các hoạt động kinh doanh của mình. Việc sử dụng cá nhân và chuyên nghiệp các tài sản do GitLab quản lý phải được giám sát và bảo vệ an ninh, trừ khi bị cấm theo luật pháp địa phương thuộc khu vực pháp lý tương ứng của Thành viên nhóm. Điều cần thiết là các tài nguyên điện tử được sử dụng để thực hiện hoạt động kinh doanh của công ty phải được bảo vệ để đảm bảo rằng các tài nguyên này có thể truy cập được cho mục đích kinh doanh và vận hành theo cách tiết kiệm chi phí, danh tiếng của công ty chúng ta được bảo vệ và chúng ta giảm thiểu nguy cơ rủi ro pháp lý.

Những người nhận tài sản do GitLab cung cấp có trách nhiệm đưa ra phán đoán đúng đắn khi sử dụng máy tính do GitLab quản lý và truy cập dữ liệu do GitLab quản lý.

Theo [quy trình xử lý sự cố khi triển khai](https://gitlab.com/gitlab-com/people-group/people-Operations/employment-templates/-/blob/main/.gitlab/issue_templates/onboarding.md) được nêu trong sổ tay của chúng tôi, bằng chứng về mã hóa thiết bị và số sê-ri thiết bị phải được cung cấp cho Bộ phận điều hành CNTT trước khi hoàn tất giai đoạn triển khai.

Chúng tôi hiện đang sử dụng Jamf làm giải pháp quản lý thiết bị đầu cuối cho máy tính xách tay Mac. Tất cả máy tính xách tay macOS do GitLab mua sẽ được định cấu hình với [Jamf](https://internal.gitlab.com/handbook/it/endpoint-tools/). Các thành viên nhóm GitLab mua sắm và chi tiêu Máy tính xách tay Mac sẽ yêu cầu cài đặt [Jamf](https://internal.gitlab.com/handbook/it/endpoint-tools/) như một phần của [Bảo mật ngày 1] của họ nhiệm vụ](https://gitlab.com/gitlab-com/people-group/people-Operations/employment-templates/-/blob/main/.gitlab/issue_templates/onboarding.md#day-1-getting-started-accounts-and-paperwork). Theo quyết định riêng của họ, CorpSec có thể cài đặt các công cụ bảo mật bổ sung thông qua Jamf.

Người dùng Linux phải cài đặt [SentinelOne](https://internal.gitlab.com/handbook/it/endpoint-tools/sentinelone/#how-do-i-install-the-sentinelone-agent-on-linux) và [DriveStrike](https://internal.gitlab.com/handbook/it/it-self-service/it-guides/drivestrike/) như một phần của quy trình làm quen với họ trong tuần đầu tiên.

Tài sản do GitLab phát hành là tài sản của công ty và phải được đối xử cẩn thận và tôn trọng. Nếu áp dụng nhãn dán cho tài sản của công ty:

- Đảm bảo mọi nội dung đều chuyên nghiệp và phù hợp với môi trường làm việc
- Không dán nhãn dán có thể làm hỏng thiết bị hoặc cản trở hoạt động của thiết bị
- Không che các cổng thông gió, quạt hoặc các bộ phận chức năng khác
- Không dán bất cứ vật gì lên màn hình hoặc những vùng có thể cản trở màn hình khi đóng lại

### Thông tin bảo mật và độc quyền
Tất cả dữ liệu GitLab đều được phân loại và phải được xử lý theo [Tiêu chuẩn phân loại dữ liệu](/handbook/security/policies_and_standards/data-classification-standard/). Tất cả tài sản điện toán kết nối với bất kỳ phần nào của mạng GitLab hoặc các dịch vụ của bên thứ 3 được GitLab sử dụng đều phải tuân thủ các tiêu chuẩn hiện hành.

### Ngoại lệ dữ liệu hỗ trợ khách hàng

Mặc dù việc truy cập vào dữ liệu RED trên các hệ thống không được phê duyệt thường bị cấm như đã nêu trong GitLab [Tiêu chuẩn phân loại dữ liệu](/handbook/security/policies_and_standards/data-classification-standard/), vẫn tồn tại một ngoại lệ giới hạn đối với các thành viên trong nhóm cung cấp hỗ trợ kỹ thuật cho khách hàng.

Các thành viên trong nhóm được phép di chuyển dữ liệu khách hàng được phân loại RED sang máy tính xách tay do GitLab quản lý của họ chỉ nhằm mục đích cung cấp dịch vụ hỗ trợ khách hàng. Ngoại lệ này phải tuân theo các yêu cầu sau:

- Di chuyển dữ liệu chỉ được phép đối với các trường hợp hỗ trợ khách hàng đang hoạt động
- Dữ liệu khách hàng phải được xóa ngay lập tức khi giải quyết trường hợp hỗ trợ
  - Trên macOS, việc này phải bao gồm việc dọn sạch Thùng rác hoặc sử dụng tiện ích dòng lệnh `rm` (bỏ qua Thùng rác).
- Tất cả các hoạt động xử lý dữ liệu phải được ghi lại và ghi lại trong phiếu ZenDesk

Quan trọng: Việc di chuyển dữ liệu khách hàng sang máy cục bộ bị cấm đối với tất cả các hoạt động kinh doanh khác ngoài việc cung cấp hỗ trợ khách hàng. Các thành viên trong nhóm yêu cầu quyền truy cập dữ liệu tương tự cho các mục đích khác phải tuân theo [Quy trình ngoại lệ của chính sách bảo mật và công nghệ](/handbook/security/security-and-technology-policy-Exception/#Exceptions) hoặc liên hệ với nhóm Bảo mật hoặc Quyền riêng tư nếu có câu hỏi trước khi tiếp tục.

### Thư viện mã nguồn mở

Chính sách này cho phép thư viện Nguồn mở nếu việc sử dụng thư viện đó tuân thủ các yêu cầu phê duyệt hoặc phê duyệt trước được nêu trong [Sổ tay pháp lý về sản phẩm](/handbook/legal/product/#using-open-source-software).

### Phần mềm miễn phí, Tiện ích mở rộng trình duyệt, Tiện ích bổ sung và Plugin

Cho phép sử dụng Phần mềm sử dụng cá nhân (phần mềm miễn phí, tiện ích bổ sung và plugin), ngoại trừ Tiện ích tích hợp Google Workspace và Tiện ích mở rộng của Chrome trái phép. Phần mềm Sử dụng Cá nhân có thể bị bộ phận CNTT, Bộ phận Pháp lý và Bảo mật loại bỏ bất kỳ lúc nào khi phần mềm đó được cho là không an toàn hoặc không an toàn.

Bạn có thể tìm thấy các Tiện ích tích hợp Google Workspace được ủy quyền và Phần mềm sử dụng cá nhân đã được phê duyệt khác trong danh sách [Phần mềm dành cho cá nhân được phê duyệt trước](https://internal.gitlab.com/handbook/finance/procurement/pre-approved-individual-use-software/) và được phép sử dụng tùy theo mọi "Ghi chú và hạn chế" đối với từng sản phẩm phần mềm được phê duyệt trước tương ứng.

Để yêu cầu phê duyệt cho Phần mềm sử dụng cá nhân mới, vui lòng làm theo quy trình yêu cầu [Phần mềm sử dụng cá nhân](/handbook/finance/procurement/individual-use-software/).

{{% notification title="Ghi chú" %}}
Để tuân thủ [Khuôn khổ AN TOÀN](/handbook/legal/safe-framework/), để ngăn chặn việc xử lý dữ liệu của công ty của bên thứ ba theo các điều khoản cấp phép sử dụng cá nhân cho phép hoặc để ngăn Thành viên nhóm lạm dụng một số tính năng của Phần mềm bên thứ ba, CNTT, Pháp lý, Quyền riêng tư và Bảo mật thường sẽ sai lầm khi không cho phép các ngoại lệ đối với phần mềm cấp doanh nghiệp, đặc biệt nếu có một tùy chọn doanh nghiệp đã có trong [Tech Stack Application](/handbook/business-technology/tech-stack-applications/) của chúng tôi đạt được mục đích tương tự.
{{% /cảnh báo %}}

### Việc sử dụng không được chấp nhận

Thành viên nhóm và nhà thầu có thể **không** sử dụng tài nguyên do GitLab quản lý cho các hoạt động bất hợp pháp hoặc bị cấm theo luật hiện hành, bất kể trường hợp nào.

Không được vô hiệu hóa các yêu cầu bảo mật đối với môi trường sản xuất và công ty GitLab cũng như trên các tài sản do GitLab quản lý mà không có phê duyệt bảo mật thông qua [Quy trình quản lý ngoại lệ chính sách bảo mật thông tin](/handbook/security/control-document-procedure/#Exceptions).

#### Hoạt động mạng và hệ thống không được chấp nhận

Các hoạt động hệ thống và mạng bị cấm bao gồm nhưng không giới hạn ở những hoạt động sau:

- Vi phạm quyền của bất kỳ cá nhân hoặc công ty nào được bảo vệ bởi bản quyền, bí mật thương mại, bằng sáng chế hoặc tài sản trí tuệ khác hoặc các luật hoặc quy định tương tự.
- Sao chép, phân phối hoặc sử dụng trái phép tài liệu có bản quyền.
- Xuất khẩu phần mềm, thông tin kỹ thuật, phần mềm mã hóa, công nghệ vi phạm pháp luật kiểm soát xuất khẩu quốc tế hoặc quốc gia.
- Cố ý đưa các chương trình độc hại vào mạng GitLab hoặc bất kỳ thiết bị điện toán nào do GitLab quản lý.
- Cố ý lạm dụng bất kỳ thiết bị điện toán nào do GitLab quản lý hoặc mạng GitLab (ví dụ: để khai thác tiền điện tử, kiểm soát mạng botnet, v.v.).
- Chia sẻ thông tin xác thực của bạn cho bất kỳ máy tính nào do GitLab quản lý hoặc dịch vụ của bên thứ 3 mà GitLab sử dụng với người khác hoặc cho phép người khác sử dụng tài khoản của bạn hoặc máy tính do GitLab quản lý. Lệnh cấm này không áp dụng cho các công nghệ đăng nhập một lần hoặc tương tự, việc sử dụng chúng đã được phê duyệt.
- Sử dụng tài sản điện toán GitLab để mua hoặc truyền tải tài liệu vi phạm chính sách quấy rối tình dục hoặc tạo ra môi trường làm việc thù địch.
- Đưa ra các đề nghị lừa đảo về sản phẩm, mặt hàng hoặc dịch vụ có nguồn gốc từ bất kỳ tài khoản GitLab nào.
- Cố ý truy cập dữ liệu hoặc đăng nhập vào máy tính hoặc tài khoản mà Thành viên nhóm hoặc nhà thầu không được phép truy cập hoặc làm gián đoạn liên lạc mạng, xử lý hoặc truy cập máy tính.
- Thực hiện bất kỳ hình thức giám sát mạng nào nhằm chặn dữ liệu không dành cho máy tính của Thành viên nhóm hoặc nhà thầu, ngoại trừ khi khắc phục sự cố mạng vì lợi ích của GitLab.
- Cố gắng bỏ qua, sửa đổi, vô hiệu hóa hoặc giả mạo các kiểm soát hoặc nhật ký bảo mật.
- Cố gắng gỡ cài đặt các biện pháp kiểm soát bảo mật mà không có sự chấp thuận trước của Người quản lý bảo mật
- Phá vỡ xác thực người dùng hoặc bảo mật của bất kỳ máy chủ, mạng hoặc tài khoản nào được GitLab sử dụng.
- Tạo đường hầm giữa các phân đoạn mạng hoặc vùng bảo mật (ví dụ: `gprd`, `gstg`, `ops`, `ci`, `ngrok`), ngoại trừ khi khắc phục sự cố vì lợi ích của GitLab.
- Do tính nhạy cảm tiềm ẩn của dữ liệu có trong ảnh chụp màn hình, việc sử dụng các công cụ chụp và chia sẻ ảnh chụp màn hình lên các trang web được lưu trữ trực tuyến đều bị cấm nếu không có sự chấp thuận rõ ràng của Bộ phận An ninh và Pháp lý.  Ảnh chụp màn hình phải được lưu trữ cục bộ hoặc trong các thư mục Google Drive được liên kết với tài khoản GitLab.com của bạn. Quyền truy cập vào các ổ đĩa và tệp này phải được quản lý theo [Chính sách quản lý quyền truy cập](/handbook/security/security-and-technology-policies/access-management-policy/) của chúng tôi và được xử lý theo [Tiêu chuẩn phân loại dữ liệu](/handbook/security/policies_and_standards/data-classification-standard/ của chúng tôi). Không nên sử dụng các công cụ như [Lightshot](https://app.prntscr.com/en/index.html), trong đó không thể tắt chức năng tải lên và có thể dẫn đến việc vô tình tải lên.
- Việc sử dụng các công cụ quản trị từ xa có rủi ro cao, chẳng hạn như TeamViewer và AnyDesk, [thường được những kẻ tấn công sử dụng](https://Attack.mitre.org/techniques/T1219/) để chiếm đoạt và điều khiển hệ thống từ xa.
- Các công cụ mô phỏng hệ điều hành khác hoặc tạo các lớp tương thích như [WINE](https://www.winehq.org/)
- Việc sử dụng torrent hoặc phần mềm P2P khác trên tài sản máy tính GitLab.

#### Hoạt động liên lạc và email không được chấp nhận

Việc chuyển tiếp các email hoặc tài liệu bí mật của doanh nghiệp tới các địa chỉ email cá nhân bên ngoài đều bị cấm. Việc tự động chuyển tiếp email từ tài khoản thành viên nhóm cũng bị cấm.

> Lưu ý: GitLab có thể truy xuất thư từ kho lưu trữ và máy chủ mà không cần thông báo trước nếu GitLab có đủ lý do để làm như vậy. Nếu thấy cần thiết, cuộc điều tra này sẽ được tiến hành với sự hiểu biết và chấp thuận của Bộ phận An ninh, Đối tác Kinh doanh Nhân dân và Phòng Pháp lý.

Ngoài việc tuân thủ [Chính sách truyền thông xã hội dành cho thành viên nhóm](/handbook/marketing/team-member-social-media-policy/), khi sử dụng mạng xã hội, hãy nghĩ đến tác động của những tuyên bố mà bạn đưa ra. Hãy nhớ rằng việc truyền tải này là vĩnh viễn và dễ dàng chuyển nhượng, đồng thời có thể ảnh hưởng đến danh tiếng cũng như mối quan hệ của công ty chúng ta với các Thành viên nhóm và khách hàng. Khi sử dụng các công cụ truyền thông xã hội như blog, Facebook, Twitter hoặc wiki, hãy đảm bảo rằng bạn không đưa ra nhận xét thay mặt GitLab mà không có sự cho phép thích hợp. Ngoài ra, bạn không được tiết lộ thông tin bí mật hoặc độc quyền của công ty chúng tôi về hoạt động kinh doanh, nhà cung cấp hoặc khách hàng của chúng tôi.

### Trả lại tài sản thuộc sở hữu của GitLab
Tất cả tài nguyên máy tính do GitLab sở hữu phải được [trả lại](/handbook/people-group/offboarding/#returning-property-to-gitlab) sau khi tách khỏi công ty.  Bất kể mọi điều trái ngược trong [Chính sách mua lại máy tính xách tay](/handbook/security/corporate/end-user-services/laptop-management/laptop-offboarding-returns/#laptop-buybacks) hoặc [Offboarding Tasks](/handbook/people-group/offboarding/#managing-the-offboarding-tasks), Thành viên nhóm phải trả lại mọi Tài sản thuộc sở hữu của GitLab -- bất kể giá trị của chúng -- nếu chúng được yêu cầu cụ thể làm như vậy trong thời gian họ làm việc với GitLab hoặc khi rời khỏi công ty. Trong trường hợp điều tra, hành vi sai trái, chấm dứt hợp đồng có lý do hoặc bất kỳ vi phạm nào đối với [Quy tắc ứng xử và đạo đức kinh doanh của GitLab](https://ir.gitlab.com/governance/governance-documents/default.aspx), thành viên nhóm không có quyền giữ lại thiết bị máy tính do GitLab sở hữu.

### Mang theo thiết bị của riêng bạn (BYOD)

Theo nguyên tắc chung, các thiết bị không phải của công ty không được phép truy cập vào tài sản của công ty. Mặc dù có một số trường hợp ngoại lệ được liệt kê bên dưới, nhưng việc truy cập vào dữ liệu được phân loại RED, như được xác định bởi [Tiêu chuẩn phân loại dữ liệu GitLab](/handbook/security/policies_and_standards/data-classification-standard/), vẫn bị cấm.

Các trường hợp ngoại lệ như sau:

#### Sử dụng điện thoại di động và máy tính bảng cá nhân

Tất cả các thiết bị điện toán di động cá nhân được sử dụng để truy cập dữ liệu do GitLab quản lý, bao gồm nhưng không giới hạn ở email và GitLab.com, phải được kích hoạt mật mã. 2FA sẽ được nhóm Bảo mật thực thi đối với tất cả tài khoản GitLab.com của nhân viên và nhà thầu cũng như tài khoản Google Workspace. Các phương pháp hay nhất về điện toán di động quy định rằng các thiết bị này phải chạy phiên bản hệ điều hành mới nhất hiện có và áp dụng tất cả các bản vá mới. Để được hỗ trợ xác định tính phù hợp của thiết bị di động của bạn, vui lòng liên hệ với Nhóm Bảo mật.

#### Không sử dụng được Laptop của Công ty

Đối với những nhân viên mới chưa nhận được máy tính xách tay của công ty, có [quy trình ngoại lệ](/handbook/security/corporate/end-user-services/laptop-management/laptop-ordering/#Exception-processes) để sử dụng các thiết bị không phải của công ty.

Các quy trình ngoại lệ tương tự được áp dụng trong trường hợp máy tính xách tay của công ty không có sẵn hoặc không thể sử dụng được do mất mát, trộm cắp hoặc hư hỏng. Xem [thủ tục bị mất hoặc bị đánh cắp](/handbook/security#reporting-an-incident) để biết thêm thông tin. Bạn phải mở [Yêu cầu miễn trừ chính sách](https://gitlab.com/gitlab-com/gl-security/security-assurance/sec-compliance/Exceptionions/issues/new?issuable_template=Exception_request). Mặc dù các quy trình ngoại lệ được coi là giải pháp tạm thời nhưng bạn vẫn cần đảm bảo hệ thống không phải của công ty đáp ứng [tiêu chuẩn cấu hình cơ bản](/handbook/security/corporate/end-user-services/laptop-management/laptop-security) và hệ thống Microsoft Windows vẫn không được phép truy cập trong mọi trường hợp.

Không đăng nhập vào bất kỳ tài khoản nào liên quan đến GitLab bằng máy tính công cộng, chẳng hạn như ki-ốt thư viện hoặc khách sạn.

### Nhắn tin di động

Tất cả các cuộc hội thoại liên quan đến GitLab cần diễn ra trong Slack. Chúng tôi đặc biệt khuyên bạn nên sử dụng ứng dụng Slack chính thức hoặc ứng dụng web Slack để nhắn tin trên thiết bị di động. Các bản tải xuống hiện có sẵn cho [iOS](https://apps.apple.com/us/app/slack/id618783545) và [Android](https://play.google.com/store/apps/details?id=com.Slack). Mặc dù có thể thuận tiện hơn khi sử dụng ứng dụng trò chuyện tích hợp để đặt tất cả các cuộc trò chuyện của bạn ở một nơi, nhưng việc sử dụng các ứng dụng này có thể vô tình dẫn đến các cuộc trò chuyện liên quan đến công việc xuyên nền tảng hoặc được gửi đến các liên hệ bên ngoài. Việc sử dụng Slack cho tất cả các hoạt động liên lạc trong công việc sẽ hỗ trợ các nỗ lực tuân thủ và bảo mật của chúng tôi. Ví dụ: trong trường hợp có vấn đề về ứng phó sự cố, có thể cần phải xem lại cuộc trò chuyện để hiểu thứ tự các sự kiện xảy ra hoặc để cung cấp bằng chứng cho thấy chuỗi hành trình sản phẩm đã được duy trì để làm bằng chứng pháp y trong quá trình bàn giao.

Đối với [cuộc gọi video](/handbook/communication/#video-calls) và để dự phòng cho Slack, chúng tôi thích Zoom hơn. Trò chuyện thu phóng là một giải pháp thay thế có thể chấp nhận được cho Slack khi thực hiện cuộc gọi điện video. Nếu cuộc trò chuyện thú vị với người khác hoặc có thể cần thiết để hồi tưởng lại cuộc trò chuyện, hãy cân nhắc ghi âm cuộc gọi.

### Sử dụng phương tiện bên ngoài trên tài sản của công ty
Việc sử dụng các thiết bị lưu trữ ngoài và di động như ổ flash USB và ổ đĩa sao lưu bên ngoài trên các thiết bị do công ty quản lý là không được phép và bị chặn theo mặc định. Bạn có thể mở yêu cầu ngoại lệ [tại đây](https://gitlab.com/gitlab-com/gl-security/corp/issue-tracker/-/issues/new?issuable_template=usb_Exception) nếu công việc có nhu cầu sử dụng thiết bị lưu trữ bên ngoài. Corporate Security sẽ phối hợp với những người yêu cầu để xác định thiết bị hỗ trợ mã hóa phù hợp nhất và định cấu hình mã hóa cũng như bảo vệ bằng mật khẩu.

Xin nhắc lại, Dữ liệu Đỏ không được truyền từ nguồn Dữ liệu Đỏ đã được phê duyệt đến bất kỳ hệ thống hoặc giải pháp nào khác mà không nhận được sự chấp thuận trước từ nhóm Quyền riêng tư và Bảo mật. Vui lòng tham khảo [Tiêu chuẩn phân loại dữ liệu](/handbook/security/policies_and_standards/data-classification-standard/) của GitLab để biết thêm chi tiết.

### Sử dụng các dịch vụ chia sẻ file ngoài Google Drive của GitLab

Việc tạo tài khoản hoặc sử dụng dịch vụ chia sẻ tệp không phải Google Drive của GitLab cho mục đích sao lưu/dự phòng đều bị cấm. Để chia sẻ các tệp liên quan đến GitLab với các tệp bên ngoài GitLab, phải cấp một ngoại lệ. Để có được ngoại lệ, hãy tạo [yêu cầu truy cập](/handbook/security/corporate/end-user-services/access-requests/access-requests/) phác thảo trường hợp kinh doanh và không tiếp tục cho đến khi ngoại lệ được cấp.

Khi tạo tài khoản và sử dụng dịch vụ chia sẻ tệp khác với Google Drive, Thành viên nhóm phải:

- Nếu có, hãy sử dụng tùy chọn `Đăng nhập bằng Google` đăng nhập một lần bằng tài khoản GitLab Google Workspace, thay vì tạo tài khoản bằng địa chỉ email và mật khẩu GitLab (hoặc nhà cung cấp khác).
- Chỉ tải tệp trực tiếp lên các thư mục dùng chung được tạo bởi những người bên ngoài GitLab và không tải tệp lên khu vực cá nhân của dịch vụ chia sẻ tệp.
- Xóa tất cả các file và đóng tài khoản khi không còn cần đến dịch vụ chia sẻ file.

### Thủ tục bị mất hoặc bị đánh cắp

GitLab cung cấp địa chỉ email `panic@gitlab.com` và [thủ tục bị mất hoặc bị đánh cắp](/handbook/security#reporting-an-incident) để các thành viên trong nhóm sử dụng trong các tình huống yêu cầu phản hồi bảo mật ngay lập tức. Nếu thành viên trong nhóm bị mất một thiết bị như ổ USB, Yubikey, điện thoại di động, máy tính bảng, máy tính xách tay, v.v. có chứa thông tin xác thực của họ hoặc dữ liệu nhạy cảm với GitLab khác, họ nên gửi email đến `panic@gitlab.com` ngay lập tức. Khi đội ngũ sản xuất và bảo mật nhận được email gửi đến địa chỉ này sẽ xử lý ngay lập tức. Sử dụng địa chỉ này là một cách tuyệt vời để hạn chế thiệt hại do mất một trong những thiết bị này.

GitLab có quyền yêu cầu tài liệu về hành vi trộm cắp và/hoặc báo cáo liên quan của cảnh sát trong trường hợp máy tính xách tay bị đánh cắp.

### Tuân thủ chính sách

Việc tuân thủ chính sách này sẽ được xác minh thông qua nhiều phương pháp khác nhau, bao gồm nhưng không giới hạn ở báo cáo tự động, kiểm tra và phản hồi cho chủ sở hữu chính sách.

Bất kỳ Thành viên nhóm hoặc nhà thầu nào bị phát hiện vi phạm chính sách này đều có thể phải chịu biện pháp kỷ luật, lên đến và bao gồm cả việc chấm dứt hợp đồng lao động hoặc thỏa thuận hợp đồng.

###Tư vấn

Để tham khảo ý kiến của Nhóm bảo mật, hãy tạo một vấn đề trong [Trình theo dõi tuân thủ bảo mật](https://gitlab.com/gitlab-com/gl-security/security-assurance/team-commercial-compliance/compliance/issues).

## Ngoại lệ

Các trường hợp ngoại lệ đối với chính sách này phải được Bộ phận An ninh, Pháp lý và Nhân sự phê duyệt.

- [Vấn đề giới thiệu](https://gitlab.com/gitlab-com/people-group/people-Operations/employment-templates/-/blob/main/.gitlab/issue_templates/onboarding.md)
- [Tiêu chuẩn phân loại dữ liệu](/handbook/security/policies_and_standards/data-classification-standard/)
- [Thủ tục hoàn trả tài sản](/handbook/people-group/offboarding/#returning-property-to-gitlab)
- [Thủ tục tài sản bị mất hoặc bị đánh cắp](/handbook/security#reporting-an-incident)

## Tài liệu tham khảo

- [Quy tắc ứng xử cộng đồng](https://about.gitlab.com/community/contribute/code-of-conduct/) của GitLab áp dụng cho tất cả thành viên của cộng đồng GitLab