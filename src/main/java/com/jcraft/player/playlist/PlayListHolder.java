package com.jcraft.player.playlist;

/** Interface for a playList holder. */
public interface PlayListHolder {

    /** @return the number of items */
    int getItemCount();

    /** Advance in the playList. */
    void next();

    /** @return current item on the playList */
    String getCurrentItem();

    void addItemIfNotFound(String item);
    
}
