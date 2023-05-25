package com.osrskillboard;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class OsrsKillboardPanel extends PluginPanel
{
    private static final int MAX_LOOT_BOXES = 500;
    private static final String HTML_LABEL_TEMPLATE =
            "<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

    // When there is no loot, display this
    private final PluginErrorPanel errorPanel = new PluginErrorPanel();

    // Handle loot boxes
    private final JPanel logsContainer = new JPanel();

    // Handle overall session data
    private final JPanel overallPanel = new JPanel();
    private final JLabel overallKillsLabel = new JLabel();
    private final JLabel overallGpLabel = new JLabel();
    private final JLabel overallIcon = new JLabel();

    // Details and navigation
    private final JPanel actionsContainer = new JPanel();
    private final JLabel detailsTitle = new JLabel();

    // Log collection
    private final List<OsrsKillboardRecord> records = new ArrayList<>();
    private final List<OsrsKillboardBox> boxes = new ArrayList<>();

    private final ItemManager itemManager;
    private final OsrsKillboardPlugin plugin;
    private final OsrsKillboardConfig config;

    private String currentView;

    OsrsKillboardPanel(final OsrsKillboardPlugin plugin, final ItemManager itemManager, final OsrsKillboardConfig config)
    {
        this.itemManager = itemManager;
        this.plugin = plugin;
        this.config = config;

        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create layout panel for wrapping
        final JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        final JPanel leftTitleContainer = new JPanel(new BorderLayout(5, 0));
        leftTitleContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        detailsTitle.setForeground(Color.WHITE);

        leftTitleContainer.add(detailsTitle, BorderLayout.CENTER);

        actionsContainer.add(leftTitleContainer, BorderLayout.WEST);

        // Create panel that will contain overall data
        overallPanel.setBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(5, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
                )
        );

        overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overallPanel.setLayout(new BorderLayout());
        overallPanel.setVisible(false);

        // Add icon and contents
        final JPanel overallInfo = new JPanel();
        overallInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overallInfo.setLayout(new GridLayout(2, 1));
        overallInfo.setBorder(new EmptyBorder(2, 10, 2, 0));
        overallKillsLabel.setFont(FontManager.getRunescapeSmallFont());
        overallGpLabel.setFont(FontManager.getRunescapeSmallFont());
        overallInfo.add(overallKillsLabel);
        overallInfo.add(overallGpLabel);
        overallPanel.add(overallIcon, BorderLayout.WEST);
        overallPanel.add(overallInfo, BorderLayout.CENTER);

        // Create reset all menu
        final JMenuItem reset = new JMenuItem("Reset All");
        reset.addActionListener(e ->
        {
            // If not in detailed view, remove all, otherwise only remove for the currently detailed title
            records.removeIf(r -> r.matches(currentView));
            boxes.removeIf(b -> b.matches(currentView));
            updateOverall();
            logsContainer.removeAll();
            logsContainer.repaint();

            // Delete all loot, or loot matching the current view
            OsrsKillboardClient client = plugin.getOsrsKillboardClient();
        });

        // Create popup menu
        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
        popupMenu.add(reset);
        overallPanel.setComponentPopupMenu(popupMenu);

        // Create loot boxes wrapper
        logsContainer.setLayout(new BoxLayout(logsContainer, BoxLayout.Y_AXIS));
        layoutPanel.add(actionsContainer);
        layoutPanel.add(overallPanel);
        layoutPanel.add(logsContainer);

        // Add error pane
        errorPanel.setContent("OSRS Killboard", "You haven't killed any players yet.");
        add(errorPanel);
    }

    void loadHeaderIcon(BufferedImage img)
    {
        overallIcon.setIcon(new ImageIcon(img));
    }

    /**
     * Adds a new entry to the plugin.
     * Creates a subtitle, adds a new entry and then passes off to the render methods, that will decide
     * how to display this new data.
     */
    void add(final String eventName, final int actorLevel, OsrsKillboardItem[] items, String killId)
    {
        final String subTitle = actorLevel > -1 ? "(lvl-" + actorLevel + ")" : "";
        final OsrsKillboardRecord record = new OsrsKillboardRecord(eventName, subTitle, items, System.currentTimeMillis(), killId);
        records.add(record);
        OsrsKillboardBox box = buildBox(record);
        if (box != null)
        {
            box.rebuild();
            updateOverall();
        }
    }

    /**
     * Adds a Collection of records to the panel
     */
    void addRecords(Collection<OsrsKillboardRecord> recs)
    {
        records.addAll(recs);
        rebuild();
    }

    /**
     * Rebuilds all the boxes from scratch using existing listed records, depending on the grouping mode.
     */
    private void rebuild()
    {
        logsContainer.removeAll();
        boxes.clear();
        int start = 0;
        if (records.size() > MAX_LOOT_BOXES)
        {
            start = records.size() - MAX_LOOT_BOXES;
        }
        for (int i = start; i < records.size(); i++)
        {
            buildBox(records.get(i));
        }
        boxes.forEach(OsrsKillboardBox::rebuild);
        updateOverall();
        logsContainer.revalidate();
        logsContainer.repaint();
    }

    /**
     * This method decides what to do with a new record, if a similar log exists, it will
     * add its items to it, updating the log's overall price and kills. If not, a new log will be created
     * to hold this entry's information.
     */
    private OsrsKillboardBox buildBox(OsrsKillboardRecord record)
    {
        // If this record is not part of current view, return
        if (!record.matches(currentView))
        {
            return null;
        }

        // Show main view
        remove(errorPanel);
        actionsContainer.setVisible(true);
        overallPanel.setVisible(true);

        // Create box
        final OsrsKillboardBox box = new OsrsKillboardBox(itemManager, record.getTitle(), record.getSubTitle());
        box.combine(record);

        // Create popup menu
        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
        box.setComponentPopupMenu(popupMenu);

        // Create reset menu
        final JMenuItem reset = new JMenuItem("Reset");
        reset.addActionListener(e ->
        {
            records.removeAll(box.getRecords());
            boxes.remove(box);
            updateOverall();
            logsContainer.remove(box);
            logsContainer.repaint();
        });

        popupMenu.add(reset);

        if(!record.getOsrsKillboardKillId().equals("") || record.getTitle() != "PvP Loot Chest"){
            final JMenuItem openOsrsKillboardLink = new JMenuItem("Open on OSRSKillboard.com");
            openOsrsKillboardLink.addActionListener(e -> OsrsKillboardPlugin.openOsrsKillboardLink(record.getOsrsKillboardKillId()));
            popupMenu.add(openOsrsKillboardLink);
        }

        if(!record.getOsrsKillboardKillId().equals("") || record.getTitle() != "PvP Loot Chest"){
            final JMenuItem copyOsrsKillboardLink = new JMenuItem("Copy kill link");
            String killUrl = OsrsKillboardPlugin.GetKillUrl(record.getOsrsKillboardKillId());
            final StringSelection osrsKillboardLink = new StringSelection(killUrl);
            copyOsrsKillboardLink.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(osrsKillboardLink, null));

            popupMenu.add(copyOsrsKillboardLink);
        }

        // Add box to panel
        boxes.add(box);
        logsContainer.add(box, 0);

        if (boxes.size() > MAX_LOOT_BOXES)
        {
            logsContainer.remove(boxes.remove(0));
        }

        return box;
    }

    private void updateOverall()
    {
        long overallKills = 0;
        long overallGp = 0;

        for (OsrsKillboardRecord record : records)
        {
            if (!record.matches(currentView))
            {
                continue;
            }

            int present = record.getItems().length;

            for (OsrsKillboardItem item : record.getItems())
            {
                overallGp += item.getGePrice();
            }

            if (present > 0)
            {
                overallKills++;
            }
        }

        overallKillsLabel.setText(htmlLabel("Total count: ", overallKills));
        overallGpLabel.setText(htmlLabel("Total value: ", overallGp));
    }

    private static String htmlLabel(String key, long value)
    {
        final String valueStr = QuantityFormatter.quantityToStackSize(value);
        return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
    }
}
