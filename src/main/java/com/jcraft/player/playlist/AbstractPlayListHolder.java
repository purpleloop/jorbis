package com.jcraft.player.playlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Base of a play list holder. */
public abstract class AbstractPlayListHolder implements PlayListHolder {

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(AbstractPlayListHolder.class);

    @Override
    public void next() {
        LOG.debug("Advance in playlist");
        int nextIndex = getSelectedIndex() + 1;
        if (nextIndex >= getItemCount()) {

            LOG.debug("End of playlist restart at beginning");
            nextIndex = 0;
        }

        selectIndex(nextIndex);
    }

    @Override
    public void addItemIfNotFound(String item) {

        for (int i = 0; i < getItemCount(); i++) {
            String testedItem = getItemAtIndex(i);
            if (item.equals(testedItem)) {
                return;
            }
        }

        addItem(item);
    }

    /**
     * Selects the playList item at the given index.
     * 
     * @param index requested index
     */
    protected abstract void selectIndex(int index);

    /** @return the currently selected index */
    protected abstract int getSelectedIndex();

    /**
     * Add an item to the playList.
     * 
     * @param item the item to add
     */
    protected abstract void addItem(String item);

    /**
     * @param index index of the requested item
     * @return the item at the given index
     */
    protected abstract String getItemAtIndex(int index);

}
