package com.jcraft.player.playlist;

import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A playList bounded to a ComboBox. */
public class ComboPlayListHolder implements PlayListHolder {

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(ComboPlayListHolder.class);

    /** The comboBox. */
    private JComboBox<String> comboBox;

    /**
     * Creates the combo playlist holder.
     * 
     * @param playList the play list
     * @param comboBox the comboBox
     */
    public ComboPlayListHolder(Vector<String> playList, JComboBox<String> comboBox) {

        this.comboBox = comboBox;

        // Add the list items to the combo
        DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>(playList);

        this.comboBox.setModel(comboBoxModel);
    }

    @Override
    public String getCurrentItem() {
        return (String) comboBox.getSelectedItem();
    }

    @Override
    public int getItemCount() {
        return comboBox.getItemCount();
    }

    @Override
    public void next() {
        LOG.debug("Advance in playlist");
        int nextIndex = comboBox.getSelectedIndex() + 1;
        if (nextIndex >= getItemCount()) {
            LOG.debug("End of playlist, restart at beginning");
            nextIndex = 0;
        }

        comboBox.setSelectedIndex(nextIndex);
    }

    @Override
    public void addItemIfNotFound(String item) {

        for (int i = 0; i < getItemCount(); i++) {
            String testedItem = comboBox.getItemAt(i);
            if (item.equals(testedItem)) {
                return;
            }
        }

        comboBox.addItem(item);
    }

}
