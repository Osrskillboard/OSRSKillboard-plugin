package com.osrskillboard;

import com.google.common.base.Strings;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;


class OsrsKillboardBox extends JPanel
{
    private static final int ITEMS_PER_ROW = 5;

    private final JPanel itemContainer = new JPanel();
    private final JLabel priceLabel = new JLabel();
    private final JLabel subTitleLabel = new JLabel();
    private final ItemManager itemManager;
    @Getter(AccessLevel.PACKAGE)
    private final String id;

    @Getter
    private final List<OsrsKillboardRecord> records = new ArrayList<>();

    private long totalPrice;
    private boolean hideIgnoredItems;
    private BiConsumer<String, Boolean> onItemToggle;

    OsrsKillboardBox(
            final ItemManager itemManager,
            final String id,
            @Nullable final String subtitle)
    {
        this.id = id;
        this.itemManager = itemManager;
        this.onItemToggle = onItemToggle;
        this.hideIgnoredItems = hideIgnoredItems;

        setLayout(new BorderLayout(0, 1));
        setBorder(new EmptyBorder(5, 0, 0, 0));

        final JPanel logTitle = new JPanel(new BorderLayout(5, 0));
        logTitle.setBorder(new EmptyBorder(7, 7, 7, 7));
        logTitle.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

        final JLabel titleLabel = new JLabel(Text.removeTags(id));
        titleLabel.setFont(FontManager.getRunescapeSmallFont());
        titleLabel.setForeground(Color.WHITE);

        logTitle.add(titleLabel, BorderLayout.WEST);

        subTitleLabel.setFont(FontManager.getRunescapeSmallFont());
        subTitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        logTitle.add(subTitleLabel, BorderLayout.CENTER);

        if (!Strings.isNullOrEmpty(subtitle))
        {
            subTitleLabel.setText(subtitle);
        }

        priceLabel.setFont(FontManager.getRunescapeSmallFont());
        priceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        logTitle.add(priceLabel, BorderLayout.EAST);

        add(logTitle, BorderLayout.NORTH);
        add(itemContainer, BorderLayout.CENTER);
    }

    private long getTotalKills()
    {
        return records.size();
    }

    /**
     * Checks if this box matches specified record
     *
     * @param record loot record
     * @return true if match is made
     */
    boolean matches(final OsrsKillboardRecord record)
    {
        return record.getTitle().equals(id);
    }

    /**
     * Checks if this box matches specified id
     *
     * @param id other record id
     * @return true if match is made
     */
    boolean matches(final String id)
    {
        if (id == null)
        {
            return true;
        }

        return this.id.equals(id);
    }

    /**
     * Adds an record's data into a loot box.
     * This will add new items to the list, re-calculating price and kill count.
     */
    void combine(final OsrsKillboardRecord record)
    {
        if (!matches(record))
        {
            throw new IllegalArgumentException(record.toString());
        }

        records.add(record);
    }

    void rebuild()
    {
        buildItems();
        String priceTypeString = " ";

        priceLabel.setText(QuantityFormatter.quantityToStackSize(totalPrice) + " gp");
        priceLabel.setToolTipText(QuantityFormatter.formatNumber(totalPrice) + " gp");

        final long kills = getTotalKills();
        if (kills > 1)
        {
            subTitleLabel.setText("x " + kills);
        }

        validate();
        repaint();
    }

    /**
     * This method creates stacked items from the item list, calculates total price and then
     * displays all the items in the UI.
     */
    private void buildItems()
    {
        final List<OsrsKillboardItem> allItems = new ArrayList<>();
        final List<OsrsKillboardItem> items = new ArrayList<>();
        totalPrice = 0;

        for (OsrsKillboardRecord record : records)
        {
            allItems.addAll(Arrays.asList(record.getItems()));
        }

        for (final OsrsKillboardItem entry : allItems)
        {
            totalPrice += entry.getGePrice();

            int quantity = 0;
            for (final OsrsKillboardItem i : items)
            {
                if (i.getId() == entry.getId())
                {
                    quantity = i.getQuantity();
                    items.remove(i);
                    break;
                }
            }

            if (quantity > 0)
            {
                int newQuantity = entry.getQuantity() + quantity;
                long pricePerItem = entry.getGePrice() == 0 ? 0 : (entry.getGePrice() / entry.getQuantity());

                items.add(new OsrsKillboardItem(entry.getId(), entry.getName(), newQuantity, pricePerItem * newQuantity));
            }
            else
            {
                items.add(entry);
            }
        }

        items.sort((i1, i2) -> Long.compare(i2.getGePrice(), i1.getGePrice()));

        // Calculates how many rows need to be display to fit all items
        final int rowSize = ((items.size() % ITEMS_PER_ROW == 0) ? 0 : 1) + items.size() / ITEMS_PER_ROW;

        itemContainer.removeAll();
        itemContainer.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));

        for (int i = 0; i < rowSize * ITEMS_PER_ROW; i++)
        {
            final JPanel slotContainer = new JPanel();
            slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            if (i < items.size())
            {
                final OsrsKillboardItem item = items.get(i);
                final JLabel imageLabel = new JLabel();
                imageLabel.setToolTipText(buildToolTip(item));
                imageLabel.setVerticalAlignment(SwingConstants.CENTER);
                imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

                AsyncBufferedImage itemImage = itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1);

                itemImage.addTo(imageLabel);

                slotContainer.add(imageLabel);

                // Create popup menu
                final JPopupMenu popupMenu = new JPopupMenu();
                popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
                slotContainer.setComponentPopupMenu(popupMenu);

                final JMenuItem toggle = new JMenuItem("Toggle item");

                popupMenu.add(toggle);
            }

            itemContainer.add(slotContainer);
        }

        itemContainer.repaint();
    }

    private static String buildToolTip(OsrsKillboardItem item)
    {
        final String name = item.getName();
        final int quantity = item.getQuantity();
        final long price = item.getGePrice();
        return name + " x " + quantity + " (" + QuantityFormatter.quantityToStackSize(price) + ") ";
    }
}

