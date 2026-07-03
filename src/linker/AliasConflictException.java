package linker;

public class AliasConflictException extends RuntimeException {

    public AliasConflictException(String alias) {
        super("Alias already in use: " + alias);
    }
}
