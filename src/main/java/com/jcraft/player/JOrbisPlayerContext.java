package com.jcraft.player;

/** Context of the JOrbisPlayer. */
public interface JOrbisPlayerContext {

    /**
     * @param requestedIndex the requested index
     * @return item at the selectedindex
     */
    String getItemAtIndex(int requestedIndex);

    /** @return the number of items */
    int getItemCount();

    /**
     * Adds an item to the playlist.
     * 
     * @param item item to add
     */
    void addItem(String item);

    /** @return the playlist */
    PlayList getPlayList();

    /** Handles the end of a play. */
    void handleEndOfPlay();

    /** Advance in the playlist. */
    void next();

    /** @return current item on the playlist */
    String getCurrentItem();

}
