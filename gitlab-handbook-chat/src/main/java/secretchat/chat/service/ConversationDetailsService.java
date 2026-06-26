package secretchat.chat.service;

import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.dto.response.MessageResponse;
import secretchat.util.LinkUtils;
import secretchat.util.MessageTimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class ConversationDetailsService {
    public List<SharedFile> files(List<ChatViewModel.MessageItem> messages) {
        return messages.stream()
                .filter(item -> item.isFile() && !item.isDeleted() && !item.isDeletedForMe())
                .map(item -> {
                    MessageResponse response = item.getResponse();
                    return new SharedFile(response, response.getFileName(), item.getSenderName(),
                            MessageTimeFormatter.format(response.getCreatedAt()), response.getFileSize());
                }).toList();
    }

    public List<SharedLink> links(List<ChatViewModel.MessageItem> messages) {
        List<SharedLink> result = new ArrayList<>();
        for (ChatViewModel.MessageItem item : messages) {
            if (item.isDeleted() || item.isDeletedForMe() || item.getContent() == null) continue;
            for (LinkUtils.LinkMatch link : LinkUtils.findLinks(item.getContent())) {
                result.add(new SharedLink(link.value(), item.getSenderName(),
                        item.getResponse() == null
                                ? item.getTime()
                                : MessageTimeFormatter.format(item.getResponse().getCreatedAt())));
            }
        }
        return result;
    }

    public String formatSize(Long bytes) {
        if (bytes == null) return "Không rõ";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024d);
        return String.format("%.1f MB", bytes / (1024d * 1024d));
    }

    public record SharedFile(MessageResponse message, String name, String sender, String time, Long size) {}
    public record SharedLink(String url, String sender, String time) {}
}
