package com.habbashx.vaultx.ui.viewer;

import com.habbashx.vaultx.core.TempFiles;
import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;
import com.habbashx.vaultx.ui.Branding;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.State;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MediaPlayerFrame extends JFrame {

    private static final int SEEK_TICKS = 1000;

    private final Path source;
    private EmbeddedMediaPlayerComponent player;
    private final JToggleButton playPause = new JToggleButton("Play");
    private final JButton stop = new JButton("Stop");
    private final JToggleButton mute = new JToggleButton("Mute");
    private final JSlider seek = new JSlider(0, SEEK_TICKS, 0);
    private final JSlider volume = new JSlider(0, 100, 80);
    private final JLabel time = new JLabel("00:00 / 00:00");
    private final JLabel notice = new JLabel(" ");
    private boolean seeking;
    private long lengthMillis;

    public MediaPlayerFrame(VaultItem item, VaultManager manager, Path source) {
        super(item.name + " — Media Player");
        this.source = source;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Branding.installWindowIcon(this);

        configureVlcDiscovery();

        try {
            player = new EmbeddedMediaPlayerComponent();
        } catch (Throwable t) {
            int choice = JOptionPane.showOptionDialog(null,
                    "VLC media player was not found, so audio and video playback is unavailable.\n\n"
                            + "Your vault and all other features (images, PDFs, text, encryption) still work.\n\n"
                            + "To play media, install the free 64-bit VLC 3.x and restart the app.",
                    "VLC required for media playback", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null,
                    new String[]{"Download VLC", "Close"}, "Download VLC");
            if (choice == 0) {
                openDownloadPage();
            }
            TempFiles.delete(source);
            dispose();
            return;
        }

        playPause.addActionListener(e -> togglePlayPause());
        stop.addActionListener(e -> player.mediaPlayer().controls().stop());
        JButton fullscreen = new JButton("Fullscreen");
        fullscreen.addActionListener(e -> toggleFullscreen());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        seek.setToolTipText("Seek");
        seek.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                seeking = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (player != null) {
                    applySeekPosition();
                }
                seeking = false;
            }
        });
        seek.addChangeListener(e -> {
            if (seeking && player != null) {
                player.mediaPlayer().controls().setPosition(seek.getValue() / (float) SEEK_TICKS);
            }
        });

        volume.setToolTipText("Volume");
        volume.addChangeListener(e -> {
            if (player == null) {
                return;
            }
            try {
                int value = volume.getValue();
                player.mediaPlayer().audio().setVolume(value);
                if (value > 0 && mute.isSelected()) {
                    mute.setSelected(false);
                    player.mediaPlayer().audio().setMute(false);
                }
            } catch (Throwable ignored) {
            }
        });

        mute.addActionListener(e -> setMute(mute.isSelected()));

        JToolBar controls = new JToolBar();
        controls.setFloatable(false);
        controls.add(playPause);
        controls.add(stop);
        controls.addSeparator(new Dimension(8, 0));
        controls.add(seek);
        controls.addSeparator(new Dimension(8, 0));
        controls.add(new JLabel("Vol:"));
        controls.add(volume);
        controls.add(mute);
        controls.addSeparator(new Dimension(24, 0));
        controls.add(fullscreen);
        controls.addSeparator(new Dimension(24, 0));
        controls.add(close);

        for (JComponent c : new JComponent[]{playPause, stop, mute, seek, volume, fullscreen, close}) {
            c.setFocusable(false);
        }

        JPanel south = new JPanel(new BorderLayout());
        south.add(time, BorderLayout.NORTH);
        south.add(notice, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        content.add(player, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);
        content.add(south, BorderLayout.NORTH);

        setContentPane(content);
        bindKeys(content);

        player.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleFullscreen();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                startPlayback();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                shutdown();
            }
        });

        setSize(860, 620);
        setLocationRelativeTo(null);

        player.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mp) {
                SwingUtilities.invokeLater(() -> {
                    playPause.setSelected(true);
                    playPause.setText("Pause");
                    notice.setText("Playing");
                });
            }

            @Override
            public void paused(MediaPlayer mp) {
                SwingUtilities.invokeLater(() -> {
                    playPause.setSelected(false);
                    playPause.setText("Play");
                    notice.setText("Paused");
                });
            }

            @Override
            public void stopped(MediaPlayer mp) {
                SwingUtilities.invokeLater(() -> {
                    playPause.setSelected(false);
                    playPause.setText("Play");
                    if (!seeking) {
                        seek.setValue(0);
                        time.setText("00:00 / " + format(lengthMillis));
                    }
                    notice.setText("Stopped");
                });
            }

            @Override
            public void finished(MediaPlayer mp) {
                SwingUtilities.invokeLater(() -> {
                    playPause.setSelected(false);
                    playPause.setText("Play");
                    notice.setText("Finished");
                });
            }

            @Override
            public void positionChanged(MediaPlayer mp, float newPosition) {
                SwingUtilities.invokeLater(() -> {
                    if (!seeking) {
                        seek.setValue(Math.max(0, Math.min(SEEK_TICKS, (int) (newPosition * SEEK_TICKS))));
                    }
                });
            }

            @Override
            public void timeChanged(MediaPlayer mp, long newTime) {
                SwingUtilities.invokeLater(() -> time.setText(format(newTime) + " / " + format(lengthMillis)));
            }

            @Override
            public void lengthChanged(MediaPlayer mp, long newLength) {
                lengthMillis = newLength;
            }

            @Override
            public void error(MediaPlayer mp) {
                SwingUtilities.invokeLater(() ->
                        notice.setText("Playback error. The codec may not be supported by VLC. Try Export to get the raw file."));
            }
        });

        applyVolume(volume.getValue());
        notice.setText("Loading\u2026");
    }

    private void startPlayback() {
        if (player == null) {
            return;
        }
        try {
            player.mediaPlayer().media().play(source.toString());
        } catch (Throwable t) {
            notice.setText("Could not start playback: " + t.getMessage());
        }
    }

    private static void openDownloadPage() {
        try {
            Desktop.getDesktop().browse(new URI("https://www.videolan.org/vlc/"));
        } catch (Throwable ignored) {
        }
    }

    private void togglePlayPause() {
        if (player == null) {
            return;
        }
        State state = player.mediaPlayer().status().state();
        if (state == State.PLAYING) {
            player.mediaPlayer().controls().setPause(true);
            playPause.setSelected(false);
            playPause.setText("Play");
        } else if (state == State.PAUSED) {
            player.mediaPlayer().controls().setPause(false);
            playPause.setSelected(true);
            playPause.setText("Pause");
        } else {
            player.mediaPlayer().media().play(source.toString());
        }
    }

    private void seekBy(long deltaMillis) {
        if (player == null) {
            return;
        }
        try {
            long target = Math.max(0, player.mediaPlayer().status().time() + deltaMillis);
            player.mediaPlayer().controls().setTime(target);
        } catch (Throwable ignored) {
        }
    }

    private void volumeBy(int delta) {
        if (player == null) {
            return;
        }
        volume.setValue(Math.max(0, Math.min(100, volume.getValue() + delta)));
    }

    private void applySeekPosition() {
        try {
            player.mediaPlayer().controls().setPosition(seek.getValue() / (float) SEEK_TICKS);
        } catch (Throwable ignored) {
        }
    }

    private void applyVolume(int value) {
        try {
            player.mediaPlayer().audio().setVolume(value);
        } catch (Throwable ignored) {
        }
    }

    private void setMute(boolean muted) {
        if (player == null) {
            return;
        }
        try {
            player.mediaPlayer().audio().setMute(muted);
        } catch (Throwable ignored) {
        }
    }

    private boolean inFullscreen() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getFullScreenWindow() == this;
        } catch (Throwable t) {
            return false;
        }
    }

    private void toggleFullscreen() {
        if (inFullscreen()) {
            exitFullscreen();
            return;
        }
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            device.setFullScreenWindow(this);
        } catch (Throwable t) {
            try {
                player.mediaPlayer().fullScreen().toggle();
            } catch (Throwable ignored) {
            }
        }
    }

    private void exitFullscreen() {
        if (!inFullscreen()) {
            return;
        }
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().setFullScreenWindow(null);
        } catch (Throwable ignored) {
        }
    }

    private void bindKeys(JComponent ignored) {
        bindKey("togglePlayPause", KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), e -> togglePlayPause());
        bindKey("seekBackward", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), e -> seekBy(-5000));
        bindKey("seekForward", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), e -> seekBy(5000));
        bindKey("volumeUp", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), e -> volumeBy(5));
        bindKey("volumeDown", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), e -> volumeBy(-5));
        bindKey("mute", KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), e -> toggleMute());
        bindKey("fullscreen", KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), e -> toggleFullscreen());
        bindKey("exitFullscreen", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), e -> exitFullscreen());
    }

    private void bindKey(String name, KeyStroke key, Consumer<ActionEvent> action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.accept(e);
            }
        });
    }

    private void toggleMute() {
        mute.doClick();
    }

    private void shutdown() {
        TempFiles.delete(source);
        try {
            if (player != null) {
                player.mediaPlayer().controls().stop();
                player.release();
            }
        } catch (Throwable ignored) {
        }
        player = null;
    }

    @Override
    public void dispose() {
        shutdown();
        super.dispose();
    }

    private static String format(long millis) {
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static void configureVlcDiscovery() {
        String configured = System.getProperty("vlc.path");
        String path = findVlcPath();
        if (path == null) {
            return;
        }
        if (configured == null || configured.isBlank()) {
            System.setProperty("vlc.path", path);
        }
    }

    private static String findVlcPath() {
        String configured = System.getProperty("vlc.path");
        if (configured != null && !configured.isBlank() && libVlcPresent(configured)) {
            return configured;
        }
        List<String> candidates = new ArrayList<>();
        String pluginEnv = System.getenv("VLC_PLUGIN_PATH");
        if (pluginEnv != null && !pluginEnv.isBlank()) {
            Path parent = Paths.get(pluginEnv).toAbsolutePath().getParent();
            if (parent != null) {
                candidates.add(parent.toString());
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            candidates.add("C:\\Program Files\\VideoLAN\\VLC");
            candidates.add("C:\\Program Files (x86)\\VideoLAN\\VLC");
            candidates.add(registryValue("HKLM\\SOFTWARE\\VideoLAN\\VLC", "InstallDir"));
            candidates.add(registryValue("HKCU\\SOFTWARE\\VideoLAN\\VLC", "InstallDir"));
        } else if (os.contains("mac")) {
            candidates.add("/Applications/VLC.app/Contents/MacOS");
        } else {
            candidates.add("/usr/lib/vlc");
            candidates.add("/usr/local/lib/vlc");
            candidates.add("/snap/vlc/current");
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && libVlcPresent(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static boolean libVlcPresent(String dir) {
        if (dir == null || dir.isBlank()) {
            return false;
        }
        File vlcPath = new File(dir.trim());
        if (!vlcPath.isDirectory()) {
            return false;
        }
        return new File(vlcPath, "libvlc.dll").exists()
                || new File(vlcPath, "libvlc.dylib").exists()
                || new File(vlcPath, "libvlc.so").exists()
                || Files.isExecutable(vlcPath.toPath().resolve("vlc"));
    }

    private static String registryValue(String key, String valueName) {
        try {
            Process process = new ProcessBuilder("reg", "query", key, "/v", valueName)
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            if (process.exitValue() == 0) {
                for (String line : output.split("\\r?\\n")) {
                    int idx = line.toLowerCase().indexOf("reg_sz");
                    if (idx >= 0) {
                        String value = line.substring(idx + "reg_sz".length()).trim();
                        if (!value.isEmpty()) {
                            return value;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}