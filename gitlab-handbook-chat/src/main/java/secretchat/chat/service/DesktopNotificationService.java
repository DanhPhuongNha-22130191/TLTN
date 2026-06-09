package secretchat.chat.service;

import javafx.application.Platform;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class DesktopNotificationService implements AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(DesktopNotificationService.class.getName());

    private TrayIcon trayIcon;
    private Runnable clickAction;

    public DesktopNotificationService() {
        initializeTray();
    }

    public boolean isSupported() {
        return trayIcon != null;
    }

    public void show(String title, String message, Runnable onClick) {
        if (trayIcon == null) return;
        clickAction = onClick;
        trayIcon.displayMessage(title, shorten(message), TrayIcon.MessageType.INFO);
    }

    private void initializeTray() {
        if (!SystemTray.isSupported()) return;
        try {
            trayIcon = new TrayIcon(createIcon(), "Secret Chat");
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(event -> {
                Runnable action = clickAction;
                if (action != null) Platform.runLater(action);
            });
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() > 0) {
                        Runnable action = clickAction;
                        if (action != null) Platform.runLater(action);
                    }
                }
            });
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException | RuntimeException ex) {
            trayIcon = null;
            LOGGER.log(System.Logger.Level.WARNING,
                    "Không thể khởi tạo system notification", ex);
        }
    }

    private Image createIcon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(87, 91, 232));
            graphics.fillRoundRect(1, 1, 30, 30, 6, 6);
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(7, 8, 18, 13, 4, 4);
            graphics.fillPolygon(new int[]{11, 15, 11}, new int[]{21, 21, 26}, 3);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private String shorten(String value) {
        if (value == null || value.isBlank()) return "Bạn có tin nhắn mới";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 117) + "..." : normalized;
    }

    @Override
    public void close() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }
}
