package com.puzzletimer;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.puzzletimer.database.CategoryDAO;
import com.puzzletimer.database.ColorDAO;
import com.puzzletimer.database.ConfigurationDAO;
import com.puzzletimer.database.DatabaseException;
import com.puzzletimer.database.SolutionDAO;
import com.puzzletimer.gui.MainFrame;
import com.puzzletimer.models.Category;
import com.puzzletimer.models.ColorScheme;
import com.puzzletimer.models.ConfigurationEntry;
import com.puzzletimer.models.Solution;
import com.puzzletimer.models.Timing;
import com.puzzletimer.parsers.ScrambleParserProvider;
import com.puzzletimer.puzzles.PuzzleProvider;
import com.puzzletimer.scramblers.ScramblerProvider;
import com.puzzletimer.state.CategoryListener;
import com.puzzletimer.state.CategoryManager;
import com.puzzletimer.state.ColorListener;
import com.puzzletimer.state.ColorManager;
import com.puzzletimer.state.ConfigurationListener;
import com.puzzletimer.state.ConfigurationManager;
import com.puzzletimer.state.MessageManager;
import com.puzzletimer.state.ScrambleManager;
import com.puzzletimer.state.SessionManager;
import com.puzzletimer.state.SolutionListener;
import com.puzzletimer.state.SolutionManager;
import com.puzzletimer.state.TimerListener;
import com.puzzletimer.state.TimerManager;
import com.puzzletimer.state.MessageManager.MessageType;
import com.puzzletimer.statistics.Best;
import com.puzzletimer.statistics.BestAverage;
import com.puzzletimer.statistics.BestMean;
import com.puzzletimer.statistics.StatisticalMeasure;
import com.puzzletimer.timer.Timer;
import com.puzzletimer.util.SolutionUtils;

public class Main {
    private ConfigurationDAO configurationDAO;
    private ColorDAO colorDAO;
    private CategoryDAO categoryDAO;
    private SolutionDAO solutionDAO;

    private MessageManager messageManager;
    private ConfigurationManager configurationManager;
    private TimerManager timerManager;
    private PuzzleProvider puzzleProvider;
    private ColorManager colorManager;
    private ScrambleParserProvider scrambleParserProvider;
    private ScramblerProvider scramblerProvider;
    private CategoryManager categoryManager;
    private ScrambleManager scrambleManager;
    private SolutionManager solutionManager;
    private SessionManager sessionManager;

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
            JFrame frame = new JFrame();
            JOptionPane.showMessageDialog(
                frame,
                "Couldn't create database.",
                "Puzzle Timer",
                JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // connect to database
        Connection connection = null;
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection("jdbc:h2:puzzletimer;IFEXISTS=TRUE", "sa", "");
        } catch (Exception e) {
            JFrame frame = new JFrame();
            JOptionPane.showMessageDialog(
                frame,
                "Couldn't connect to database. Isn't Puzzle Timer already running?",
                "Puzzle Timer",
                JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // message manager
        this.messageManager = new MessageManager();

        // configuration DAO
        this.configurationDAO = new ConfigurationDAO(connection);

        // configuration manager
        this.configurationManager = new ConfigurationManager(this.configurationDAO.getAll());
        this.configurationManager.addConfigurationListener(new ConfigurationListener() {
            @Override
            public void configurationEntryUpdated(String key, String value) {
                try {
                    Main.this.configurationDAO.update(
                        new ConfigurationEntry(key, value));
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }
        });

        // timer manager
        this.timerManager = new TimerManager();
        this.timerManager.setInspectionEnabled(
            this.configurationManager.getConfiguration("INSPECTION-TIME-ENABLED").equals("TRUE"));
        this.timerManager.addTimerListener(new TimerListener() {
            @Override
            public void solutionFinished(Timing timing, String penalty) {
                // add solution
                Main.this.solutionManager.addSolution(
                    new Solution(
                        UUID.randomUUID(),
                        Main.this.categoryManager.getCurrentCategory().getCategoryId(),
                        Main.this.scrambleManager.getCurrentScramble(),
                        timing,
                        penalty));

                // check for personal records
                Solution[] solutions = Main.this.solutionManager.getSolutions();

                StatisticalMeasure[] measures = {
                    new Best(1, Integer.MAX_VALUE),
                    new BestMean(3, 3),
                    new BestMean(100, 100),
                    new BestAverage(5, 5),
                    new BestAverage(12, 12),
                };

                String[] descriptions = {
                    "single",
                    "mean of 3",
                    "mean of 100",
                    "average of 5",
                    "average of 12",
                };

                for (int i = 0; i < measures.length; i++) {
                    if (solutions.length >= measures[i].getMinimumWindowSize()) {
                        measures[i].setSolutions(solutions);
                        if (measures[i].getWindowPosition() == 0) {
                            Main.this.messageManager.enqueueMessage(
                                MessageType.INFORMATION,
                                String.format("Personal Record - %s: %s (%s)",
                                    Main.this.categoryManager.getCurrentCategory().getDescription(),
                                    SolutionUtils.formatMinutes(measures[i].getValue()),
                                    descriptions[i]));
                        }
                    }
                }

                // gerate next scramble
                Main.this.scrambleManager.changeScramble();
            }

            @Override
            public void timerChanged(Timer timer) {
                Main.this.configurationManager.setConfiguration(
                    "TIMER-TRIGGER", timer.getTimerId());
            }

            @Override
            public void inspectionEnabledSet(boolean inspectionEnabled) {
                Main.this.configurationManager.setConfiguration(
                    "INSPECTION-TIME-ENABLED", inspectionEnabled ? "TRUE" : "FALSE");
            }
        });

        // puzzle provider
        this.puzzleProvider = new PuzzleProvider();

        // color DAO
        this.colorDAO = new ColorDAO(connection);

        // color manager
        this.colorManager = new ColorManager(this.colorDAO.getAll());
        this.colorManager.addColorListener(new ColorListener() {
            @Override
            public void colorSchemeUpdated(ColorScheme colorScheme) {
                try {
                    Main.this.colorDAO.update(colorScheme);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }
        });

        // scramble parser provider
        this.scrambleParserProvider = new ScrambleParserProvider();

        // scrambler provider
        this.scramblerProvider = new ScramblerProvider();

        // category DAO
        this.categoryDAO = new CategoryDAO(connection);

        // categoryManager
        Category[] categories = this.categoryDAO.getAll();

        UUID currentCategoryId = UUID.fromString(
            this.configurationManager.getConfiguration("CURRENT-CATEGORY"));
        Category currentCategory = null;
        for (Category category : categories) {
            if (category.getCategoryId().equals(currentCategoryId)) {
                currentCategory = category;
            }
        }

        this.categoryManager = new CategoryManager(categories, currentCategory);
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void currentCategoryChanged(Category category) {
                Main.this.configurationManager.setConfiguration(
                    "CURRENT-CATEGORY",
                    category.getCategoryId().toString());

                try {
                    Main.this.solutionManager.loadSolutions(
                        Main.this.solutionDAO.getAll(category));
                    Main.this.sessionManager.clearSession();
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }

            @Override
            public void categoryAdded(Category category) {
                try {
                    Main.this.categoryDAO.insert(category);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }

            @Override
            public void categoryRemoved(Category category) {
                try {
                    Main.this.categoryDAO.delete(category);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }

            @Override
            public void categoryUpdated(Category category) {
                try {
                    Main.this.categoryDAO.update(category);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }
        });

        // scramble manager
        this.scrambleManager = new ScrambleManager(
            this.scramblerProvider,
            this.scramblerProvider.get(currentCategory.getScramblerId()));
        this.categoryManager.addCategoryListener(new CategoryListener() {
            @Override
            public void currentCategoryChanged(Category category) {
                Main.this.scrambleManager.setCategory(category);
            }
        });

        // solution DAO
        this.solutionDAO = new SolutionDAO(connection, this.scramblerProvider, this.scrambleParserProvider);

        // solution manager
        this.solutionManager = new SolutionManager();
        this.solutionManager.addSolutionListener(new SolutionListener() {
            @Override
            public void solutionAdded(Solution solution) {
                Main.this.sessionManager.addSolution(solution);

                try {
                    Main.this.solutionDAO.insert(solution);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }

            @Override
            public void solutionsAdded(Solution[] solutions) {
                try {
                    Main.this.solutionDAO.insert(solutions);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }

            @Override
            public void solutionUpdated(Solution solution) {
                Main.this.sessionManager.updateSolution(solution);

                try {
                    Main.this.solutionDAO.update(solution);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }

            @Override
            public void solutionRemoved(Solution solution) {
                Main.this.sessionManager.removeSolution(solution);

                try {
                    Main.this.solutionDAO.delete(solution);
                } catch (DatabaseException e) {
                    Main.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        "DATABASE ERROR: " + e.getMessage());
                }
            }
        });

        // session manager
        this.sessionManager = new SessionManager();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e){
                }

                Main main = new Main();

                Image icon = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/com/puzzletimer/resources/icon.png"));

                // main frame
                MainFrame mainFrame = new MainFrame(
                    main.messageManager,
                    main.configurationManager,
                    main.timerManager,
                    main.puzzleProvider,
                    main.colorManager,
                    main.scrambleParserProvider,
                    main.scramblerProvider,
                    main.categoryManager,
                    main.scrambleManager,
                    main.solutionManager,
                    main.sessionManager);
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setIconImage(icon);

                main.categoryManager.setCurrentCategory(main.categoryManager.getCurrentCategory());

                mainFrame.setVisible(true);
            }
        });
    }
}
