/** Module definition for the JOrbis Player */
module com.jcraft.jorbis {

    exports com.jcraft.jorbis;
    exports com.jcraft.player;
    exports com.jcraft.player.playlist;

    requires org.apache.logging.log4j;
    requires transitive java.desktop;
}
