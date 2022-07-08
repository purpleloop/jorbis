package com.jcraft.player.playlist;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

/** A playList bounded to a ComboBox. */
public class ComboPlayListHolder extends AbstractPlayListHolder {

    /** The comboBox. */
    private JComboBox<String> comboBox;

    /**
     * Creates the holder.
     * 
     * @param playList the play list
     * @param comboBox the comboBox
     */
    public ComboPlayListHolder(PlayList playList, JComboBox<String> comboBox) {

        this.comboBox = comboBox;
        
        // Add the list items to the combo
        DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>(
                playList.getVector());

        this.comboBox.setModel(comboBoxModel);
    }

    /**
     * Adds an item to the playList.
     * 
     * @param item item to add
     */
    @Override
    public void addItem(String item) {
        comboBox.addItem(item);
    }

    @Override
    public String getCurrentItem() {
        return (String) comboBox.getSelectedItem();
    }

    /**
     * @param requestedIndex the requested index
     * @return item at the selected index
     */
    protected String getItemAtIndex(int requestedIndex) {
        return comboBox.getItemAt(requestedIndex);
    }

    @Override
    public int getItemCount() {
        return comboBox.getItemCount();
    }

    @Override
    protected int getSelectedIndex() {
        return comboBox.getSelectedIndex();
    }

    @Override
    protected void selectIndex(int nextIndex) {
        comboBox.setSelectedIndex(nextIndex);        
    }

}
