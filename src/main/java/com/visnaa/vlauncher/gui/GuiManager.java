package com.visnaa.vlauncher.gui;

import com.visnaa.vlauncher.Main;
import com.visnaa.vlauncher.minecraft.Profile;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.ImageObserver;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GuiManager
{
    private final int width, height;
    private final String title;
    private final Image icon;
    private JFrame frame;
    private JDialog loadingPopup;
    private JLabel loadingText;
    private JProgressBar loadingProgress;
    private float loadingProgressMultiplier = 1;
    private JLabel loadingProgressDone;
    private JComboBox<Profile> profile;

    public GuiManager(int width, int height, String title, Image icon)
    {
        this.width = width;
        this.height = height;
        this.title = title;
        this.icon = icon;
    }

    public void createMainWindow()
    {
        frame = new JFrame();
        frame.setSize(width, height);
        frame.setTitle(title);
        frame.setIconImage(icon);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                Main.getInstance().exit();
            }
        });
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        JPanel profilePanel = new JPanel();

        profile = new JComboBox<>(Main.getInstance().getProfiles().toArray(new Profile[0]));
        profile.setSelectedItem(Main.getInstance().getCurrentProfile());

        JButton createProfileButton = new JButton("Create a New Profile");
        createProfileButton.addActionListener(_ -> {
            createProfileCreation();
        });

        JPanel playerPanel = new JPanel();

        JLabel playerNameLabel = new JLabel("Player Name: ");

        JTextField playerName = new JTextField();
        playerName.setColumns(16);
        playerName.setText(Main.getInstance().getPlayerName());

        JPanel playPanel = new JPanel();

        JButton playButton = new JButton("PLAY");
        playButton.addActionListener(_ ->
            new Thread(() -> {
                if (Main.getInstance().getMinecraft() != null)
                {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "An instance of Minecraft is already running", "Minecraft is Running", JOptionPane.INFORMATION_MESSAGE));
                    return;
                }
                Main.getInstance().setCurrentProfile((Profile) profile.getSelectedItem());
                Main.getInstance().setPlayerName(playerName.getText());
                Main.getInstance().startGame();
            }).start());

        profilePanel.add(profile);
        profilePanel.add(createProfileButton);

        playerPanel.add(playerNameLabel);
        playerPanel.add(playerName);

        playPanel.add(playButton);

        mainPanel.add(profilePanel);
        mainPanel.add(playerPanel);
        mainPanel.add(playPanel);
        frame.add(mainPanel);
        frame.setVisible(true);

        if (Main.getInstance().getCurrentProfile() == null)
            createProfileCreation();
    }

    public JFrame getFrame()
    {
        return frame;
    }

    public void minimize()
    {
        frame.setState(Frame.ICONIFIED);
    }

    public void maximise()
    {
        frame.setState(Frame.NORMAL);
    }

    public void createProfileCreation()
    {
        SwingUtilities.invokeLater(() -> {
            JDialog profileCreator = new JDialog(frame, "Create profile", Dialog.ModalityType.APPLICATION_MODAL);
            profileCreator.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            profileCreator.setLocationRelativeTo(null);
            profileCreator.setResizable(false);

            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

            JPanel namePanel = new JPanel();

            JLabel nameLabel = new JLabel("Name: ");
            JTextField name = new JTextField();
            name.setColumns(16);

            namePanel.add(nameLabel);
            namePanel.add(name);

            JPanel versionPanel = new JPanel();

            JLabel versionLabel = new JLabel("Version: ");
            JComboBox<String> versions = new JComboBox<>(Main.getInstance().getDownloader().getVersions().toArray(new String[0]));
            versions.setSelectedItem(Main.getInstance().getDownloader().getLatestRelease());

            versionPanel.add(versionLabel);
            versionPanel.add(versions);

            JPanel minecraftPathPanel = new JPanel();

            JLabel minecraftPathLabel = new JLabel("Minecraft Path: ");
            JTextField minecraftPath = new JTextField("default");
            minecraftPath.setEditable(false);
            minecraftPath.setColumns(16);

            JButton minecraftPathButton = new JButton("Choose");
            minecraftPathButton.addActionListener(_ -> {
                JFileChooser fileChooser = new JFileChooser("");
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                    minecraftPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            });

            JButton minecraftPathDefault = new JButton("Default");
            minecraftPathDefault.addActionListener(_ -> minecraftPath.setText("default"));

            minecraftPathPanel.add(minecraftPathLabel);
            minecraftPathPanel.add(minecraftPath);
            minecraftPathPanel.add(minecraftPathButton);
            minecraftPathPanel.add(minecraftPathDefault);

            JPanel javaPanel = new JPanel();

            JLabel javaLabel = new JLabel("Java Executable: ");
            JTextField javaPath = new JTextField("bundled");
            javaPath.setEditable(false);
            javaPath.setColumns(16);

            JButton javaPathButton = new JButton("Choose");
            javaPathButton.addActionListener(_ -> {
                JFileChooser fileChooser = new JFileChooser("");
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setAcceptAllFileFilterUsed(false);
                fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Executables (*.exe)", "exe"));
                if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                    javaPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            });

            JButton javaPathDefault = new JButton("Default");
            javaPathDefault.addActionListener(_ -> javaPath.setText("bundled"));

            javaPanel.add(javaLabel);
            javaPanel.add(javaPath);
            javaPanel.add(javaPathButton);
            javaPanel.add(javaPathDefault);

            JPanel jvmPanel = new JPanel();
            jvmPanel.setLayout(new GridLayout(0, 2, 10, 0));

            List<JTextField> args = new ArrayList<>();
            JLabel jvmLabel = new JLabel("Custom JVM Arguments: ");
            JButton jvmAddButton = new JButton("Add");
            jvmAddButton.addActionListener(_ ->
                SwingUtilities.invokeLater(() -> {
                    JTextField jvmArg = new JTextField();
                    jvmArg.setColumns(16);

                    JButton jvmRemove = new JButton("Remove");
                    jvmRemove.addActionListener(_ ->
                        SwingUtilities.invokeLater(() -> {
                            args.remove(jvmArg);
                            jvmPanel.remove(jvmArg);
                            jvmPanel.remove(jvmRemove);
                            profileCreator.pack();
                        }));

                    args.add(jvmArg);
                    jvmPanel.add(jvmArg);
                    jvmPanel.add(jvmRemove);
                    profileCreator.pack();
                }));

            jvmPanel.add(jvmLabel);
            jvmPanel.add(jvmAddButton);

            JPanel createPanel = new JPanel();
            JButton createButton = new JButton("Create Profile");
            createButton.addActionListener(_ -> createProfile(profileCreator, name.getText(), (String) versions.getSelectedItem(), minecraftPath.getText().equals("default") ? null :  Path.of(minecraftPath.getText()), javaPath.getText().isEmpty() || javaPath.getText().equals("bundled") ? null : new File(javaPath.getText()), args.stream().map(JTextComponent::getText).toList()));
            createPanel.add(createButton);

            panel.add(namePanel);
            panel.add(versionPanel);
            panel.add(minecraftPathPanel);
            panel.add(javaPanel);
            panel.add(jvmPanel);
            panel.add(createPanel);

            profileCreator.add(panel);
            profileCreator.pack();
            profileCreator.setVisible(true);
        });
    }

    private void createProfile(JDialog profileCreator, String name, String version, Path minecraftPath, File java, List<String> jvmArgs)
    {
        if (name == null || name.isEmpty())
        {
            JOptionPane.showMessageDialog(profileCreator, "You have to specify the name of the profile", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (version == null || version.isEmpty())
        {
            JOptionPane.showMessageDialog(profileCreator, "Invalid version: " + version, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Path mcPath;
        if (minecraftPath == null)
        {
            mcPath = Main.getInstance().getDownloader().getRootDirectory();
        }
        else if (!minecraftPath.toFile().exists())
        {
            JOptionPane.showMessageDialog(profileCreator, "Specified Minecraft path does not exist", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        else if (!minecraftPath.toFile().isDirectory())
        {
            JOptionPane.showMessageDialog(profileCreator, "Specified Minecraft path is not a directory", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        else
        {
            mcPath = minecraftPath;
        }

        if (java == null)
        {
            Main.getInstance().createProfile(profileCreator, new Profile(name, version, mcPath, null, jvmArgs));
            return;
        }
        else if (!java.exists() || !java.isFile())
        {
            JOptionPane.showMessageDialog(profileCreator, "Specified Java executable is not a valid file", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Main.getInstance().createProfile(profileCreator, new Profile(name, version, mcPath, java, jvmArgs));
    }

    public void createLoadingPopup(long totalSize)
    {
        SwingUtilities.invokeLater(() -> {
            loadingPopup = new JDialog(frame, "Starting Minecraft...", Dialog.ModalityType.APPLICATION_MODAL);
            loadingPopup.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new GridLayout(0, 1));

            JPanel textPanel = new JPanel();
            loadingText = new JLabel("Launching Minecraft...");
            textPanel.add(loadingText);

            JPanel progressPanel = new JPanel();
            progressPanel.setLayout(new GridLayout(1, 3));
            if (totalSize >= Integer.MAX_VALUE)
            {
                for (long i = 2; i < Long.MAX_VALUE; i++)
                {
                    if (totalSize / i > Integer.MAX_VALUE)
                        continue;

                    loadingProgress = new JProgressBar(0, (int) (totalSize / i));
                    loadingProgressMultiplier = 1F / i;
                }
            }
            else
                loadingProgress = new JProgressBar(0, (int) totalSize);

            loadingProgressDone = new JLabel("0 MB", SwingConstants.RIGHT);
            JLabel loadingProgressSize = new JLabel(String.format("%.2f", totalSize / 1_048_576F) + " MB", SwingConstants.LEFT);

            progressPanel.add(loadingProgressDone);
            progressPanel.add(loadingProgress);
            progressPanel.add(loadingProgressSize);

            mainPanel.add(textPanel);
            mainPanel.add(progressPanel);

            loadingPopup.add(mainPanel);
            loadingPopup.setSize(600, 100);
            loadingPopup.setLocationRelativeTo(null);
            loadingPopup.setVisible(true);
        });
    }

    public void setCurrentProfile(Profile profile, List<Profile> profiles)
    {
        SwingUtilities.invokeLater(() -> {
            this.profile.setModel(new DefaultComboBoxModel<>(profiles.toArray(new Profile[0])));
            this.profile.setSelectedItem(profile);
        });
    }

    public void setLoadingText(String text)
    {
        SwingUtilities.invokeLater(() -> loadingText.setText(text));
    }

    public void progressLoading(int size)
    {
        SwingUtilities.invokeLater(() -> {
            loadingProgress.setValue((int) (loadingProgress.getValue() + (size * loadingProgressMultiplier)));
            loadingProgressDone.setText(String.format("%.2f", loadingProgress.getValue() / 1_048_576F) + " MB");
        });
    }

    public void disposeLoadingPopup()
    {
        SwingUtilities.invokeLater(() -> {
            loadingPopup.dispose();
            frame.remove(loadingPopup);
        });
    }
}
