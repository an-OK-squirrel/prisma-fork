package com.puzzletimer;

import info.clearthought.layout.TableLayout;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.DataLine.Info;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.puzzletimer.database.CategoryDAO;
import com.puzzletimer.database.ColorDAO;
import com.puzzletimer.database.ConfigurationDAO;
import com.puzzletimer.database.SolutionDAO;
import com.puzzletimer.graphics.Panel3D;
import com.puzzletimer.models.Category;
import com.puzzletimer.models.ColorScheme;
import com.puzzletimer.models.ConfigurationEntry;
import com.puzzletimer.models.Scramble;
import com.puzzletimer.models.Solution;
import com.puzzletimer.models.Timing;
import com.puzzletimer.puzzles.Puzzle;
import com.puzzletimer.puzzles.PuzzleProvider;
import com.puzzletimer.scramblers.Scrambler;
import com.puzzletimer.scramblers.ScramblerProvider;
import com.puzzletimer.state.CategoryListener;
import com.puzzletimer.state.CategoryManager;
import com.puzzletimer.state.ColorListener;
import com.puzzletimer.state.ColorManager;
import com.puzzletimer.state.ConfigurationListener;
import com.puzzletimer.state.ConfigurationManager;
import com.puzzletimer.state.ScrambleListener;
import com.puzzletimer.state.ScrambleManager;
import com.puzzletimer.state.SessionListener;
import com.puzzletimer.state.SessionManager;
import com.puzzletimer.state.SolutionListener;
import com.puzzletimer.state.SolutionManager;
import com.puzzletimer.state.TimerListener;
import com.puzzletimer.state.TimerManager;
import com.puzzletimer.statistics.Average;
import com.puzzletimer.statistics.Best;
import com.puzzletimer.statistics.Mean;
import com.puzzletimer.statistics.StandardDeviation;
import com.puzzletimer.statistics.StatisticalMeasure;
import com.puzzletimer.timer.KeyboardTimer;
import com.puzzletimer.timer.StackmatTimer;
import com.puzzletimer.timer.Timer;
import com.puzzletimer.tips.TipperProvider;
import com.puzzletimer.util.SolutionUtils;

@SuppressWarnings("serial")
public class Main extends JFrame {
    private Panel3D panel3D;

    private TipsFrame tipsFrame;
    private ScrambleQueueFrame scrambleQueueFrame;
    private HistoryFrame historyFrame;
    private SessionSummaryFrame sessionSummaryFrame;
    private CategoryManagerFrame categoryManagerDialog;
    private ColorSchemeFrame colorSchemeFrame;

    private PuzzleProvider puzzleProvider;
    private TipperProvider tipperProvider;
    private ScramblerProvider scramblerProvider;
    private ConfigurationManager configurationManager;
    private TimerManager timerManager;
    private CategoryManager categoryManager;
    private ScrambleManager scrambleManager;
    private SolutionManager solutionManager;
    private SessionManager sessionManager;
    private ColorManager colorManager;

    private AudioFormat audioFormat;
    private Mixer.Info mixerInfo;

    private boolean timerStopped;

    public Main() {
        // make empty database if necessary
        try {
            File databaseFile = new File("puzzletimer.h2.db");
            if (!databaseFile.exists()) {
                BufferedInputStream input = new BufferedInputStream(getClass().getResourceAsStream("/com/puzzletimer/resources/puzzletimer.h2.db"));
                FileOutputStream output = new FileOutputStream("puzzletimer.h2.db");

                for (;;) {
                    int data = input.read();
                    if (data < 0) {
                        break;
                    }

                    output.write(data);
                }

                input.close();
                output.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
            // TODO: show error message and quit
        }

        // connect to database
        Connection connection = null;
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection("jdbc:h2:puzzletimer;IFEXISTS=TRUE", "sa", "");
        } catch (Exception e) {
            e.printStackTrace();
            return;
            // TODO: show error message and quit
        }

        // configuration DAO
        final ConfigurationDAO configurationDAO = new ConfigurationDAO(connection);

        // color DAO
        final ColorDAO colorDAO = new ColorDAO(connection);

        // category DAO
        final CategoryDAO categoryDAO = new CategoryDAO(connection);

        // solution DAO
        final SolutionDAO solutionDAO = new SolutionDAO(connection);


        // puzzle provider
        this.puzzleProvider = new PuzzleProvider();

        // tipper provider
        this.tipperProvider = new TipperProvider();

        // scrambler provider
        this.scramblerProvider = new ScramblerProvider();

        // configuration manager
        ConfigurationEntry[] configurationEntries;
        try {
            configurationEntries = configurationDAO.getAll();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
            // TODO: show error message and quit
        }

        this.configurationManager = new ConfigurationManager(configurationEntries);
        this.configurationManager.addConfigurationListener(new ConfigurationListener() {
            @Override
            public void configurationEntryUpdated(ConfigurationEntry entry) {
                try {
                    configurationDAO.update(entry);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }
        });

        // stackmat timer input
        this.audioFormat = new AudioFormat(8000, 8, 1, true, false);
        this.mixerInfo = null;

        // timer manager
        this.timerManager = new TimerManager();
        this.timerManager.addTimerListener(new TimerListener() {
            @Override
            public void timerChanged(Timer timer) {
                Main.this.configurationManager.setConfigurationEntry(
                    new ConfigurationEntry("TIMER-TRIGGER", timer.getTimerId()));
            }
        });

        // retrieve stackmat timer input device configuration
        ConfigurationEntry stackmatTimerInputDeviceEntry =
            this.configurationManager.getConfigurationEntry("STACKMAT-TIMER-INPUT-DEVICE");
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            if (stackmatTimerInputDeviceEntry.getValue().equals(mixerInfo.getName())) {
                this.mixerInfo = mixerInfo;
                break;
            }
        }

        // retrieve timer trigger configuration
        ConfigurationEntry timerTriggerEntry =
            this.configurationManager.getConfigurationEntry("TIMER-TRIGGER");
        Timer timer = null;
        if (timerTriggerEntry.getValue().equals("KEYBOARD-TIMER-CONTROL")) {
            timer = new KeyboardTimer(this.timerManager, this, KeyEvent.VK_CONTROL);
        } else if (timerTriggerEntry.getValue().equals("KEYBOARD-TIMER-SPACE")) {
            timer = new KeyboardTimer(this.timerManager, this, KeyEvent.VK_SPACE);
        } else if (timerTriggerEntry.getValue().equals("STACKMAT-TIMER")) {
            if (this.mixerInfo != null) {
                TargetDataLine targetDataLine = null;
                try {
                    targetDataLine = AudioSystem.getTargetDataLine(Main.this.audioFormat, Main.this.mixerInfo);
                    targetDataLine.open(Main.this.audioFormat);
                    timer = new StackmatTimer(this.timerManager, targetDataLine);
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                    // TODO: show error message?
                }

                timer = new StackmatTimer(this.timerManager, targetDataLine);
            } else {
                timer = new KeyboardTimer(this.timerManager, this, KeyEvent.VK_SPACE);
            }
        }

        this.timerManager.setTimer(timer);
        Main.this.configurationManager.setConfigurationEntry(
            new ConfigurationEntry("TIMER-TRIGGER", timer.getTimerId()));

        // categoryManager
        Category[] categories;
        try {
            categories = categoryDAO.getAll();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
            // TODO: show error message and quit
        }

        UUID currentCategoryId = UUID.fromString(
            this.configurationManager.getConfigurationEntry("CURRENT-CATEGORY").getValue());
        Category defaultCategory = null;
        for (Category category : categories) {
            if (category.getCategoryId().equals(currentCategoryId)) {
                defaultCategory = category;
                break;
            }
        }

        this.categoryManager = new CategoryManager(categories, defaultCategory);
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void currentCategoryChanged(Category category) {
                ConfigurationEntry entry =
                    Main.this.configurationManager.getConfigurationEntry("CURRENT-CATEGORY");
                entry.setValue(category.getCategoryId().toString());
                Main.this.configurationManager.setConfigurationEntry(entry);
            }

            @Override
            public void categoryAdded(Category category) {
                try {
                    categoryDAO.insert(category);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }

            @Override
            public void categoryRemoved(Category category) {
                try {
                    categoryDAO.delete(category);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }

            @Override
            public void categoryUpdated(Category category) {
                try {
                    categoryDAO.update(category);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }
        });

        // scramble manager
        this.scrambleManager = new ScrambleManager(
            this.scramblerProvider,
            this.scramblerProvider.get(defaultCategory.scramblerId));
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void currentCategoryChanged(Category category) {
                Main.this.scrambleManager.setCategory(category);
            }
        });

        // solution manager
        this.solutionManager = new SolutionManager();
        this.solutionManager.addSolutionListener(new SolutionListener() {
            @Override
            public void solutionAdded(Solution solution) {
                try {
                    solutionDAO.insert(solution);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }

            @Override
            public void solutionUpdated(Solution solution) {
                try {
                    solutionDAO.update(solution);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }

            @Override
            public void solutionRemoved(Solution solution) {
                try {
                    solutionDAO.delete(solution);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }
        });
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void currentCategoryChanged(Category category) {
                Solution[] solutions = null;
                try {
                    solutions = solutionDAO.getAll(category);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
                Main.this.solutionManager.loadSolutions(solutions);
            }
        });
        this.timerManager.addTimerListener(new TimerListener() {
            @Override
            public void timerReady() {
                Main.this.timerStopped = false;
            }

            @Override
            public void timerStopped(Timing timing) {
                if (!Main.this.timerStopped) {
                    Main.this.solutionManager.addSolution(
                        new Solution(
                            UUID.randomUUID(),
                            Main.this.categoryManager.getCurrentCategory().getCategoryId(),
                            Main.this.scrambleManager.getCurrentScramble(),
                            timing,
                            ""));
                    Main.this.scrambleManager.changeScramble();
                }

                Main.this.timerStopped = true;
            }
        });

        // session manager
        this.sessionManager = new SessionManager();
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void currentCategoryChanged(Category category) {
                Main.this.sessionManager.clearSession();
            }
        });
        this.solutionManager.addSolutionListener(new SolutionListener() {
            @Override
            public void solutionAdded(Solution solution) {
                Main.this.sessionManager.addSolution(solution);
            }

            @Override
            public void solutionRemoved(Solution solution) {
                Main.this.sessionManager.removeSolution(solution);
            }

            @Override
            public void solutionsUpdated(Solution[] solutions) {
                Main.this.sessionManager.notifyListeners();
            }
        });

        // color manager
        ColorScheme[] colorSchemes;
        try {
            colorSchemes = colorDAO.getAll();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        this.colorManager = new ColorManager(colorSchemes);
        this.colorManager.addColorListener(new ColorListener() {
            @Override
            public void colorSchemeUpdated(ColorScheme colorScheme) {
                try {
                    colorDAO.update(colorScheme);
                } catch (SQLException e) {
                    e.printStackTrace();
                    // TODO: show error message
                }
            }
        });

        createComponents();

        // update screen
        this.categoryManager.setCurrentCategory(defaultCategory);
    }

    private void createComponents() {
        Image icon = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/com/puzzletimer/resources/icon.png"));

        // frameMain
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        setIconImage(icon);
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void categoriesUpdated(Category[] categories, Category currentCategory) {
                setTitle("Puzzle Timer - " + currentCategory.description);
            }
        });

        // menu
        setJMenuBar(createMenuBar());

        // panelMain
        JPanel panelMain = new JPanel(new TableLayout(new double[][] {
            { 3, 0.3, 0.4, 0.3, 3 },
            { 20, 0.5, TableLayout.PREFERRED, 0.5, TableLayout.PREFERRED, 3 },
        }));
        add(panelMain);

        // panelScramble
        final JPanel panelScramble = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 3));
        panelScramble.setPreferredSize(new Dimension(10000, 150));
        this.scrambleManager.addScrambleListener(new ScrambleListener() {
            @Override
            public void scrambleChanged(Scramble scramble) {
                panelScramble.removeAll();

                final String[] sequence = scramble.getSequence();
                for (int i = 0; i < sequence.length; i++) {
                    JLabel label = new JLabel(sequence[i]);
                    label.setFont(new Font("Arial", Font.PLAIN, 18));
                    label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    final int length = i + 1;
                    label.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            String[] partialSequence = new String[length];
                            for (int i = 0; i < length; i++) {
                                partialSequence[i] = sequence[i];
                            }

                            Main.this.updateScrambleViewer(partialSequence);
                        }
                    });
                    panelScramble.add(label);
                }

                panelScramble.revalidate();
                panelScramble.repaint();
            }
        });
        panelMain.add(panelScramble, "1, 1, 3, 1");

        // timer panel
        panelMain.add(createTimerPanel(), "1, 2, 3, 2");

        // times panel
        panelMain.add(createTimesPanel(), "1, 4");

        // statistics panel
        panelMain.add(createStatisticsPanel(), "2, 4");

        // scramble panel
        panelMain.add(createScramblePanel(), "3, 4");

        // tips frame
        this.tipsFrame = new TipsFrame(
            this.puzzleProvider,
            this.tipperProvider,
            this.scramblerProvider,
            this.categoryManager,
            this.scrambleManager);
        this.tipsFrame.setLocationRelativeTo(null);
        this.tipsFrame.setIconImage(icon);

        // scramble queue frame
        this.scrambleQueueFrame = new ScrambleQueueFrame(
            this.scramblerProvider,
            this.categoryManager,
            this.scrambleManager);
        this.scrambleQueueFrame.setLocationRelativeTo(null);
        this.scrambleQueueFrame.setIconImage(icon);

        // history frame
        this.historyFrame = new HistoryFrame(
            this.categoryManager,
            this.solutionManager);
        this.historyFrame.setLocationRelativeTo(null);
        this.historyFrame.setIconImage(icon);

        // session summary frame
        this.sessionSummaryFrame = new SessionSummaryFrame(
            this.categoryManager,
            this.sessionManager);
        this.sessionSummaryFrame.setLocationRelativeTo(null);
        this.sessionSummaryFrame.setIconImage(icon);

        // category manager dialog
        this.categoryManagerDialog = new CategoryManagerFrame(
            this.puzzleProvider,
            this.scramblerProvider,
            this.categoryManager);
        this.categoryManagerDialog.setLocationRelativeTo(null);
        this.categoryManagerDialog.setIconImage(icon);

        // color scheme frame
        this.colorSchemeFrame = new ColorSchemeFrame(
            this.puzzleProvider,
            this.colorManager);
        this.colorSchemeFrame.setLocationRelativeTo(null);
        this.colorSchemeFrame.setIconImage(icon);
    }

    private JMenuBar createMenuBar() {
        // menuBar
        JMenuBar menuBar = new JMenuBar();

        // menuFile
        final JMenu menuFile = new JMenu("File");
        menuFile.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menuFile);

        // menuItemExit
        final JMenuItem menuItemExit = new JMenuItem("Exit");
        menuItemExit.setMnemonic(KeyEvent.VK_X);
        menuItemExit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        menuFile.add(menuItemExit);

        // menuView
        JMenu menuView = new JMenu("View");
        menuView.setMnemonic(KeyEvent.VK_V);
        menuBar.add(menuView);

        // menuItemTips
        JMenuItem menuItemTips = new JMenuItem("Tips...");
        menuItemTips.setMnemonic(KeyEvent.VK_T);
        menuItemTips.setAccelerator(KeyStroke.getKeyStroke("ctrl alt T"));
        menuItemTips.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.tipsFrame.setVisible(true);
            }
        });
        menuView.add(menuItemTips);

        // menuItemScrambleQueue
        JMenuItem menuItemScrambleQueue = new JMenuItem("Scramble queue...");
        menuItemScrambleQueue.setMnemonic(KeyEvent.VK_Q);
        menuItemScrambleQueue.setAccelerator(KeyStroke.getKeyStroke("ctrl alt Q"));
        menuItemScrambleQueue.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.scrambleQueueFrame.setVisible(true);
            }
        });
        menuView.add(menuItemScrambleQueue);

        // menuItemHistory
        JMenuItem menuItemHistory = new JMenuItem("History...");
        menuItemHistory.setMnemonic(KeyEvent.VK_H);
        menuItemHistory.setAccelerator(KeyStroke.getKeyStroke("ctrl alt H"));
        menuItemHistory.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.historyFrame.setVisible(true);
            }
        });
        menuView.add(menuItemHistory);

        // menuItemSessionSummary
        JMenuItem menuItemSessionSummary = new JMenuItem("Session summary...");
        menuItemSessionSummary.setMnemonic(KeyEvent.VK_S);
        menuItemSessionSummary.setAccelerator(KeyStroke.getKeyStroke("ctrl alt S"));
        menuItemSessionSummary.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.sessionSummaryFrame.setVisible(true);
            }
        });
        menuView.add(menuItemSessionSummary);

        // menuCategory
        final JMenu menuCategory = new JMenu("Category");
        menuCategory.setMnemonic(KeyEvent.VK_C);
        menuBar.add(menuCategory);
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void categoriesUpdated(Category[] categories, Category currentCategory) {
                menuCategory.removeAll();

                // category manager
                JMenuItem menuItemCategoryManager = new JMenuItem("Category manager...");
                menuItemCategoryManager.setMnemonic(KeyEvent.VK_M);
                menuItemCategoryManager.setAccelerator(KeyStroke.getKeyStroke("ctrl alt C"));
                menuItemCategoryManager.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        Main.this.categoryManagerDialog.setVisible(true);
                    }
                });
                menuCategory.add(menuItemCategoryManager);

                menuCategory.addSeparator();

                ButtonGroup categoryGroup = new ButtonGroup();

                // built-in categories
                class BuiltInCategory {
                    public Category category;
                    public char mnemonic;
                    public char accelerator;

                    public BuiltInCategory(Category category, char mnemonic, char accelerator) {
                        this.category = category;
                        this.mnemonic = mnemonic;
                        this.accelerator = accelerator;
                    }
                }

                // TODO: find a better way to do this
                BuiltInCategory[] builtInCategories = {
                    new BuiltInCategory(categories[0], '2', '2'),
                    new BuiltInCategory(categories[1], 'R', '3'),
                    new BuiltInCategory(categories[2], 'O', '\0'),
                    new BuiltInCategory(categories[3], 'B', '\0'),
                    new BuiltInCategory(categories[4], 'F', '\0'),
                    new BuiltInCategory(categories[5], '4', '4'),
                    new BuiltInCategory(categories[6], 'B', '\0'),
                    new BuiltInCategory(categories[7], '5', '5'),
                    new BuiltInCategory(categories[8], 'B', '\0'),
                    new BuiltInCategory(categories[9], '6', '6'),
                    new BuiltInCategory(categories[10], '7', '7'),
                    new BuiltInCategory(categories[11], 'C', 'K'),
                    new BuiltInCategory(categories[12], 'M', 'M'),
                    new BuiltInCategory(categories[13], 'P', 'P'),
                    new BuiltInCategory(categories[14], 'S', '1'),
                    new BuiltInCategory(categories[15], 'M', 'G'),
                    new BuiltInCategory(categories[16], 'M', 'A'),
                };

                for (final BuiltInCategory builtInCategory : builtInCategories) {
                    JRadioButtonMenuItem menuItemCategory = new JRadioButtonMenuItem(builtInCategory.category.description);
                    menuItemCategory.setMnemonic(builtInCategory.mnemonic);
                    if (builtInCategory.accelerator != '\0') {
                        menuItemCategory.setAccelerator(KeyStroke.getKeyStroke(builtInCategory.accelerator, InputEvent.CTRL_MASK));
                    }
                    menuItemCategory.setSelected(builtInCategory.category == currentCategory);
                    menuItemCategory.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            Main.this.categoryManager.setCurrentCategory(builtInCategory.category);
                        }
                    });
                    menuCategory.add(menuItemCategory);
                    categoryGroup.add(menuItemCategory);
                }

                menuCategory.addSeparator();

                // user defined categories
                for (final Category category : categories) {
                    if (category.isUserDefined()) {
                        JRadioButtonMenuItem menuItemCategory = new JRadioButtonMenuItem(category.description);
                        menuItemCategory.setSelected(category == currentCategory);
                        menuItemCategory.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                Main.this.categoryManager.setCurrentCategory(category);
                            }
                        });
                        menuCategory.add(menuItemCategory);
                        categoryGroup.add(menuItemCategory);
                    }
                }
            }
        });

        // menuOptions
        final JMenu menuOptions = new JMenu("Options");
        menuOptions.setMnemonic(KeyEvent.VK_O);
        menuBar.add(menuOptions);

        // menuColorScheme
        JMenuItem menuColorScheme = new JMenuItem("Color scheme...");
        menuColorScheme.setMnemonic(KeyEvent.VK_C);
        menuColorScheme.setAccelerator(KeyStroke.getKeyStroke("ctrl alt K"));
        menuColorScheme.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.colorSchemeFrame.setVisible(true);
            }
        });
        menuOptions.add(menuColorScheme);

        // menuTimerTrigger
        final JMenu menuTimerTrigger = new JMenu("Timer trigger");
        menuTimerTrigger.setMnemonic(KeyEvent.VK_T);
        menuOptions.add(menuTimerTrigger);
        ButtonGroup timerTriggerGroup = new ButtonGroup();

        ConfigurationEntry timerTriggerEntry =
            this.configurationManager.getConfigurationEntry("TIMER-TRIGGER");

        // menuItemCtrlKeys
        final JRadioButtonMenuItem menuItemCtrlKeys = new JRadioButtonMenuItem("Ctrl keys");
        menuItemCtrlKeys.setMnemonic(KeyEvent.VK_C);
        menuItemCtrlKeys.setAccelerator(KeyStroke.getKeyStroke("ctrl C"));
        menuItemCtrlKeys.setSelected(timerTriggerEntry.getValue().equals("KEYBOARD-TIMER-CONTROL"));
        menuItemCtrlKeys.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.timerStopped = true;
                Timer timer = new KeyboardTimer(Main.this.timerManager, Main.this, KeyEvent.VK_CONTROL);
                Main.this.timerManager.setTimer(timer);
            }
        });
        menuTimerTrigger.add(menuItemCtrlKeys);
        timerTriggerGroup.add(menuItemCtrlKeys);

        // menuItemSpaceKey
        final JRadioButtonMenuItem menuItemSpaceKey = new JRadioButtonMenuItem("Space key");
        menuItemSpaceKey.setMnemonic(KeyEvent.VK_S);
        menuItemSpaceKey.setAccelerator(KeyStroke.getKeyStroke(' ', InputEvent.CTRL_MASK));
        menuItemSpaceKey.setSelected(timerTriggerEntry.getValue().equals("KEYBOARD-TIMER-SPACE"));
        menuItemSpaceKey.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.timerStopped = true;
                Timer timer = new KeyboardTimer(Main.this.timerManager, Main.this, KeyEvent.VK_SPACE);
                Main.this.timerManager.setTimer(timer);
            }
        });
        menuTimerTrigger.add(menuItemSpaceKey);
        timerTriggerGroup.add(menuItemSpaceKey);

        // menuItemStackmatTimer
        final JRadioButtonMenuItem menuItemStackmatTimer = new JRadioButtonMenuItem("Stackmat timer");
        menuItemStackmatTimer.setMnemonic(KeyEvent.VK_T);
        menuItemStackmatTimer.setAccelerator(KeyStroke.getKeyStroke("ctrl T"));
        menuItemStackmatTimer.setSelected(timerTriggerEntry.getValue().equals("STACKMAT-TIMER"));
        menuItemStackmatTimer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.this.timerStopped = true;

                if (Main.this.mixerInfo == null) {
                    Main.this.timerManager.setTimer(null);
                    return;
                }

                TargetDataLine targetDataLine;
                try {
                    targetDataLine = AudioSystem.getTargetDataLine(Main.this.audioFormat, Main.this.mixerInfo);
                    targetDataLine.open(Main.this.audioFormat);
                } catch (LineUnavailableException ex) {
                    // select the default timer
                    menuItemSpaceKey.setSelected(true);
                    Timer defaultTimer = new KeyboardTimer(Main.this.timerManager, Main.this, KeyEvent.VK_SPACE);
                    Main.this.timerManager.setTimer(defaultTimer);
                    return;
                }

                Timer timer = new StackmatTimer(Main.this.timerManager, targetDataLine);
                Main.this.timerManager.setTimer(timer);
            }
        });
        menuTimerTrigger.add(menuItemStackmatTimer);
        timerTriggerGroup.add(menuItemStackmatTimer);

        // menuStackmatTimerInputDevice
        JMenu stackmatTimerInputDevice = new JMenu("Stackmat timer input device");
        menuTimerTrigger.setMnemonic(KeyEvent.VK_S);
        menuOptions.add(stackmatTimerInputDevice);
        ButtonGroup stackmatTimerInputDeviceGroup = new ButtonGroup();

        // menuItemDevice
        ConfigurationEntry stackmatTimerInputDeviceEntry =
            this.configurationManager.getConfigurationEntry("STACKMAT-TIMER-INPUT-DEVICE");

        for (final Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Line.Info[] targetLinesInfo =
                AudioSystem.getTargetLineInfo(new Info(TargetDataLine.class, this.audioFormat));

            boolean validMixer = false;
            for (Line.Info lineInfo : targetLinesInfo) {
                if (AudioSystem.getMixer(mixerInfo).isLineSupported(lineInfo)) {
                    validMixer = true;
                    break;
                }
            }

            if (!validMixer) {
                continue;
            }

            JRadioButtonMenuItem menuItemDevice = new JRadioButtonMenuItem(mixerInfo.getName());
            menuItemDevice.setSelected(stackmatTimerInputDeviceEntry.getValue().equals(mixerInfo.getName()));
            menuItemDevice.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    Main.this.mixerInfo = mixerInfo;
                    Main.this.configurationManager.setConfigurationEntry(
                        new ConfigurationEntry("STACKMAT-TIMER-INPUT-DEVICE", mixerInfo.getName()));
                }
            });
            stackmatTimerInputDevice.add(menuItemDevice);
            stackmatTimerInputDeviceGroup.add(menuItemDevice);

            if (Main.this.mixerInfo == null) {
                Main.this.mixerInfo = mixerInfo;
                this.configurationManager.setConfigurationEntry(
                    new ConfigurationEntry("STACKMAT-TIMER-INPUT-DEVICE", mixerInfo.getName()));
            }
        }

        //menuHelp
        final JMenu menuHelp = new JMenu("Help");
        menuHelp.setMnemonic(KeyEvent.VK_H);
        menuBar.add(menuHelp);

        // menuItemAbout
        final JMenuItem menuItemAbout = new JMenuItem("About...");
        menuItemAbout.setMnemonic(KeyEvent.VK_A);
        menuItemAbout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AboutDialog aboutDialog = new AboutDialog(Main.this, true);
                aboutDialog.setLocationRelativeTo(null);
                aboutDialog.setVisible(true);
            }
        });
        menuHelp.add(menuItemAbout);

        return menuBar;
    }

    private JPanel createTimerPanel() {
        // panelTime
        JPanel panelTimer = new JPanel(new TableLayout(new double[][] {
            { 0.35, TableLayout.PREFERRED, 0.15, TableLayout.PREFERRED, 0.15, TableLayout.PREFERRED, 0.35 },
            { TableLayout.FILL },
        }));

        // labelLeftHand
        final ImageIcon iconLeft = new ImageIcon(getClass().getResource("/com/puzzletimer/resources/left.png"));
        final ImageIcon iconLeftPressed = new ImageIcon(getClass().getResource("/com/puzzletimer/resources/leftPressed.png"));

        final JLabel labelLeftHand = new JLabel(iconLeft);
        this.timerManager.addTimerListener(new TimerListener() {
            @Override
            public void leftHandPressed() {
                labelLeftHand.setIcon(iconLeftPressed);
            }

            @Override
            public void leftHandReleased() {
                labelLeftHand.setIcon(iconLeft);
            }
        });
        panelTimer.add(labelLeftHand, "1, 0");

        // labelTime
        final JLabel labelTime = new JLabel("00:00.00");
        labelTime.setFont(new Font("Arial", Font.BOLD, 108));
        this.timerManager.addTimerListener(new TimerListener() {
            @Override
            public void timerRunning(Timing timing) {
                if (!Main.this.timerStopped) {
                    labelTime.setText(SolutionUtils.formatMinutes(timing.getElapsedTime()));
                }
            }

            @Override
            public void timerStopped(Timing timing) {
                labelTime.setText(SolutionUtils.formatMinutes(timing.getElapsedTime()));
            }
        });
        panelTimer.add(labelTime, "3, 0");

        // labelRightHand
        final ImageIcon iconRight = new ImageIcon(getClass().getResource("/com/puzzletimer/resources/right.png"));
        final ImageIcon iconRightPressed = new ImageIcon(getClass().getResource("/com/puzzletimer/resources/rightPressed.png"));

        final JLabel labelRightHand = new JLabel(iconRight);
        this.timerManager.addTimerListener(new TimerListener() {
            @Override
            public void rightHandPressed() {
                labelRightHand.setIcon(iconRightPressed);
            }

            @Override
            public void rightHandReleased() {
                labelRightHand.setIcon(iconRight);
            }
        });
        panelTimer.add(labelRightHand, "5, 0");

        return panelTimer;
    }

    private JScrollPane createTimesPanel() {
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.setPreferredSize(new Dimension(200, 200));
        scrollPane.setMinimumSize(new Dimension(200, 200));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Times"));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        this.sessionManager.addSessionListener(new SessionListener() {
            @Override
            public void solutionsUpdated(final Solution[] solutions) {
                JPanel panelTimes = new JPanel();
                panelTimes.setLayout(new GridBagLayout());

                GridBagConstraints c = new GridBagConstraints();
                c.ipady = 4;
                c.anchor = GridBagConstraints.BASELINE_TRAILING;

                for (int i = 0; i < Math.min(500, solutions.length); i++) {
                    final int index = i;

                    JLabel labelIndex = new JLabel(Integer.toString(solutions.length - i) + ".");
                    labelIndex.setFont(new Font("Tahoma", Font.BOLD, 13));
                    c.gridx = 0;
                    c.insets = new Insets(0, 0, 0, 8);
                    panelTimes.add(labelIndex, c);

                    JLabel labelTime = new JLabel(SolutionUtils.formatMinutes(solutions[i].timing.getElapsedTime()));
                    labelTime.setFont(new Font("Tahoma", Font.PLAIN, 13));
                    c.gridx = 2;
                    c.insets = new Insets(0, 0, 0, 16);
                    panelTimes.add(labelTime, c);

                    final JLabel labelPlus2 = new JLabel("+2");
                    labelPlus2.setFont(new Font("Tahoma", Font.PLAIN, 13));
                    if (!solutions[index].penalty.equals("+2")) {
                        labelPlus2.setForeground(Color.LIGHT_GRAY);
                    }
                    labelPlus2.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    labelPlus2.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (!solutions[index].penalty.equals("+2")) {
                                solutions[index].penalty = "+2";
                                Main.this.solutionManager.updateSolution(solutions[index]);
                            } else if (solutions[index].penalty.equals("+2")) {
                                solutions[index].penalty = "";
                                Main.this.solutionManager.updateSolution(solutions[index]);
                            }
                        }
                    });
                    c.gridx = 3;
                    c.insets = new Insets(0, 0, 0, 8);
                    panelTimes.add(labelPlus2, c);

                    final JLabel labelDNF = new JLabel("DNF");
                    labelDNF.setFont(new Font("Tahoma", Font.PLAIN, 13));
                    if (!solutions[index].penalty.equals("DNF")) {
                        labelDNF.setForeground(Color.LIGHT_GRAY);
                    }
                    labelDNF.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    labelDNF.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (!solutions[index].penalty.equals("DNF")) {
                                solutions[index].penalty = "DNF";
                                Main.this.solutionManager.updateSolution(solutions[index]);
                            } else if (solutions[index].penalty.equals("DNF")) {
                                solutions[index].penalty = "";
                                Main.this.solutionManager.updateSolution(solutions[index]);
                            }
                        }
                    });
                    c.gridx = 4;
                    c.insets = new Insets(0, 0, 0, 16);
                    panelTimes.add(labelDNF, c);

                    JLabel labelX = new JLabel();
                    labelX.setIcon(new ImageIcon(getClass().getResource("/com/puzzletimer/resources/x.png")));
                    labelX.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    labelX.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            Main.this.solutionManager.removeSolution(solutions[index]);
                        }
                    });
                    c.gridx = 5;
                    c.insets = new Insets(0, 0, 0, 0);
                    panelTimes.add(labelX, c);
                }

                scrollPane.setViewportView(panelTimes);
            }
        });

        return scrollPane;
    }

    private JPanel createStatisticsPanel() {
        // panelStatistics
        double f = TableLayout.FILL;
        double p = TableLayout.PREFERRED;
        JPanel panelStatistics = new JPanel(new TableLayout(new double[][] {
            { f, p, 8, p, f },
            { f, p, 1, p, 1, p, 6, p, 1, p, 1, p, 1, p, 6, p, 1, p, 1, p, 1, p, f },
        }));
        panelStatistics.setBorder(BorderFactory.createTitledBorder("Statistics"));

        StatisticalMeasure[] statistics = {
            new Mean(1, Integer.MAX_VALUE),
            new StandardDeviation(1, Integer.MAX_VALUE),
            new Best(1, Integer.MAX_VALUE),
            new Mean(3, 3),
            new Average(5, 5),
            new StandardDeviation(3, 3),
            new Best(3, 3),
            new Mean(10, 10),
            new Average(12, 12),
            new StandardDeviation(10, 10),
            new Best(10, 10),
        };

        int y = 1;
        for (final StatisticalMeasure measure : statistics) {
            JLabel labelMeasure = new JLabel(measure.getDescription() + ":");
            labelMeasure.setFont(new Font("Tahoma", Font.BOLD, 11));
            panelStatistics.add(labelMeasure, "1, " + y + ", R, C");

            final JLabel labelValue = new JLabel("XX:XX.XX");
            panelStatistics.add(labelValue, "3, " + y + ", L, C");

            this.sessionManager.addSessionListener(new SessionListener() {
                @Override
                public void solutionsUpdated(Solution[] solutions) {
                    if (solutions.length >= measure.getMinimumWindowSize()) {
                        int size = Math.min(solutions.length, measure.getMaximumWindowSize());

                        Solution[] window = new Solution[size];
                        for (int i = 0; i < size; i++) {
                            window[i] = solutions[i];
                        }

                        measure.setSolutions(window);
                        labelValue.setText(SolutionUtils.formatMinutes(measure.getValue()));
                    } else {
                        labelValue.setText("XX:XX.XX");
                    }
                }
            });

            y += 2;
        }

        return panelStatistics;
    }

    private JPanel createScramblePanel() {
        // panelScramble
        JPanel panelScramble = new JPanel();
        panelScramble.setBorder(BorderFactory.createTitledBorder("Scramble"));
        panelScramble.setLayout(new TableLayout(new double[][] {
            { TableLayout.FILL },
            { TableLayout.PREFERRED },
        }));

        // panel3D
        this.panel3D = new Panel3D();
        this.panel3D.setMinimumSize(new Dimension(200, 180));
        this.panel3D.setPreferredSize(new Dimension(200, 180));
        this.panel3D.setFocusable(false);
        panelScramble.add(this.panel3D, "0, 0");

        this.scrambleManager.addScrambleListener(new ScrambleListener() {
            @Override
            public void scrambleChanged(Scramble scramble) {
                updateScrambleViewer(scramble.getSequence());
            }
        });

        return panelScramble;
    }

    private void updateScrambleViewer(String[] sequence) {
        Category currentCategory = Main.this.categoryManager.getCurrentCategory();
        Scrambler scrambler = this.scramblerProvider.get(currentCategory.scramblerId);
        Puzzle puzzle = this.puzzleProvider.get(scrambler.getScramblerInfo().getPuzzleId());
        ColorScheme colorScheme = this.colorManager.getColorScheme(puzzle.getPuzzleInfo().getPuzzleId());
        this.panel3D.mesh = puzzle.getScrambledPuzzleMesh(colorScheme, sequence);
        this.panel3D.repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e){
                }

                JFrame frame = new Main();
                frame.setSize(640, 480);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
    }
}
