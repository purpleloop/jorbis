package com.jcraft.player;

import com.jcraft.player.playlist.PlayListHolder;

/** Context of the JOrbisPlayer. */
public interface JOrbisPlayerContext {

    /** @return the playListHolder */
    PlayListHolder getPlayListHolder();

    /** Handles the end of a play. */
    void handleEndOfPlay();
    
}
