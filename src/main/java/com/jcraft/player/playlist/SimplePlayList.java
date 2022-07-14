package com.jcraft.player.playlist;

import java.util.ArrayList;
import java.util.List;

/** A simple play list. */
public class SimplePlayList implements PlayListHolder {

    /** The play list items. */
    private List<String> items;

    /** The current item. */
    int currentIndex = 0;

    /** Creates a simple play list. */
    public SimplePlayList() {
        items = new ArrayList<>();
    }

    /**
     * Add an item to the play list.
     * 
     * @param item the item to add
     */
    public void add(String item) {
        items.add(item);
    }

    public int getItemCount() {
        return items.size();
    }

    @Override
    public void next() {
        currentIndex++;
        if (currentIndex >= items.size()) {
            currentIndex = 0;
        }
    }

    @Override
    public String getCurrentItem() {
        return items.get(currentIndex);
    }

    /** Clears the play list. */
    public void clear() {
        items.clear();
    }

    @Override
    public void addItemIfNotFound(String item) {
        if (!items.contains(item)) {
            add(item);
        }
    }

}
