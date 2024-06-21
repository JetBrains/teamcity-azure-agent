package jetbrains.buildServer.clouds.azure;

/**
 * Provides number sequence.
 */
public interface IdProvider {
    /**
     * Gets a next integer from sequence.
     *
     * @return identifier.
     */
    int getNextId();
}
