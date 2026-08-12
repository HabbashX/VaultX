package com.habbashx.vaultx.ui.viewer;

import com.habbashx.vaultx.core.TempFiles;
import com.habbashx.vaultx.core.VaultItem;
import com.habbashx.vaultx.core.VaultManager;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.State;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public final class MediaPlayerFrame extends JFrame {

    private final Path source;
    private EmbeddedMediaPlayerComponent player;
    private final JToggleButton playPause = new JToggleButton("Play");
    private final JButton stop = new JButton("Stop");
    private final JSlider seek = new JSlider(0, 1000, 0);
    private final JSlider volume = new JSlider(0, 100, 80);
    private final JLabel time = new JLabel("00:00 / 00:00");
    private final JLabel notice = new JLabel(" ");
    private boolean seeking;
    private long lengthMillis;

    public MediaPlayerFrame(VaultItem item, VaultManager manager, Path source) {
        super(item.name + " — Media Player");
        this.source = source;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        try {
            player = new EmbeddedMediaPlayerComponent();
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(null,
                    "VLC media player was not found.\n\n"
                            + "Install the 64-bit VLC 3.x from https://www.videolan.org/vlc/ and restart the app.\n"
                            + "Details: " + t.getMessage(),
                    "Media player", JOptionPane.ERROR_MESSAGE);
            TempFiles.delete(source);
            dispose();
            return;
        }

        playPause.addActionListener(e -> togglePlayPause());
        stop.addActionListener(e -> player.mediaPlayer().controls().stop());
        JButton fullscreen = new JButton("Fullscreen");
        fullscreen.addActionListener(e -> {
            try {
                player.mediaPlayer().fullScreen().toggle();
            } catch (Throwable ignored) {
            }
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        seek.setToolTipText("Seek");
        seek.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                seeking = true;
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                seeking = false;
            }
        });
        seek.addChangeListener(e -> {
            if (seeking && player != null) {
                player.mediaPlayer().controls().setPosition(seek.getValue() / 1000f);
            }
        });

        volume.setToolTipText("Volume");
        volume.addChangeListener(e -> {
            if (player != null && volume.hasFocus()) {
                try {
                    player.mediaPlayer().audio().setVolume(volume.getValue());
                } catch (Throwable ignored) {
                }
            }
        });

        JToolBar controls = new JToolBar();
        controls.setFloatable(false);
        controls.add(playPause);
        controls.add(stop);
        controls.addSeparator(new Dimension(8, 0));
        controls.add(seek);
        controls.addSeparator(new Dimension(8, 0));
        controls.add(new JLabel("Vol:"));
        controls.add(volume);
        controls.addSeparator(new Dimension(24, 0));
        controls.add(fullscreen);
        controls.addSeparator(new Dimension(24, 0));
        controls.add(close);

        JPanel south = new JPanel(new BorderLayout());
        south.add(time, BorderLayout.NORTH);
        south.add(notice, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        content.add(player, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);
        content.add(south, BorderLayout.NORTH);

        setContentPane(content);
        addWindowListener(new WindowAdapter() {
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
                        seek.setValue(Math.max(0, Math.min(1000, (int) (newPosition * 1000f))));
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
                SwingUtilities.invokeLater(() -> notice.setText("Playback error. The codec may not be supported by VLC."));
            }
        });

        setVolume(volume.getValue());
        player.mediaPlayer().media().play(source.toString());
        notice.setText("Loading…");
    }

    private void togglePlayPause() {
        if (player == null) {
            return;
        }
        State state = player.mediaPlayer().status().state();
        if (state == State.PLAYING) {
            player.mediaPlayer().controls().setPause(true);
        } else if (state == State.PAUSED) {
            player.mediaPlayer().controls().setPause(false);
        } else {
            player.mediaPlayer().media().play(source.toString());
        }
    }

    private void setVolume(int value) {
        try {
            player.mediaPlayer().audio().setVolume(value);
        } catch (Throwable ignored) {
        }
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
}